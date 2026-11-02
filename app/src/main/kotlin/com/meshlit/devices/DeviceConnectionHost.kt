package com.meshlit.devices

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.control.AgentBackend
import com.meshlit.control.LocalAgentTurn
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.mcp.*
import com.meshlit.core.mcp.control.*
import com.meshlit.core.ssh.NodeSshAction
import com.meshlit.core.ssh.NodeSshRequest
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.operations.OperationsControl
import com.meshlit.providers.OnlineProviders
import com.meshlit.resources.WebDevices
import com.meshlit.ssh.SshConnections
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.koin.core.context.GlobalContext
import java.net.URI

/** Saved sources only. No network scan, invitation, permission change or secret enters the tool. */
class DeviceConnectionHost(context:Context,private val ssh:SshConnections,private val providers:OnlineProviders) {
    private val app=context.applicationContext as MeshlitApplication
    private val gate=OperationsControl.get(app).gate
    private val vault=EncryptedCredentialStore(app,"agent-device-connections")
    private val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    val coordinator=DeviceConnectionCoordinator(object:DeviceConnectionStore {
        override fun load()=vault.get("state")?.let{json.decodeFromString<DeviceConnectionSnapshot>(it)} ?: DeviceConnectionSnapshot()
        override fun save(value:DeviceConnectionSnapshot){vault.putCommitted("state",json.encodeToString(value))}
    },::sources,{gate.requireAllowed(ManagedFeature.AUTOMATION,true)},::probe)
    private fun id(type:String,value:String)="$type:${DeviceDirectory.hash(value).take(32)}"
    private fun binding(value:String)=DeviceDirectory.hash(value)
    private fun name(value:String,fallback:String)=value.filterNot(Char::isISOControl).trim().take(100).ifBlank{fallback}
    fun sources():List<DeviceConnectionSource> = buildList {
        val backend=GlobalContext.get().get<AgentBackend>()
        ssh.connections.value.forEach { source ->
            val permitted=source.agentAllowed && NodeSshAction.STATUS in source.agentNodeActions && backend.delegated(AgentBackend.Scope.SSH)
            add(DeviceConnectionSource(id("ssh",source.id),name(source.name,"Saved SSH node"),ConnectionTransport.SSH_STATUS,binding(json.encodeToString(source)),permitted,
                "Approve SSH agent delegation and the saved node's STATUS action first","${source.host}:${source.port} · ${source.hostKeySha256}"))
        }
        WebDevices.get(app).devices.value.forEach { source ->
            add(DeviceConnectionSource(id("web",source.id),name(source.name,"Paired web device"),ConnectionTransport.PAIRED_WEB,binding(json.encodeToString(source)),source.state=="APPROVED" && source.agentRead,
                "Complete first-device approval and enable this paired device's agent read scope",source.certificateSha256 ?: "Saved HTTPS origin; system certificate trust"))
        }
        providers.profiles.value.forEach { source ->
            val origin=runCatching{URI(source.endpoint).let{"${it.scheme}://${it.host}"+(if(it.port==-1)"" else ":${it.port}")}}.getOrNull().orEmpty()
            add(DeviceConnectionSource(id("model",source.id),name(source.name,"Saved model API"),ConnectionTransport.MODEL_API,binding(json.encodeToString(source)),source.enabled && source.agentAllowed,
                "Enable the saved model profile and its agent access first","Saved model API $origin · ${source.model}; not a worker identity"))
        }
        app.mcpClientPool.configured.value.values.filter{it.enabled}.forEach { source ->
            add(DeviceConnectionSource(id("mcp",source.id),name(source.name,"Saved MCP connector"),ConnectionTransport.MCP,binding(json.encodeToString(source)),false,
                "Setup only on Android: select this installed connector in MCP settings; automated process startup is not supported here","Saved executable configuration; no device enrollment"))
        }
        val discovery=GlobalContext.get().get<com.meshlit.disco.DiscoveryModeStore>()
        if(discovery.mode.value!=com.meshlit.disco.DiscoveryMode.OFF) {
            val peers=GlobalContext.get().get<com.meshlit.disco.PeerRepository>().uiState.value.grouped.values.flatten()
            peers.distinctBy{it.advertisement.nodeId}.take(32).forEach { peer ->
                val node=peer.advertisement.nodeId
                add(DeviceConnectionSource(id("nearby",node),"Nearby device ${node.take(8)}",ConnectionTransport.NEARBY_PEER,binding(node),false,
                    "Setup only: verify and approve this advertised identity in Devices before connecting","Unverified local discovery advertisement"))
            }
        }
    }.take(128)
    private suspend fun probe(source:DeviceConnectionSource,authorize:()->Unit):DeviceConnectionProbe {
        authorize()
        return try { when(source.transport) {
            ConnectionTransport.SSH_STATUS -> {
                val node=ssh.connections.value.single{ id("ssh",it.id)==source.id }
                val result=ssh.executeNode(node.id,NodeSshRequest(NodeSshAction.STATUS))
                authorize()
                val reply=runCatching{Json.parseToJsonElement(result.stdout).jsonObject}.getOrNull()
                val ok=result.exitCode==0 && !result.truncated && reply?.get("ok")?.jsonPrimitive?.booleanOrNull==true
                DeviceConnectionProbe(ok,if(ok)"Pinned SSH status verified; sessions open per command and other action scopes remain unchanged" else "SSH connected but the approved node status protocol did not succeed")
            }
            ConnectionTransport.PAIRED_WEB -> {
                val node=WebDevices.get(app).devices.value.single{ id("web",it.id)==source.id }
                val result=WebDevices.get(app).call(node.id,"/api/v1/status",agent=true)
                authorize();val approved=result["state"]?.jsonPrimitive?.content=="APPROVED"
                DeviceConnectionProbe(approved,if(approved)"Paired HTTPS identity and receiver approval verified; other device actions remain separately scoped" else "Receiver approval is not active; review the device in Devices")
            }
            ConnectionTransport.MODEL_API -> {
                val profile=providers.profiles.value.single{ id("model",it.id)==source.id }
                val models=providers.probeConnection(profile.id,authorize)
                val listed=profile.model in models
                DeviceConnectionProbe(listed,if(listed)"Saved model API catalog verified; generation, worker enrollment and resource pooling were not tested" else "Model API replied, but the saved model was not listed",itemCount=models.size)
            }
            else -> DeviceConnectionProbe(false,"This transport requires owner setup; no connection was started")
        } } catch(e:java.io.IOException) {
            DeviceConnectionProbe(false,"Saved source contact failed; check its network and identity",retryable=e !is javax.net.ssl.SSLException)
        }
    }
    suspend fun actor():String = currentCoroutineContext()[LocalAgentTurn]?.conversationId?.let{"chat:$it"} ?: "local-mcp"
    suspend fun invoke(owner:String,args:JsonObject):JsonObject=gate.run(ManagedFeature.AUTOMATION,true) {
        require(args.keys.all{it in setOf("action","query","offerId","sourceId","requestId")})
        fun text(key:String)=(args[key] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("$key must be text")
        when(text("action")) {
            "list","request" -> {
                val query=(args["query"] as? JsonPrimitive)?.takeIf{it.isString}?.content.orEmpty()
                val offer=coordinator.offer(owner,query)
                val exact=offer.choices.singleOrNull()?.takeIf{it.canConnect && query.isNotBlank() && (it.id==query || it.name.equals(query,true))}
                if(text("action")=="request" && exact!=null)buildJsonObject{put("receipt",json.encodeToJsonElement(coordinator.connect(owner,offer.id,exact.id)))}
                else buildJsonObject{put("offer",json.encodeToJsonElement(offer))}
            }
            "connect" -> buildJsonObject{put("receipt",json.encodeToJsonElement(coordinator.connect(owner,text("offerId"),text("sourceId"))))}
            "status" -> buildJsonObject{put("receipts",json.encodeToJsonElement(coordinator.receipts(owner)))}
            "cancel" -> {coordinator.cancel(owner,text("requestId"));buildJsonObject{put("cancelRequested",true)}}
            else -> error("Unsupported device connection action")
        }
    }
    fun specs()=listOf(McpToolSpec("device_connection","List real saved/opt-in discovered device choices, check an approved source's identity/contact, read receipts or cancel. No enrollment, arbitrary address scan, credential input, worker start or action permission changes. Requires owner feature access and exact source approval.",buildJsonObject {
        put("type","object");put("properties",buildJsonObject {
            put("action",buildJsonObject{put("type","string");put("enum",buildJsonArray{listOf("list","request","connect","status","cancel").forEach{add(it)}})})
            listOf("query","offerId","sourceId","requestId").forEach{put(it,buildJsonObject{put("type","string")})}
        });put("required",buildJsonArray{add("action")})
    }){args->McpToolResult.Json(invoke(actor(),args.jsonObject))})
}
