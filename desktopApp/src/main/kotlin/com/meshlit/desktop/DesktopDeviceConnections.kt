package com.meshlit.desktop

import com.meshlit.core.mcp.control.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest

@Serializable internal data class DesktopConnectSource(val id:String,val name:String,val kind:String,val endpoint:String="",val model:String="",val descriptor:String="")
@Serializable private data class DesktopConnectDocument(val version:Int=1,val sources:List<DesktopConnectSource> = emptyList(),val state:DeviceConnectionSnapshot=DeviceConnectionSnapshot())

/** Private owner IPC configures this adapter. The model action API cannot edit its policy/catalog.
 * Credentials are supplied transiently by the trusted extension, never stored or returned by view(). */
internal class DesktopDeviceConnections(
    private val read:()->String?,private val save:(String)->Unit,private val checkSession:()->Unit,
    private val knownNodes:()->List<SavedNode> = {emptyList()},
) {
    private val json=Json{encodeDefaults=true;ignoreUnknownKeys=true}
    private var loaded=false
    private var document=DesktopConnectDocument()
    private var failedClosed=false
    private fun load() {
        if(loaded)return
        document=read()?.let{json.decodeFromString<DesktopConnectDocument>(it)} ?: DesktopConnectDocument()
        require(document.version==1 && document.sources.size<=64)
        document.sources.forEach(::validate);loaded=true
    }
    @Synchronized private fun commit(next:DesktopConnectDocument) {
        try{save(json.encodeToString(next));document=next}catch(e:Exception){failedClosed=true;throw e}
    }
    private val coordinatorDelegate=lazy { DeviceConnectionCoordinator(object:DeviceConnectionStore {
        override fun load():DeviceConnectionSnapshot {this@DesktopDeviceConnections.load();return document.state}
        override fun save(value:DeviceConnectionSnapshot){commit(document.copy(state=value))}
    },::catalog,{checkSession();check(!failedClosed){"Connection storage unavailable"}},::probe) }
    private val coordinator by coordinatorDelegate
    private fun validate(source:DesktopConnectSource) {
        require(source.id.matches(Regex("[A-Za-z0-9:_-]{1,160}")) && source.name.length in 1..100 && source.name.none(Char::isISOControl))
        require(source.kind in setOf("compatible","mcp_setup"))
        require(source.descriptor.length<=4096)
        if(source.kind=="compatible") {
            val endpoint=HostEndpoint.parse(source.endpoint)
            require(source.model.length in 1..512 && source.model.none(Char::isISOControl))
        } else require(source.endpoint.isEmpty())
    }
    private fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun binding(source:DesktopConnectSource)=digest(json.encodeToString(source))
    @Synchronized private fun catalog():List<DeviceConnectionSource> {
        load()
        val configured=document.sources.map { source -> DeviceConnectionSource(
            source.id,source.name,if(source.kind=="compatible")ConnectionTransport.MODEL_API else ConnectionTransport.MCP,binding(source),source.kind=="compatible",
            "Setup only in Studio: start this trusted connector through the agent MCP selector; automatic attachment to an existing SDK session is not supported",
            if(source.kind=="compatible")"Saved model API ${HostEndpoint.parse(source.endpoint).base} · ${source.model}; system TLS or literal loopback" else "Saved MCP configuration; no automatic process launch",
        ) }
        val nodes=knownNodes().filter{node->document.sources.none{it.endpoint==node.endpoint}}.map { node ->
            DeviceConnectionSource("node:${node.id}",node.name,ConnectionTransport.MODEL_API,digest("${node.endpoint}|${node.protocol}"),false,
                "Setup only: save this address as an authenticated model connection before agent use","Saved address only; identity and credentials are not enrolled")
        }
        return (configured+nodes).take(128)
    }
    /** Sync uses existing owner-saved profiles. New/edited entries never inherit a grant. */
    @Synchronized fun sync(values:JsonArray):JsonObject {
        checkSession();load();require(values.size<=64)
        val next=values.map{json.decodeFromJsonElement<DesktopConnectSource>(it).also(::validate)}
        require(next.map{it.id}.distinct().size==next.size)
        if(next!=document.sources)commit(document.copy(sources=next))
        return view()
    }
    @Synchronized fun view():JsonObject {
        checkSession();val c=coordinator
        return buildJsonObject {
            put("enabled",c.state.value.policy.enabled)
            put("sources",buildJsonArray { catalog().forEach { source ->add(buildJsonObject {
                put("id",source.id);put("name",source.name);put("transport",source.transport.name)
                put("supported",source.supported);put("identityLabel",source.identityLabel);put("reason",source.setupReason)
                put("reviewBinding",source.binding);put("approved",c.state.value.policy.grants[source.id]==source.binding)
            }) } })
        }
    }
    fun configure(input:JsonObject):JsonObject {
        checkSession();require(input.keys.all{it in setOf("enabled","sourceId","allowed","reviewBinding")})
        input["enabled"]?.let {coordinator.setEnabled(checkNotNull(it.jsonPrimitive.booleanOrNull))}
        input["sourceId"]?.let {coordinator.setSourceAllowed(it.jsonPrimitive.content,checkNotNull(input["allowed"]?.jsonPrimitive?.booleanOrNull),input["reviewBinding"]?.jsonPrimitive?.content)}
        return view()
    }
    private suspend fun probe(source:DeviceConnectionSource,authorize:()->Unit):DeviceConnectionProbe {
        authorize()
        val configured=synchronized(this){document.sources.single{it.id==source.id}}
        if(configured.kind!="compatible")return DeviceConnectionProbe(false,"This connector needs owner setup")
        val authentication=currentCoroutineContext()[ContactCredential]
        require(authentication!=null && authentication.sourceId==source.id){"Saved credential access is required for this connection"}
        val result=runInterruptible(Dispatchers.IO){HostClient(HostEndpoint.parse(configured.endpoint),authentication.token).use{it.health()}}
        currentCoroutineContext().ensureActive();authorize()
        return when(result) {
            is HostConnectionResult.Ready -> DeviceConnectionProbe(configured.model in result.models,
                if(configured.model in result.models)"Saved model API catalog verified; model use, device commands and worker enrollment are separate" else "API replied, but the saved model was not advertised",itemCount=result.models.size)
            is HostConnectionResult.Failed -> DeviceConnectionProbe(false,result.message,result.code in setOf("network","timeout"))
        }
    }
    private class ContactCredential(val sourceId:String,val token:String):kotlin.coroutines.AbstractCoroutineContextElement(Key) {
        companion object Key:kotlin.coroutines.CoroutineContext.Key<ContactCredential>
    }
    suspend fun action(owner:String,args:JsonObject,credential:String=""):JsonObject {
        checkSession();require(args.keys.all{it in setOf("action","query","offerId","sourceId","requestId")})
        fun text(key:String)=(args[key] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("$key must be text")
        require(credential.length<=8192 && credential.none(Char::isISOControl))
        return when(text("action")) {
            "list","request" -> {
                val query=(args["query"] as? JsonPrimitive)?.takeIf{it.isString}?.content.orEmpty()
                val offer=coordinator.offer(owner,query)
                buildJsonObject{put("offer",json.encodeToJsonElement(offer))}
            }
            "connect" -> withContext(ContactCredential(text("sourceId"),credential)){buildJsonObject{put("receipt",json.encodeToJsonElement(coordinator.connect(owner,text("offerId"),text("sourceId"))))}}
            "status" -> buildJsonObject{put("receipts",json.encodeToJsonElement(coordinator.receipts(owner)))}
            "cancel" -> {coordinator.cancel(owner,text("requestId"));buildJsonObject{put("cancelRequested",true)}}
            else -> error("Unsupported connection operation")
        }
    }
    fun cancelOwner(owner:String)=coordinator.cancelOwner(owner)
    fun close(){if(coordinatorDelegate.isInitialized())coordinator.close()}
}
