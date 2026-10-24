package com.meshlit.core.observability

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import java.time.Duration

/** OTLP/HTTP base URL, e.g. https://collector.example/otlp. No vendor endpoint defaults. */
object OtelBootstrap {
    fun create(sink:TraceSink, endpoint:String?,headers:Map<String,String>,instanceId:String,status:(Boolean)->Unit):OpenTelemetrySdk {
        val resource=Resource.create(Attributes.builder().put("service.name","meshlit").put("service.instance.id",instanceId).build())
        val trace=SdkTracerProvider.builder().setResource(resource).addSpanProcessor(SinkSpanProcessor(sink))
        val meter=SdkMeterProvider.builder().setResource(resource)
        if(endpoint!=null) {
            val base=TelemetryPrivacy.endpoint(endpoint)
            val tb=OtlpHttpSpanExporter.builder().setEndpoint("$base/v1/traces").setTimeout(Duration.ofSeconds(10))
            val mb=OtlpHttpMetricExporter.builder().setEndpoint("$base/v1/metrics").setTimeout(Duration.ofSeconds(10))
            headers.forEach { (k,v)->tb.addHeader(k,v);mb.addHeader(k,v) }
            trace.addSpanProcessor(BatchSpanProcessor.builder(PrivateSpanExporter(tb.build(),status)).setMaxQueueSize(512).setMaxExportBatchSize(128).setScheduleDelay(Duration.ofSeconds(5)).build())
            meter.registerMetricReader(PeriodicMetricReader.builder(mb.build()).setInterval(Duration.ofSeconds(30)).build())
        }
        return OpenTelemetrySdk.builder().setTracerProvider(trace.build()).setMeterProvider(meter.build()).build()
    }
}
