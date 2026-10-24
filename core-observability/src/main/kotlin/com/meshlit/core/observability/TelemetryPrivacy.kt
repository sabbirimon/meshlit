package com.meshlit.core.observability

import io.opentelemetry.api.common.*
import io.opentelemetry.sdk.trace.data.*
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import java.net.URI

/** Export boundary strips arbitrary attributes, exception events and status descriptions. */
object TelemetryPrivacy {
    fun endpoint(value:String):String {
        val u=URI(value.trim())
        require(u.scheme=="https" || (u.scheme=="http" && u.host in setOf("localhost","127.0.0.1","::1","[::1]"))) { "Use HTTPS; HTTP is only allowed on loopback" }
        require(!u.host.isNullOrBlank() && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null) { "Endpoint cannot contain credentials, query or fragment" }
        require(u.path.isNullOrEmpty() || u.path=="/" || !u.path.contains(".."))
        return value.trim().trimEnd('/')
    }
    fun span(data:SpanData):SpanData {
        val b=Attributes.builder()
        data.attributes.asMap().forEach { (k,v) ->
            if(k.key in AuditRecord.MEASUREMENTS || k.key in setOf("http.status_code","duration_ms","agent.tools","user.length","error")) {
                when(v) { is Long->b.put(k.key,v); is Double->if(v.isFinite())b.put(k.key,v) }
            }
            if(k.key in setOf("audit.source","audit.actor","audit.outcome") && v is String && v.matches(Regex("[A-Z_]{1,24}"))) b.put(k.key,v)
            if(k.key=="audit.target_hash" && v is String && v.matches(Regex("[a-f0-9]{64}"))) b.put(k.key,v)
        }
        val attrs=b.build()
        return object:SpanData by data {
            override fun getName()=data.name.takeIf { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,95}")) } ?: "event.redacted"
            override fun getAttributes()=attrs
            override fun getTotalAttributeCount()=attrs.size()
            override fun getEvents():List<EventData> = emptyList()
            override fun getTotalRecordedEvents()=0
            override fun getLinks():List<LinkData> = emptyList()
            override fun getTotalRecordedLinks()=0
            override fun getStatus()=StatusData.create(data.status.statusCode, "")
        }
    }
}
internal class PrivateSpanExporter(private val delegate:SpanExporter,private val status:(Boolean)->Unit):SpanExporter {
    override fun export(spans:Collection<SpanData>):CompletableResultCode = delegate.export(spans.map(TelemetryPrivacy::span)).also { r -> r.whenComplete { status(r.isSuccess) } }
    override fun flush()=delegate.flush()
    override fun shutdown()=delegate.shutdown()
}
