package com.meshlit.gateway
import android.content.Context
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.control.*
import com.meshlit.core.mcp.gateway.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.core.common.control.ManagedFeature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID
/** Human-configured targets. Capability descriptions never grant authority or executable code. */
class UnifiedRouting(context:Context,private val backend:AgentBackend,private val remote:RemoteRoutes,private val contentPolicy:()->GatewayPolicy = {GatewayPolicy()}) {
    private val store=EncryptedCredentialStore(context,"unified-routes")
    private val gate=com.meshlit.operations.OperationsControl.get(context).gate
    private val json=Json{ignoreUnknownKeys=false}
    private val mutable=MutableStateFlow(store.get("routes")?.let{json.decodeFromString<List<UnifiedRoute>>(it)}.orEmpty())
    val routes=mutable.asStateFlow()
    private val observations=MutableStateFlow<Map<String,RouteObservation>>(emptyMap());val health=observations.asStateFlow()
    @Volatile private var generation=0L
    private val slots=java.util.concurrent.ConcurrentHashMap<String,Semaphore>()
    @Synchronized fun saveHuman(text:String){
        require(text.toByteArray().size<=65536);val next=json.decodeFromString<List<UnifiedRoute>>(text)
        require(next.size<=128 && next.map{it.id}.distinct().size==next.size);next.forEach{it.validate()}
        store.putCommitted("routes",json.encodeToString(next));generation++;mutable.value=next
        // Keep semaphores for unchanged IDs: editing does not grant an additional in-flight slot.
    }
    fun saved()=json.encodeToString(routes.value)
    private fun key(route:UnifiedRoute,target:String)="${route.protocol}:$target"
    suspend fun execute(id:String,args:JsonObject,agent:Boolean=false):JsonObject=gate.run(ManagedFeature.GATEWAY,agent) {
        require(args.toString().toByteArray().size<=32768);contentPolicy().check(args.toString())
        val route=routes.value.single{it.id==id};val epoch=generation
        val slot=slots.getOrPut(id){Semaphore(2)}
        withTimeout(180000){slot.withPermit {
            require(epoch==generation && routes.value.single{it.id==id}==route){"Route changed during admission"}
            // One permit is intentionally reserved for single-request routes.
            suspend fun dispatch():JsonObject {
                val target=UnifiedRouteSelector.choose(route,agent,System.currentTimeMillis(),observations.value.filterKeys{it.startsWith("${route.protocol}:")}.values.associateBy{it.target}) { candidate ->
                    if(route.protocol==RouteProtocol.LLM) true else remote.tools(agent).any{it.jsonObject["name"]?.jsonPrimitive?.content==candidate} &&
                        remote.toolProtocol(candidate)==route.protocol
                }
                val start=android.os.SystemClock.elapsedRealtime();var succeeded=false
                return try {
                    val result=if(route.protocol==RouteProtocol.LLM){
                        val command=AgentCommand(UUID.randomUUID().toString(),AgentOperation.MODEL_GENERATE,modelId=target,prompt=args["prompt"]!!.jsonPrimitive.content,maxTokens=args["maxTokens"]?.jsonPrimitive?.int ?: 256)
                        val controller=if(agent) backend.controller else backend.humanController
                        val admitted=controller.submit(command)
                        try {val job=controller.jobs.first{list->list.any{it.command.requestId==admitted.command.requestId && it.terminal}}.single{it.command.requestId==admitted.command.requestId}
                            check(job.phase==AgentJobPhase.SUCCEEDED){"Routed model task failed; no automatic replay"};job.result!!.jsonObject
                        }catch(e:CancellationException){withContext(NonCancellable){controller.cancel(command.requestId)};throw e}
                    }else remote.invoke(target,args,agent)
                    require(generation==epoch && routes.value.single{it.id==id}==route){"Route revoked while remote outcome may be uncertain"}
                    contentPolicy().check(result.toString(),true)
                    succeeded=true
                    buildJsonObject{put("routeId",id);put("target",target);put("protocol",route.protocol.name);put("result",result)}
                }finally{observations.update{it+(key(route,target) to RouteObservation(target,System.currentTimeMillis(),succeeded,android.os.SystemClock.elapsedRealtime()-start))}}
            }
            // Acquire the second permit for routes restricted to one concurrent invocation.
            if(route.maxConcurrent==1) singleLocks.getOrPut(id){Mutex()}.withLock{dispatch()} else dispatch()
        }}
    }
    private val singleLocks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
}
