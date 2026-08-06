package com.meshlit.core.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer

/**
 * Central facade for the tracing subsystem. The app instantiates
 * one of these at cold start and holds it for the process
 * lifetime.
 *
 * Responsibilities:
 *   - Hold the current [Tracer] (real OTel or no-op).
 *   - Own the [TracingMode] state so observers can reconfigure.
 *   - Provide the [span] helper every emitter calls.
 *
 * Wiring:
 *   - `MeshlitApplication.onCreate` calls [start] with the
 *     initial mode + OTLP endpoint URL.
 *   - `MeshlitApplication` observes settings flows; on change it
 *     calls [reconfigure] which re-initialises the SDK in-place.
 *   - Callers use [tracer] + [span] without checking the mode —
 *     the no-op tracer is the same type so the callsite is
 *     mode-agnostic.
 */
class TracingController(
    private val sink: TraceSink = NoopSink,
) {
    @Volatile private var current: OpenTelemetry = OpenTelemetry.noop()
    @Volatile private var mode: TracingMode = TracingMode.Off

    /** Read-only view of the active mode (used by the Log screen). */
    fun mode(): TracingMode = mode

    /** Returns the configured tracer. Never null. */
    fun tracer(name: String): Tracer = current.getTracer(name)

    /**
     * Initialise / re-initialise the tracer with the supplied
     * mode + (optionally) OTLP endpoint + headers. Safe to call
     * from any thread.
     */
    fun reconfigure(
        mode: TracingMode,
        otlpEndpoint: String? = null,
        otlpHeaders: Map<String, String> = emptyMap(),
    ) {
        this.mode = mode
        current = when (mode) {
            TracingMode.Off -> OpenTelemetry.noop()
            TracingMode.Local -> OtelBootstrap.local(sink)
            TracingMode.Otel -> {
                if (otlpEndpoint.isNullOrBlank()) {
                    OtelBootstrap.local(sink)
                } else {
                    OtelBootstrap.otlp(otlpEndpoint, otlpHeaders, sink)
                }
            }
        }
    }
}

/** Tracing mode — mirrors `com.meshlit.settings.TracingMode`. */
enum class TracingMode { Off, Local, Otel }

/**
 * Where spans land. The default no-op sink discards everything.
 * The app wires a real sink that forwards to [LogBuffer] + SLF4J
 * + (in Otel mode) the OTel exporter.
 */
interface TraceSink {
    fun onSpan(name: String, attributes: Map<String, String>)
}

object NoopSink : TraceSink {
    override fun onSpan(name: String, attributes: Map<String, String>) = Unit
}
