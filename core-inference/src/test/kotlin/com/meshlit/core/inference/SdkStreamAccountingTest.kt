package com.meshlit.core.inference

import ai.runanywhere.proto.v1.*
import org.junit.Assert.*
import org.junit.Test

class SdkStreamAccountingTest {
    @Test fun unverifiedSdkCountersDoNotBecomeNativeUsage(){
        val accounting=SdkStreamAccounting()
        accounting.accept(LLMStreamEvent(token="One chunk contains many tokens",event_kind=LLMStreamEventKind.LLM_STREAM_EVENT_KIND_TOKEN))
        accounting.accept(LLMStreamEvent(token=".",event_kind=LLMStreamEventKind.LLM_STREAM_EVENT_KIND_TOKEN))
        accounting.accept(LLMStreamEvent(is_final=true,result=LLMStreamFinalResult(prompt_tokens=9,completion_tokens=7,tokens_per_second=12f,finish_reason="length")))
        val result=accounting.finish(800)
        assertNull(result.promptTokens);assertNull(result.generatedTokens);assertNull(result.tokensPerSecond)
        assertEquals(FinishReason.MAX_TOKENS,result.finishReason)
    }
    @Test fun terminalWithoutUsageRemainsUnknown(){
        val accounting=SdkStreamAccounting();accounting.accept(LLMStreamEvent(is_final=true))
        val result=accounting.finish(20)
        assertNull(result.promptTokens);assertNull(result.generatedTokens);assertNull(result.tokensPerSecond)
    }
    @Test fun missingTerminalCannotReportSuccess(){
        assertTrue(runCatching{SdkStreamAccounting().finish(20)}.isFailure)
    }
    @Test fun terminalNativeErrorFails(){
        assertTrue(runCatching{SdkStreamAccounting().accept(LLMStreamEvent(is_final=true,result=LLMStreamFinalResult(error_code=3)))}.isFailure)
    }
}
