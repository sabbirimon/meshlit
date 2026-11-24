package com.meshlit.resources
import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.mcp.*
import com.meshlit.core.mcp.control.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Explicit source-bound approval; credentials stay in existing MCP profiles. Results expire, with provenance retained in each receipt. */
class DeviceResultsHub private constructor(context:Context) {
    private val app=context.applicationContext as MeshlitApplication
    private val prefs=context.getSharedPreferences("device-result-grants",0)
    private val mutable=MutableStateFlow<Map<String,DeviceObservation>>(emptyMap())
    val results=mutable.asStateFlow()
    fun sources()=app.mcpClientPool.configured.value.values.filter{it.enabled}
    private fun signature(source:UserMcpServer)=MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(source).toByteArray()).joinToString(""){"%02x".format(it)}
    fun agentAllowed(source:UserMcpServer)=prefs.getString(source.id,null)==signature(source)
    fun approveAgent(source:UserMcpServer,enabled:Boolean){val edit=prefs.edit();if(enabled)edit.putString(source.id,signature(source)) else edit.remove(source.id);check(edit.commit())}
    suspend fun fetch(id:String,agent:Boolean=false):DeviceObservation = com.meshlit.operations.OperationsControl.get(app).gate.run(com.meshlit.core.common.control.ManagedFeature.GATEWAY,agent) {
        if(id.startsWith("web:"))return@run WebDevices.get(app).readResources(id.removePrefix("web:"),agent)
        val source=sources().singleOrNull{it.id==id} ?: error("Configured source unavailable")
        require(!agent || agentAllowed(source)){"Agent observation access is not approved for this source"}
        require(sources().count{it.name==source.name}==1){"Ambiguous source name"}
        val result=withTimeout(15_000){app.mcpClientPool.invoke(source.namespaced("resource_status"),buildJsonObject{})}
        currentCoroutineContext().ensureActive()
        require(sources().any{it==source} && (!agent || agentAllowed(source))){"Source changed or access revoked"}
        val value=(result as? McpToolResult.Json)?.value ?: error("Source did not provide resource data")
        val observation=DeviceObservations.parse(source.id,source.name,value,System.currentTimeMillis())
        mutable.update{it+(source.id to observation)}
        observation
    }
    fun agentSnapshot():JsonObject=buildJsonObject {
        val sources=sources().filter(::agentAllowed);val now=System.currentTimeMillis()
        val web=WebDevices.get(app);val approvedWeb=web.devices.value.filter{it.agentRead}
        put("sources",buildJsonArray{sources.forEach{source->add(buildJsonObject{put("id",source.id);put("name",source.name);put("status",if(mutable.value[source.id]?.fresh(now)==true)"fresh" else "needs_fetch")})}})
        put("results",buildJsonArray{sources.mapNotNull{mutable.value[it.id]}.filter{it.fresh(now)}.forEach{add(Json.encodeToJsonElement(DeviceObservation.serializer(),it))}})
        put("web_results",buildJsonArray{approvedWeb.mapNotNull{web.observations.value[it.id]}.filter{it.fresh(now)}.forEach{add(Json.encodeToJsonElement(DeviceObservation.serializer(),it))}})
        put("untrusted_observations",true)
    }
    companion object {
        @Volatile private var instance:DeviceResultsHub?=null
        fun get(context:Context)=instance ?: synchronized(this){instance ?: DeviceResultsHub(context.applicationContext).also{instance=it}}
    }
}
