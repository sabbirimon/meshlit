package com.meshlit.core.mcp.gateway
import kotlinx.serialization.Serializable
@Serializable enum class RouteProtocol { LLM, MCP, A2A, EXTENSION }
@Serializable enum class RouteSelection { PRIORITY, LOWEST_OBSERVED_LATENCY }
@Serializable data class UnifiedRoute(val id:String,val protocol:RouteProtocol,val targets:List<String>,
    val enabled:Boolean=true,val agentAllowed:Boolean=false,val selection:RouteSelection=RouteSelection.PRIORITY,
    val maxConcurrent:Int=1,val failureCooldownMs:Long=30000) {
    fun validate(){require(id.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,39}")));require(targets.size in 1..16 && targets.distinct().size==targets.size && targets.all{it.length in 1..160 && it.none(Char::isWhitespace)});require(maxConcurrent in 1..2 && failureCooldownMs in 1000..300000)}
}
@Serializable data class RouteObservation(val target:String,val observedAtMs:Long,val succeeded:Boolean,val latencyMs:Long)
object UnifiedRouteSelector {
    /** Choose once before dispatch. No retry after admission: mutations/billing may already
     * have happened. Missing latency is never represented as zero. */
    fun choose(route:UnifiedRoute,agent:Boolean,nowMs:Long,observations:Map<String,RouteObservation>,available:(String)->Boolean):String {
        route.validate();require(route.enabled && (!agent || route.agentAllowed));require(route.protocol!=RouteProtocol.EXTENSION){"Gateway extension adapter unavailable"}
        val choices=route.targets.filter{available(it) && observations[it]?.let{last->last.succeeded || nowMs>=last.observedAtMs && nowMs-last.observedAtMs>=route.failureCooldownMs}!=false}
        require(choices.isNotEmpty()){"No approved available target; failed routes are cooling down"}
        return if(route.selection==RouteSelection.PRIORITY) choices.first() else choices.minByOrNull { target ->
            observations[target]?.takeIf{it.succeeded && nowMs>=it.observedAtMs && nowMs-it.observedAtMs<=60000}?.latencyMs ?: Long.MAX_VALUE
        }!!
    }
}
