package com.meshlit.core.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TracingController(private val sink:TraceSink=NoopSink, private val instanceId:String=java.util.UUID.randomUUID().toString()) {
    @Volatile private var current:OpenTelemetry=OpenTelemetry.noop()
    @Volatile private var mode=TracingMode.Off
    private val _status=MutableStateFlow("Off")
    val status=_status.asStateFlow()
    private val successes=AtomicLong(); private val failures=AtomicLong()
    fun mode()=mode
    fun tracer(name:String):Tracer=current.getTracer(name)
    @Synchronized fun reconfigure(mode:TracingMode,otlpEndpoint:String?=null,otlpHeaders:Map<String,String> = emptyMap()) {
        val next=if(mode==TracingMode.Off) OpenTelemetry.noop() else OtelBootstrap.create(sink,
            otlpEndpoint?.takeIf { mode==TracingMode.Otel && it.isNotBlank() },otlpHeaders,instanceId) { ok->
            if(ok)successes.incrementAndGet() else failures.incrementAndGet()
            if(this.mode==TracingMode.Otel)_status.value="Trace batches acknowledged: ${successes.get()} · failed: ${failures.get()}"
        }
        val previous=current
        current=next;this.mode=mode
        _status.value=when(mode){TracingMode.Off->"Off";TracingMode.Local->"Local only";TracingMode.Otel->if(otlpEndpoint.isNullOrBlank())"No collector configured; local only" else "Collector configured; awaiting trace acknowledgment"}
        (previous as? OpenTelemetrySdk)?.close()
    }
    fun record(record:AuditRecord) {
        val r=record.safe(); val a=Attributes.builder().put("audit.source",r.source.name).put("audit.actor",r.actor.name).put("audit.outcome",r.outcome.name)
        r.targetHash?.let { a.put("audit.target_hash",it) };r.measurements.forEach { (k,v)->a.put(k,v) }
        val duration=r.measurements["duration_ms"]?.toLong()?.coerceIn(0,86_400_000) ?: 0
        val end=r.timeMs;val span=tracer("com.meshlit.audit").spanBuilder(r.action).setAllAttributes(a.build()).setStartTimestamp(end-duration,TimeUnit.MILLISECONDS).startSpan()
        if(r.outcome in setOf(AuditOutcome.FAILED,AuditOutcome.DENIED)) span.setStatus(StatusCode.ERROR)
        span.end(end,TimeUnit.MILLISECONDS)
        val meter=current.getMeter("com.meshlit.audit")
        // Low-cardinality labels: never IDs or user-defined names in metrics.
        val labels=Attributes.builder().put("source",r.source.name).put("outcome",r.outcome.name).build()
        meter.counterBuilder("meshlit.audit.events").build().add(1,labels)
        r.measurements["duration_ms"]?.takeIf { r.outcome!=AuditOutcome.STARTED && it>=0 }?.let { meter.histogramBuilder("meshlit.operation.duration").setUnit("ms").build().record(it,labels) }
        if(r.source==AuditSource.DEVICE)r.measurements.forEach { (k,v)->meter.gaugeBuilder("meshlit.device.$k").build().set(v) }
    }
    suspend fun flush():Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val sdk=current as? OpenTelemetrySdk ?: return@withContext true
        val trace=sdk.sdkTracerProvider.forceFlush(); val metrics=sdk.sdkMeterProvider.forceFlush()
        trace.join(12,TimeUnit.SECONDS);metrics.join(12,TimeUnit.SECONDS)
        trace.isSuccess && metrics.isSuccess
    }
}
enum class TracingMode { Off, Local, Otel }
interface TraceSink { fun onSpan(name:String,attributes:Map<String,String>) }
object NoopSink:TraceSink { override fun onSpan(name:String,attributes:Map<String,String>)=Unit }
