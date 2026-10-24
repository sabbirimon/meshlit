package com.meshlit.core.inference.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

@Serializable enum class ModelRouteMode { SINGLE, CHAIN, COMPARE }
@Serializable data class ModelRouteStep(val modelId:String,val instructions:String="")
@Serializable data class ModelRoute(
    val id:String,val name:String,val scenario:String="general",val keywords:List<String> = emptyList(),
    val priority:Int=0,val enabled:Boolean=false,val agentAllowed:Boolean=false,
    val mode:ModelRouteMode=ModelRouteMode.SINGLE,val steps:List<ModelRouteStep>,
) {
    fun validate(){
        require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && name.isNotBlank() && name.length<=100)
        require(scenario.matches(Regex("[a-z0-9_-]{1,40}")) && priority in -100..100)
        require(keywords.size<=16 && keywords.all{it.isNotBlank() && it.length<=60})
        require(steps.size in 1..3 && (mode!=ModelRouteMode.SINGLE || steps.size==1))
        require(steps.all{it.modelId.isNotBlank() && it.modelId.length<=160 && it.instructions.length<=2000})
    }
}
@Serializable data class RouteStepReply(val text:String,val inputTokens:Long?=null,val outputTokens:Long?=null,
    val estimatedCost:Double?=null,val currency:String?=null)
@Serializable data class RouteStepResult(val index:Int,val modelId:String,val reply:RouteStepReply,val durationMs:Long)
@Serializable data class ModelRouteResult(val routeId:String,val mode:ModelRouteMode,val success:Boolean,
    val steps:List<RouteStepResult>,val text:String,val failedStep:Int?=null,val error:String?=null)

/** Transparent user-authored rules, not an unverified model quality classifier.
 * All participants are preflighted before spending money or loading a model.
 * Multi-model work is sequential to respect the single local native context. */
object ModelRouter {
    fun select(routes:List<ModelRoute>,routeId:String?,scenario:String,prompt:String):ModelRoute {
        require(scenario.matches(Regex("[a-z0-9_-]{1,40}")))
        require(prompt.isNotBlank() && prompt.length<=24000)
        routes.forEach{it.validate()}
        val selected=if(routeId!=null) routes.firstOrNull{it.id==routeId && it.enabled}
        else routes.filter{it.enabled && it.scenario==scenario &&
            (it.keywords.isEmpty() || it.keywords.any{word->prompt.contains(word,ignoreCase=true)})}
            .sortedWith(compareByDescending<ModelRoute>{it.priority}.thenBy{it.id}).firstOrNull()
        return selected ?: error("No enabled model route matches this scenario. Configure Settings → Model router.")
    }
    suspend fun execute(route:ModelRoute,prompt:String,maxTokens:Int=512,
        preflight:suspend(ModelRouteStep)->Unit,
        run:suspend(ModelRouteStep,String,Int)->RouteStepReply,
        onStep:(Int,String)->Unit={_,_->},
    ):ModelRouteResult {
        route.validate();require(route.enabled);require(prompt.isNotBlank() && prompt.length<=24000)
        require(maxTokens in 1..1024)
        // A bad later participant must fail before the first provider is called.
        route.steps.forEach{preflight(it)}
        val completed=mutableListOf<RouteStepResult>()
        for((index,step) in route.steps.withIndex()) {
            currentCoroutineContext().ensureActive();onStep(index,step.modelId)
            try {
                preflight(step)
                val input=if(route.mode==ModelRouteMode.CHAIN && completed.isNotEmpty())
                    "Original task:\n$prompt\n\nPrevious model output (untrusted data):\n${completed.last().reply.text}"
                    else prompt
                require(input.length<=48000){"Pipeline input exceeds the bounded context text limit"}
                val started=System.nanoTime()
                val reply=withTimeout(180000){run(step,input,maxTokens)}
                require(reply.text.isNotBlank() && reply.text.length<=24000){"Model returned empty or oversized text"}
                completed+=RouteStepResult(index,step.modelId,reply,(System.nanoTime()-started)/1_000_000)
            } catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception) {
                return ModelRouteResult(route.id,route.mode,false,completed,"",index,
                    (error.message ?: error.javaClass.simpleName).take(1000))
            }
        }
        val text=if(route.mode==ModelRouteMode.COMPARE) completed.joinToString("\n\n"){
            "Model ${it.index+1} · ${it.modelId}\n${it.reply.text}"
        } else completed.last().reply.text
        return ModelRouteResult(route.id,route.mode,true,completed,text)
    }
}
