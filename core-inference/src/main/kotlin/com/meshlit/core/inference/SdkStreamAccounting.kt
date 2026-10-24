package com.meshlit.core.inference

import ai.runanywhere.proto.v1.LLMStreamEvent
import ai.runanywhere.proto.v1.LLMStreamEventKind

/** Text deltas are rendering events. Only terminal runtime metadata measures tokens. */
internal class SdkStreamAccounting {
    private val text=StringBuilder()
    private var terminal:LLMStreamEvent?=null
    fun accept(event:LLMStreamEvent):String? {
        check(terminal==null){"SDK emitted data after completion"}
        check(event.error_code==0 && event.error_message.isBlank()){"SDK stream failed (${event.error_code})"}
        if(event.is_final){
            val result=event.result
            check(result==null || result.error_code==0 && result.error_message.isBlank()){"SDK generation failed"}
            terminal=event
            return null
        }
        if(event.event_kind!=LLMStreamEventKind.LLM_STREAM_EVENT_KIND_TOKEN || event.token.isEmpty()) return null
        check(text.length+event.token.length<=2*1024*1024){"SDK output exceeds text limit"}
        text.append(event.token)
        return event.token
    }
    fun finish(durationMs:Long):InferenceResult {
        val final=checkNotNull(terminal){"SDK stream ended without terminal metadata"}
        val native=final.result
        val finalText=text.toString().ifEmpty{native?.text.orEmpty()}
        // Pinned 0.20.12 terminal counters have no provenance/estimated flag.
        // Device evidence reports 3 for output the backend measured as 6 tokens.
        // Do not promote SDK estimates/chunk counters to native usage or billing.
        val reason=native?.finish_reason?.takeIf{it.isNotBlank()} ?: final.finish_reason
        val finish=when(reason.lowercase()){
            "length","max_tokens","limit"->FinishReason.MAX_TOKENS
            "stop_sequence"->FinishReason.STOP_SEQUENCE
            "cancelled","canceled"->FinishReason.CANCELLED
            "error"->FinishReason.ERROR
            else->FinishReason.NATURAL_STOP
        }
        return InferenceResult(null,null,durationMs,null,finish,finalText)
    }
}
