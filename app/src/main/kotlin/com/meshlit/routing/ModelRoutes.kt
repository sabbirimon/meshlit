package com.meshlit.routing

import android.content.Context
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.models.ModelLibrary
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ModelRouteState(val running:Boolean=false,val routeId:String?=null,val step:Int?=null,
    val modelId:String?=null,val result:ModelRouteResult?=null)

/** Shared human/chat/MCP routes. No retries, provider substitution or hidden cloud fallback. */
class ModelRoutes(context:Context,private val library:ModelLibrary,private val inference:InferenceCoordinator,
    private val online:OnlineProviders) {
    private val store=EncryptedCredentialStore(context,"model-routes-v1")
    private val json=Json{ignoreUnknownKeys=false}
    private val _routes=MutableStateFlow(runCatching{store.get("routes")?.let{json.decodeFromString<List<ModelRoute>>(it)}}.getOrNull().orEmpty())
    val routes=_routes.asStateFlow()
    private val _state=MutableStateFlow(ModelRouteState());val state=_state.asStateFlow()
    private val lock=Mutex()
    @Synchronized fun save(route:ModelRoute){
        route.validate();require(_routes.value.size<30 || _routes.value.any{it.id==route.id})
        check(!_state.value.running){"Stop the active route before editing"}
        val updated=_routes.value.filterNot{it.id==route.id}+route
        store.put("routes",json.encodeToString(updated));_routes.value=updated
    }
    @Synchronized fun remove(id:String){
        check(!_state.value.running){"Stop the active route before deleting"}
        val updated=_routes.value.filterNot{it.id==id};store.put("routes",json.encodeToString(updated));_routes.value=updated
    }
    suspend fun execute(routeId:String?,scenario:String,prompt:String,maxTokens:Int=512,agent:Boolean=false,authorize:()->Unit={}):ModelRouteResult = lock.withLock {
        authorize();library.ready.await()
        val route=ModelRouter.select(_routes.value,routeId,scenario,prompt)
        require(!agent || route.agentAllowed){"This route does not allow agent execution"}
        _state.value=ModelRouteState(true,route.id)
        try {
            val result=ModelRouter.execute(route,prompt,maxTokens,preflight={step->
                authorize()
                // Re-read configuration at each boundary so profile/delegation revocation is honored.
                val current=_routes.value.firstOrNull{it.id==route.id}
                require(current?.enabled==true && (!agent || current.agentAllowed)){"Route was disabled or revoked"}
                if(step.modelId.startsWith("cloud:")) {
                    val p=online.profiles.value.firstOrNull{it.id==step.modelId.removePrefix("cloud:")}
                        ?: error("Missing online profile ${step.modelId}")
                    require(p.enabled && (!agent || p.agentAllowed)){"Online profile is disabled or not delegated"}
                    require(!p.requiresApiKey || online.resolveToken(p,agent).isNotBlank()){"Online profile requires a saved API key"}
                } else {
                    require(library.models.value.any{it.id==step.modelId && it.installed}){"Install local model ${step.modelId} first"}
                    require(library.devicePlan().allowHeavyWork){"Current device policy blocks local work"}
                }
            },run={step,input,limit->
                if(step.modelId.startsWith("cloud:")) {
                    val(p,r)=online.generate(step.modelId.removePrefix("cloud:"),listOf(OnlineMessage("user",input)),step.instructions,limit,agent=agent)
                    RouteStepReply(r.text,r.inputTokens,r.outputTokens,r.estimatedCost(p),p.currency)
                } else {
                    library.load(step.modelId)
                    val model=library.models.value.first{it.id==step.modelId}
                    val result=inference.infer(InferenceRequest(prompt=step.instructions.takeIf{it.isNotBlank()}?.let{"Instructions: $it\n\n"}.orEmpty()+input,
                        maxTokens=limit,onToken={},expectedModelPath=model.path))
                    when(result){
                        is MeshlitResult.Success->RouteStepReply(result.value.finalText)
                        is MeshlitResult.Failure->error(result.error.tag)
                    }
                }
            },onStep={index,model->_state.value=ModelRouteState(true,route.id,index,model)})
            _state.value=_state.value.copy(result=result)
            result
        } finally {_state.value=_state.value.copy(running=false)}
    }
}
