package com.meshlit.core.observability

import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

/**
 * Forwards finished spans to the user-supplied [TraceSink] so the
 * in-app log buffer can show them alongside normal log entries.
 *
 * Used in both [TracingMode.Local] and [TracingMode.Otel] so the
 * user always sees the spans locally *and* pushes them to Grafana
 * / Tempo when Otel is on.
 */
class SinkSpanProcessor(
    private val sink: TraceSink,
) : SpanProcessor {

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        // No-op — we only forward on end.
    }

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
        val attrs = span.attributes.asMap().mapKeys { it.key.key }
            .mapValues { it.value.toString() }
        sink.onSpan(span.name, attrs)
    }

    override fun isEndRequired(): Boolean = true

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
