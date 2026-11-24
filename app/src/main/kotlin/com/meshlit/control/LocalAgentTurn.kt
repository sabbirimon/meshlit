package com.meshlit.control
import kotlinx.coroutines.Job
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
/** Carries a local chat's cancellation lifetime through bounded tool calls without granting authority. */
class LocalAgentTurn(val job:Job,val conversationId:String?=null):AbstractCoroutineContextElement(Key) {
    companion object Key:CoroutineContext.Key<LocalAgentTurn>
}
