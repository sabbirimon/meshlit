package com.meshlit.core.observability

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Convenience wrapper around the active [Tracer] that every
 * emitter in the app uses. Wraps the OpenTelemetry call sites in
 * a `try/catch` so a broken or half-initialised SDK never crashes
 * the caller — telemetry is best-effort.
 *
 * Usage from any suspend block:
 *
 *     TracerHolder.span("inference.run", mapOf("model" to "llama-3")) {
 *         engine.generate(prompt)
 *     }
 *
 * In [TracingMode.Off] the tracer is [io.opentelemetry.api.OpenTelemetry.noop]
 * so the call still returns immediately — no measurable cost.
 */
object TracerHolder {

    @Volatile private var controller: TracingController = TracingController(NoopSink)

    /** Wire the singleton from app startup. Replaces prior wiring. */
    fun bind(controller: TracingController) {
        this.controller = controller
    }

    /** Read-only handle to the active controller (for tests + UI). */
    fun controller(): TracingController = controller

    /**
     * Open a span, run [block] inside its scope on the supplied
     * [coroutineContext] (defaults to [Dispatchers.Default] so we
     * don't inherit a UI dispatcher), and end the span — recording
     * any thrown exception as an error status.
     */
    suspend fun <T> span(
        name: String,
        attributes: Map<String, String> = emptyMap(),
        coroutineContext: kotlin.coroutines.CoroutineContext = Dispatchers.Default,
        block: suspend (Span) -> T,
    ): T = withContext(coroutineContext) {
        val tracer: Tracer = controller.tracer("com.meshlit")
        val builder = tracer.spanBuilder(name)
        attributes.forEach { (k, v) -> builder.setAttribute(k, v) }
        val span: Span = builder.startSpan()
        var scope: Scope? = null
        try {
            scope = span.makeCurrent()
            block(span)
        } catch (t: Throwable) {
            span.recordException(t)
            span.setStatus(StatusCode.ERROR, t.message ?: t.javaClass.simpleName)
            throw t
        } finally {
            scope?.close()
            span.end()
        }
    }
}
