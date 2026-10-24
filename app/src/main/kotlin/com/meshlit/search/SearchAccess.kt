package com.meshlit.search

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

@Serializable data class SearchAccess(val webEnabled:Boolean=false,val agentWeb:Boolean=false,val agentLocal:Boolean=false,val agentWebPaused:Boolean=false) {
    fun requireWeb(agent:Boolean) {check(webEnabled && (!agent || (agentWeb && !agentWebPaused))){"Internet search is off or agent access is not granted"}}
}
/** Human grants are persisted. Agents may pause/resume only inside the existing Internet grant. */
class SearchAccessController(initial:SearchAccess=SearchAccess(),private val persist:(SearchAccess)->Unit={}) {
    private val mutable=MutableStateFlow(initial)
    val state=mutable.asStateFlow()
    private val active=mutableMapOf<Job,Boolean>()
    @Synchronized fun saveHuman(value:SearchAccess) {
        val old=mutable.value
        val revoked=(old.webEnabled && !value.webEnabled) || (old.agentWeb && !value.agentWeb) || (old.agentLocal && !value.agentLocal) || (!old.agentWebPaused && value.agentWebPaused)
        if(revoked) {
            val denyOnly=old.copy(webEnabled=old.webEnabled && value.webEnabled,agentWeb=old.agentWeb && value.agentWeb,agentLocal=old.agentLocal && value.agentLocal,agentWebPaused=old.agentWebPaused || value.agentWebPaused)
            mutable.value=denyOnly;cancelRevoked(denyOnly)
        }
        persist(value);mutable.value=value
    }
    @Synchronized fun setAgentPaused(paused:Boolean) {
        val old=mutable.value
        check(old.webEnabled && old.agentWeb){"An agent cannot enable Internet access without your saved grant"}
        val next=old.copy(agentWebPaused=paused)
        if(paused) {mutable.value=next;cancelRevoked(next)}
        persist(next);mutable.value=next
    }
    private fun cancelRevoked(value:SearchAccess) {active.filterValues{agent->!value.webEnabled || (agent && (!value.agentWeb || value.agentWebPaused))}.keys.toList().forEach{it.cancel(CancellationException("Search access revoked"))}}
    @Synchronized fun cancelWebRequests(){active.keys.toList().forEach{it.cancel(CancellationException("Search credential changed"))}}
    suspend fun <T> web(agent:Boolean,work:suspend()->T):T=coroutineScope {
        val job=currentCoroutineContext().job
        synchronized(this@SearchAccessController){mutable.value.requireWeb(agent);active[job]=agent}
        try {work().also{currentCoroutineContext().ensureActive();mutable.value.requireWeb(agent)}}
        finally{synchronized(this@SearchAccessController){active.remove(job)}}
    }
}
