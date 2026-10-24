package com.meshlit.core.observability
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assert.*
import org.junit.Test
class TracingPrivacyTest {
    @Test fun exportBoundaryRemovesPromptUrlExceptionAndArbitraryContext() {
        var result:SpanData?=null
        val provider=SdkTracerProvider.builder().addSpanProcessor(object:SpanProcessor {
            override fun onStart(c:Context,s:ReadWriteSpan){};override fun isStartRequired()=false
            override fun onEnd(s:ReadableSpan){result=TelemetryPrivacy.span(s.toSpanData())}
            override fun isEndRequired()=true
            override fun shutdown()=CompletableResultCode.ofSuccess()
            override fun forceFlush()=CompletableResultCode.ofSuccess()
        }).build()
        provider.get("test").spanBuilder("inference.run").startSpan().apply {
            setAttribute("url.full","https://user:pass@example/?token=secret");setAttribute("prompt","private content")
            setAttribute("output_tokens",4L);recordException(Exception("secret prompt"));end()
        }
        assertEquals(1,result!!.attributes.size());assertTrue(result!!.events.isEmpty());assertFalse(result!!.attributes.toString().contains("secret"));provider.close()
    }
}
