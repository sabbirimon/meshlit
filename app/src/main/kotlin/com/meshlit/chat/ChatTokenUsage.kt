package com.meshlit.chat

import kotlinx.serialization.Serializable

/** Counts come only from the engine/provider. A streamed text callback is not a token. */
@Serializable data class ChatTokenUsage(val source:String,val requestedOutput:Int,val input:Long?,val output:Long?,
    val cached:Long?,val elapsedMs:Long,val tokensPerSecond:Double?,val rateBasis:String?,val contextCapacity:Int?,val finishReason:String?,val budgetReason:String?=null)

internal fun chatTokenUsage(source:String,requested:Int,input:Long?,output:Long?,cached:Long?,elapsedMs:Long,
    runtimeRate:Float?,context:Int?,finishReason:String?=null):ChatTokenUsage {
    val actualInput=input?.takeIf{it>=0};val actualOutput=output?.takeIf{it>=0}
    val native=runtimeRate?.takeIf{it.isFinite() && it>0 && actualOutput!=null && actualOutput>0}?.toDouble()
    val elapsed=elapsedMs.coerceAtLeast(0)
    val average=if(actualOutput!=null && actualOutput>0 && elapsed>0) actualOutput*1000.0/elapsed else null
    return ChatTokenUsage(source,requested,actualInput,actualOutput,cached?.takeIf{it>=0 && actualInput!=null && it<=actualInput},elapsed,
        native ?: average,if(native!=null) "Runtime-reported rate" else if(average!=null) "End-to-end average · includes input/network time" else null,
        context?.takeIf{it>0},finishReason)
}
