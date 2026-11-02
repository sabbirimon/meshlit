package com.meshlit.resources
import android.content.Context
import com.meshlit.core.mcp.control.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.operations.OperationsControl
import com.meshlit.core.common.control.ManagedFeature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.SecureRandom

@Serializable data class WebDevice(val id:String,val name:String,val url:String,val certificateSha256:String?=null,val callerId:String,val state:String="PENDING",val agentRead:Boolean=false,val teamAllowed:Boolean=false,val modelId:String="")
/** Outbound paired HTTPS clients. Bearers/invitations never appear in URLs or exported profiles. */
class WebDevices private constructor(context:Context) {
    private val context=context.applicationContext
    private val vault=EncryptedCredentialStore(context,"web-device-clients")
    private val gate=OperationsControl.get(context).gate
    private val json=Json{ignoreUnknownKeys=true}
    private val mutable=MutableStateFlow(runCatching{vault.get("devices")?.let{json.decodeFromString<List<WebDevice>>(it)}}.getOrNull().orEmpty())
    val devices=mutable.asStateFlow()
    private val samples=MutableStateFlow<Map<String,DeviceObservation>>(emptyMap());val observations=samples.asStateFlow()
    @Synchronized private fun save(device:WebDevice){
        val next=mutable.value.filterNot{it.id==device.id}+device
        if(!device.agentRead || !device.teamAllowed)mutable.value=next
        vault.putCommitted("devices",json.encodeToString(next));mutable.value=next
    }
    fun permission(id:String,agentRead:Boolean?=null,teamAllowed:Boolean?=null,modelId:String?=null) {
        val old=mutable.value.single{it.id==id};val model=modelId ?: old.modelId
        require(model.length<=200 && !model.startsWith("cloud:"))
        save(old.copy(agentRead=agentRead ?: old.agentRead,teamAllowed=teamAllowed ?: old.teamAllowed,modelId=model))
    }
    suspend fun enroll(name:String,url:String,pin:String?,invitation:String):WebDevice=gate.run(ManagedFeature.GATEWAY){
        require(name.isNotBlank() && name.length<=100 && mutable.value.size<32)
        val endpoint=WebDeviceEndpoint(url,pin?.takeIf{it.isNotBlank()})
        val id=java.util.UUID.randomUUID().toString();val caller="android_"+id.replace("-","")
        val bearer=ByteArray(32).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
        val device=WebDevice(id,name,url,endpoint.certificateSha256,caller)
        vault.putCommitted("token-$id",bearer);save(device)
        // Preserve pending identity on timeout so retrying status does not create another device.
        val result=WebDeviceClient(endpoint).request("/api/v1/enroll",invitation=invitation,body=buildJsonObject{put("id",caller);put("name",android.os.Build.MODEL);put("kind","ANDROID");put("token",bearer);put("roles",buildJsonArray{add("control");add("sensors")})})
        device.copy(state=result["state"]?.jsonPrimitive?.content ?: "PENDING").also(::save)
    }
    suspend fun call(id:String,path:String,body:JsonObject?=null,agent:Boolean=false):JsonObject=gate.run(ManagedFeature.GATEWAY,agent){
        val expected=mutable.value.singleOrNull{it.id==id} ?: error("Device was removed")
        require(!agent || expected.agentRead){"Agent resource read not approved"}
        val token=vault.get("token-$id") ?: error("Missing device credential")
        val response=WebDeviceClient(WebDeviceEndpoint(expected.url,expected.certificateSha256)).request(path,token,body)
        currentCoroutineContext().ensureActive();require(mutable.value.any{it==expected}){"Device access changed"};response
    }
    suspend fun status(id:String):JsonObject=call(id,"/api/v1/status").also{body->val old=mutable.value.single{it.id==id};save(old.copy(state=body["state"]?.jsonPrimitive?.content ?: "UNKNOWN"))}
    suspend fun readResources(id:String,agent:Boolean=false):DeviceObservation {
        val expected=mutable.value.single{it.id==id};val body=call(id,"/api/v1/resources",agent=agent)
        val result=DeviceObservations.parse("web:$id",expected.name,body,System.currentTimeMillis());samples.update{it+(id to result)};return result
    }
    suspend fun disconnect(id:String) {
        call(id,"/api/v1/disconnect",buildJsonObject{})
        synchronized(this){val next=mutable.value.filterNot{it.id==id};mutable.value=next;vault.putCommitted("devices",json.encodeToString(next));vault.remove("token-$id");samples.update{it-id}}
    }
    suspend fun modelJob(id:String,request:AgentCommand,onUnconfirmedStop:suspend(String)->Unit):String {
        val expected=mutable.value.single{it.id==id};require(expected.teamAllowed && expected.modelId==request.modelId)
        fun current(){require(mutable.value.any{it==expected}){"Remote team permission changed"}}
        var requestId:String?="web_${DeviceDirectory.hash(expected.callerId).take(16)}_${request.requestId}";var finished=false
        try {
            current();val submitted=call(id,"/api/v1/commands",json.encodeToJsonElement(AgentCommand.serializer(),request).jsonObject)
            requestId=submitted["requestId"]!!.jsonPrimitive.content
            return withTimeout(180_000){var text:String?=null
                while(text==null){current();val jobs=call(id,"/api/v1/jobs")["jobs"]!!.jsonArray.map{it.jsonObject};current()
                    val job=jobs.singleOrNull{it["requestId"]?.jsonPrimitive?.content==requestId} ?: error("Remote job is not observable")
                    when(job["phase"]!!.jsonPrimitive.content){"SUCCEEDED"->{text=job["result"]!!.jsonObject["text"]!!.jsonPrimitive.content;finished=true};"FAILED","CANCELLED","INTERRUPTED"->error("Remote job did not succeed");else->delay(1000)}
                };checkNotNull(text).take(12000)
            }
        }finally{if(!finished && requestId!=null)withContext(NonCancellable){val acknowledged=runCatching{call(id,"/api/v1/cancel",buildJsonObject{put("requestId",requestId)})["phase"]?.jsonPrimitive?.content in setOf("CANCELLED","FAILED","SUCCEEDED","INTERRUPTED")}.getOrDefault(false);if(!acknowledged)onUnconfirmedStop(requestId!!)}}
    }
    companion object {@Volatile private var instance:WebDevices?=null;fun get(context:Context)=instance ?: synchronized(this){instance ?: WebDevices(context.applicationContext).also{instance=it}}}
}
