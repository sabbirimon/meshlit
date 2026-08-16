package com.meshlit.core.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.time.Duration

/**
 * Builds an [OpenTelemetrySdk] for one of two modes:
 *   - [local] — spans go to the in-process logging exporter +
 *     the user-supplied [TraceSink] (typically forwards to
 *     [com.meshlit.observability.LogBuffer]).
 *   - [otlp] — same plus a [BatchSpanProcessor] pointed at the
 *     user's OTLP/gRPC endpoint URL.
 *
 * The SDK is created lazily and held in a static singleton so
 * the rest of the app can reach it via [TracingController.tracer].
 */
object OtelBootstrap {

    private const val SERVICE_NAME = "com.meshlit"

    /**
     * Build a "Local" SDK. Spans are exported to the logging
     * appender (logcat) + the user-supplied [sink] so the in-app
     * log buffer can show them.
     */
    fun local(sink: TraceSink): OpenTelemetry {
        val resource = Resource.getDefault().merge(
            Resource.create(Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"), SERVICE_NAME)),
        )
        val provider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
            .addSpanProcessor(SinkSpanProcessor(sink))
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(provider)
            .build()
    }

    /**
     * Build an OTLP SDK. [endpoint] is the gRPC URL
     * (e.g. `https://otlp-gateway-us-east-0.grafana.cloud:443`).
     * [headers] are key/value pairs typically carrying
     * `Authorization=Basic <base64>`.
     */
    fun otlp(
        endpoint: String,
        headers: Map<String, String>,
        sink: TraceSink,
    ): OpenTelemetry {
        val exporterBuilder = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(Duration.ofSeconds(10))
        headers.forEach { (k, v) -> exporterBuilder.addHeader(k, v) }
        val resource = Resource.getDefault().merge(
            Resource.create(Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"), SERVICE_NAME)),
        )
        val provider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(
                BatchSpanProcessor.builder(exporterBuilder.build())
                    .setMaxQueueSize(2048)
                    .build(),
            )
            .addSpanProcessor(SinkSpanProcessor(sink))
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(provider)
            .build()
    }
}