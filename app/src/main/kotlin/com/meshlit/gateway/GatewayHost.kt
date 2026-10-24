package com.meshlit.gateway

import android.content.Context
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.control.*
import com.meshlit.core.mcp.gateway.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable data class GatewaySettings(val port:Int=18893,val modelId:String="",val policy:GatewayPolicy=GatewayPolicy())
/** Configuration edits are human-only; restart/rekey revokes the old listener and running request handlers. */
class GatewayHost(context:Context,private val backend:AgentBackend,private val lab:com.meshlit.security.SecurityLab,private val packages:com.meshlit.security.LabPackages,private val search:()->com.meshlit.search.AppSearchService) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store=EncryptedCredentialStore(context,"agent-gateway")
    val remoteRoutes=RemoteRoutes(context)
    val routing=UnifiedRouting(context,backend,remoteRoutes,{settings.value.policy})
    private val json=Json {ignoreUnknownKeys=true}
    private val _settings=MutableStateFlow(store.get("settings")?.let {runCatching {json.decodeFromString<GatewaySettings>(it)}.getOrNull()} ?: GatewaySettings())
    val settings=_settings.asStateFlow()
    private val _clients=MutableStateFlow(store.get("clients")?.let{runCatching{json.decodeFromString<List<GatewayClient>>(it).also{list->require(list.size<=64);require(list.map{client->client.id}.distinct().size==list.size);list.forEach{client->client.validate()}}}.getOrNull()} ?: emptyList())
    val clients=_clients.asStateFlow()
    private val access=GatewayClientAccess(records={clients.value})
    @Synchronized fun issueClient(name:String,modelIds:Set<String>,scopes:Set<GatewayScope>,tools:Set<String> = emptySet(),hours:Int=1,maxTokens:Int=512,rpm:Int=30):IssuedGatewayKey {
        require(_clients.value.size<64){"Remove an expired client before adding another"}
        require(hours in 1..720)
        val issued=GatewayClientAccess.issue(name,modelIds,scopes,tools,hours*3_600_000L,maxTokens,rpm)
        stop();saveClients(_clients.value+issued.client);return issued
    }
    @Synchronized fun revokeClient(id:String){stop();saveClients(_clients.value.map{if(it.id==id) it.copy(revoked=true) else it})}
    @Synchronized fun removeClient(id:String){stop();saveClients(_clients.value.filterNot{it.id==id})}
    private fun saveClients(value:List<GatewayClient>){store.putCommitted("clients",json.encodeToString(value));_clients.value=value}
    private val _running=MutableStateFlow(false);val running=_running.asStateFlow()
    private var server:EmbeddedGateway?=null
    private val expirations=java.util.concurrent.ConcurrentHashMap<String,Job>()
    private val epoch=java.util.concurrent.atomic.AtomicLong()
    private val cleanupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun token():String=store.get("token") ?: (UUID.randomUUID().toString()+UUID.randomUUID()).also {store.putCommitted("token",it)}
    @Synchronized fun save(value:GatewaySettings) {require(value.port in 1024..65535);require(value.modelId.length<=160);value.policy.validate();stop();store.putCommitted("settings",json.encodeToString(value));_settings.value=value}
    @Synchronized fun rotate() {stop();store.putCommitted("token",UUID.randomUUID().toString()+UUID.randomUUID())}
    @Synchronized fun start() {
        operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.GATEWAY)
        check(!com.meshlit.BuildConfig.PLAY_REVIEW) {"Inbound gateway is not enabled in Play Review"}
        stop();val config=settings.value
        val next=EmbeddedGateway(config.port,token(),{settings.value.policy},::tools,::invoke,::models,::chat,::send,::get,::cancel,access)
        try {next.start(15000,false);server=next;_running.value=true} catch(e:Exception) {next.stop();throw e}
    }
    @Synchronized fun stop() {remoteRoutes.resetSession();epoch.incrementAndGet();server?.stop();server=null;_running.value=false;val ids=owned.keys.toList();owned.clear();expirations.values.forEach{it.cancel()};expirations.clear();cleanupScope.launch {backend.controller.ready.await();ids.forEach {id->backend.controller.jobs.value.firstOrNull {it.command.requestId==id && !it.terminal}?.let {backend.controller.cancel(id)}}}}
    private fun tools()=buildJsonArray {
        fun tool(name:String,description:String,schema:JsonObject)=add(buildJsonObject {put("name",name);put("description",description);put("inputSchema",schema)})
        tool("meshlit_command_submit","Submit a durable command; saved agent permissions remain required",AgentCommandSchema.describe())
        val idSchema=buildJsonObject {put("type","object");put("required",buildJsonArray {add("id")});put("properties",buildJsonObject {put("id",buildJsonObject {put("type","string");put("maxLength",80)})})}
        tool("meshlit_job_status","Read a job submitted by this gateway",idSchema)
        tool("meshlit_job_cancel","Cancel a job submitted by this gateway",idSchema)
        tool("lab_package_action","List/install/uninstall owner-allowlisted pip packages inside ready VM",buildJsonObject {put("type","object");put("required",buildJsonArray {add("action")});put("properties",buildJsonObject {put("action",buildJsonObject {put("type","string");put("enum",buildJsonArray {add("list");add("install");add("uninstall")})});put("package",buildJsonObject {put("type","string");put("maxLength",100)})})})
        tool("security_assessments_list","List assessments explicitly delegated to agents",buildJsonObject {put("type","object");put("properties",buildJsonObject {})})
        tool("security_assessment_run","Run an approved read-only tool inside an SSH-ready VM",buildJsonObject {put("type","object");put("required",buildJsonArray {add("id");add("tool")});put("properties",buildJsonObject {put("id",buildJsonObject {put("type","string")});put("tool",buildJsonObject {put("type","string");put("enum",buildJsonArray {add("apk_inventory");add("pcap_summary");add("tshark");add("sleuthkit");add("elf_inventory");add("sqlite_metadata")})})})})
        tool("gateway_routes_list","List enabled unified routes without credentials",buildJsonObject{put("type","object")})
        tool("gateway_route_execute","Execute an owner-delegated unified LLM/MCP/A2A route once",buildJsonObject{put("type","object");put("required",buildJsonArray{add("routeId");add("arguments")});put("properties",buildJsonObject{put("routeId",buildJsonObject{put("type","string")});put("arguments",buildJsonObject{put("type","object")})})})
        search().specs().forEach{spec->tool(spec.name,spec.description,spec.inputSchema.jsonObject)}
        remoteRoutes.tools().forEach {add(it)}
    }
    private val owned=java.util.concurrent.ConcurrentHashMap<String,String>()
    private suspend fun invoke(name:String,args:JsonObject):JsonObject = operations.run(com.meshlit.core.common.control.ManagedFeature.GATEWAY,true) { invokeManaged(name,args) }
    private suspend fun invokeManaged(name:String,args:JsonObject):JsonObject {
        if(name in setOf("app_search","web_search","search_access","device_settings_read")) {
            val result=search().specs().single{it.name==name}.handler(args)
            return buildJsonObject{put("content",buildJsonArray{add(buildJsonObject{put("type","text");put("text",when(result){is com.meshlit.core.mcp.McpToolResult.Json->result.value.toString();is com.meshlit.core.mcp.McpToolResult.Text->result.text;is com.meshlit.core.mcp.McpToolResult.Error->result.message})})});put("isError",result is com.meshlit.core.mcp.McpToolResult.Error)}
        }
        if(name.startsWith("remote_")) return remoteRoutes.invoke(name,args)
        val result=when(name) {
            "gateway_routes_list"->json.encodeToJsonElement(routing.routes.value.filter{it.enabled && it.agentAllowed})
            "gateway_route_execute"->routing.execute(args["routeId"]!!.jsonPrimitive.content,args["arguments"]!!.jsonObject,true)
            "lab_package_action"->packages.run(args["action"]!!.jsonPrimitive.content,"pip","repo",args["package"]?.jsonPrimitive?.content.orEmpty(),"",agent=true)
            "security_assessments_list"->buildJsonObject {put("assessments",buildJsonArray {lab.assessments.value.filter {it.agentAllowed && it.expiresAtMs>System.currentTimeMillis()}.forEach {assessment->add(buildJsonObject {put("id",assessment.id);put("sha256",assessment.sha256);put("tools",buildJsonArray {assessment.tools.forEach {add(it)}})})}})}
            "security_assessment_run"->lab.run(args["id"]!!.jsonPrimitive.content,args["tool"]!!.jsonPrimitive.content,agent=true)
            "meshlit_command_submit"->{
                val command=json.decodeFromJsonElement<AgentCommand>(args)
                backend.controller.ready.await()
                require(owned[command.requestId]==currentGatewayClient().id || backend.controller.jobs.value.none {it.command.requestId==command.requestId})
                require(owned.size<100 || owned[command.requestId]==currentGatewayClient().id) {"Restart gateway after retained admission limit"}
                val job=admit(command);safeJob(job)
            }
            "meshlit_job_status"->safeJob(ownedJob(args["id"]!!.jsonPrimitive.content) ?: throw NoSuchElementException())
            "meshlit_job_cancel"->{val id=args["id"]!!.jsonPrimitive.content;require(owned[id]==currentGatewayClient().id);safeJob(backend.controller.cancel(id) ?: throw NoSuchElementException())}
            else->{require(name.startsWith("remote_"));remoteRoutes.invoke(name,args)}
        }
        return buildJsonObject {put("content",buildJsonArray {add(buildJsonObject {put("type","text");put("text",result.toString())})});put("isError",false)}
    }
    private suspend fun admit(command:AgentCommand):AgentJob {
        operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.GATEWAY,true)
        val started=epoch.get();check(running.value) {"Gateway is stopped"}
        val principal=currentGatewayClient()
        if(command.operation==AgentOperation.MODEL_GENERATE) {
            require(principal.allowsModel(command.modelId.orEmpty()))
            require(command.maxTokens<=principal.maxOutputTokens)
        }
        val existing=owned.putIfAbsent(command.requestId,principal.id)
        require(existing==null || existing==principal.id)
        if(!principal.owner && existing==null) expirations[command.requestId]=cleanupScope.launch {
            delay((principal.expiresAtMs-System.currentTimeMillis()).coerceAtLeast(0))
            if(owned[command.requestId]==principal.id) backend.controller.cancel(command.requestId)
        }
        try {
            val result=backend.controller.submit(command)
            if(epoch.get()!=started || !running.value) {withContext(NonCancellable) {backend.controller.cancel(command.requestId)};throw CancellationException("Gateway revoked")}
            return result
        } catch(e:Exception) {owned.remove(command.requestId);throw e}
    }
    private suspend fun ownedJob(id:String):AgentJob? {backend.controller.ready.await();return if(owned[id]==currentGatewayClient().id) backend.controller.jobs.value.firstOrNull {it.command.requestId==id} else null}
    private fun safeJob(job:AgentJob)=buildJsonObject {put("id",job.command.requestId);put("phase",job.phase.name);job.result?.let {settings.value.policy.check(it.toString(),true);put("result",it)};job.errorCode?.let {put("errorCode",it)}}
    private suspend fun models():JsonArray {
        val value=backend.executeHuman(AgentCommand(UUID.randomUUID().toString(),AgentOperation.MODELS_LIST))
        return buildJsonArray {(value.jsonObject["models"] as? JsonArray).orEmpty().filter {it.jsonObject["installed"]?.jsonPrimitive?.booleanOrNull==true || it.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull==true}.forEach {add(buildJsonObject {put("id",it.jsonObject["modelId"]!!);put("object","model");put("owned_by","meshlit")})}
            routing.routes.value.filter{it.enabled && it.agentAllowed && it.protocol==RouteProtocol.LLM}.forEach{route->add(buildJsonObject{put("id","route:${route.id}");put("object","model");put("owned_by","meshlit-routes")})}
        }
    }
    private suspend fun chat(input:JsonObject):JsonObject {
        val alias=input["model"]?.jsonPrimitive?.content.orEmpty()
        if(alias.startsWith("route:")){
            val messages=input["messages"]!!.jsonArray;require(messages.size in 1..40)
            val prompt=messages.joinToString("\n\n"){val entry=it.jsonObject;val role=entry["role"]!!.jsonPrimitive.content;require(role in setOf("user","assistant","system"));role+": "+entry["content"]!!.jsonPrimitive.also{require(it.isString)}.content}
            val route=routing.routes.value.single{it.id==alias.removePrefix("route:")};require(route.protocol==RouteProtocol.LLM)
            val value=routing.execute(route.id,buildJsonObject{put("prompt",prompt);put("maxTokens",(input["max_completion_tokens"] ?: input["max_tokens"])?.jsonPrimitive?.int ?: 256)},true)
            return buildJsonObject{put("id","chatcmpl-${UUID.randomUUID()}");put("object","chat.completion");put("created",System.currentTimeMillis()/1000);put("model",alias);put("choices",buildJsonArray{add(buildJsonObject{put("index",0);put("finish_reason","stop");put("message",buildJsonObject{put("role","assistant");put("content",value["result"]!!.jsonObject["text"]!!.jsonPrimitive.content)})})})}
        }
        require(input["model"]?.jsonPrimitive?.content==settings.value.modelId && settings.value.modelId.isNotBlank()) {"Use owner-selected gateway model"}
        val messages=input["messages"] as? JsonArray ?: throw IllegalArgumentException()
        require(messages.size in 1..40)
        val prompt=messages.joinToString("\n\n") {val entry=it.jsonObject;val role=entry["role"]!!.jsonPrimitive.content;require(role in setOf("user","assistant","system"));val text=entry["content"] as? JsonPrimitive ?: throw IllegalArgumentException();require(text.isString);"$role: ${text.content}"}
        val command=AgentCommand(UUID.randomUUID().toString(),AgentOperation.MODEL_GENERATE,modelId=settings.value.modelId,prompt=prompt,maxTokens=(input["max_completion_tokens"] ?: input["max_tokens"])?.jsonPrimitive?.int ?: 256,temperature=input["temperature"]?.jsonPrimitive?.float ?: 0.7f)
        require(owned.size<100) {"Gateway retained admission limit reached"}
        val admitted=admit(command)
        try {
            val job=backend.controller.jobs.first {jobs->jobs.any {it.command.requestId==admitted.command.requestId && it.terminal}}.first {it.command.requestId==command.requestId}
            check(job.phase==AgentJobPhase.SUCCEEDED) {"Model task failed"}
            val text=job.result!!.jsonObject["text"]!!.jsonPrimitive.content
            settings.value.policy.check(text,true)
            return buildJsonObject {put("id","chatcmpl-${command.requestId}");put("object","chat.completion");put("created",System.currentTimeMillis()/1000);put("model",command.modelId);put("choices",buildJsonArray {add(buildJsonObject {put("index",0);put("finish_reason","stop");put("message",buildJsonObject {put("role","assistant");put("content",text)})})})}
        } catch(e:kotlinx.coroutines.CancellationException) {kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {backend.controller.cancel(command.requestId)};throw e}
    }
    private suspend fun send(params:JsonObject):JsonObject {
        require(settings.value.modelId.isNotBlank()) {"Select gateway model"}
        val message=params["message"]!!.jsonObject
        require(message["role"]?.jsonPrimitive?.content=="user")
        val parts=message["parts"] as? JsonArray ?: throw IllegalArgumentException()
        require(parts.size in 1..32)
        val text=parts.joinToString("\n") {val part=it.jsonObject;require(part["kind"]?.jsonPrimitive?.content=="text");part["text"]!!.jsonPrimitive.content}
        settings.value.policy.check(text)
        require(owned.size<100)
        val id=UUID.randomUUID().toString();val command=AgentCommand(id,AgentOperation.MODEL_GENERATE,modelId=settings.value.modelId,prompt=text,maxTokens=minOf(256,currentGatewayClient().maxOutputTokens))
        admit(command);return get(id)!!
    }
    private suspend fun get(id:String):JsonObject?=ownedJob(id)?.let {job ->buildJsonObject {
        put("id",id);put("contextId",id);put("kind","task")
        put("status",buildJsonObject {put("state",when(job.phase) {AgentJobPhase.QUEUED->"submitted";AgentJobPhase.RUNNING->"working";AgentJobPhase.SUCCEEDED->"completed";AgentJobPhase.CANCELLED->"canceled";else->"failed"})})
        if(job.phase==AgentJobPhase.SUCCEEDED) {val text=job.result?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty();settings.value.policy.check(text,true);put("artifacts",buildJsonArray {add(buildJsonObject {put("artifactId",id);put("parts",buildJsonArray {add(buildJsonObject {put("kind","text");put("text",text)})})})})}
    }}
    private suspend fun cancel(id:String):JsonObject? {if(owned[id]!=currentGatewayClient().id) return null;backend.controller.cancel(id);return get(id)}
}
