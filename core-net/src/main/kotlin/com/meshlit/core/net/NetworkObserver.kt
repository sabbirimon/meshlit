package com.meshlit.core.net

import com.meshlit.core.observability.LogSource
import com.meshlit.core.observability.TracerHolder
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * OkHttp [EventListener] wrapper that records every Meshlit HTTP
 * call to:
 *
 *   1. The in-process ring buffer ([entries]) — read by the
 *      Network Monitor screen.
 *   2. The OpenTelemetry tracer — a span per call + per phase
 *      (DNS / connect / TLS / responseHeaders / responseBody).
 *   3. The shared [com.meshlit.observability.LogBuffer] under the
 *      [LogSource.NETWORK] tag — so the existing LogScreen shows
 *      them too.
 *
 * Threading: the listener callbacks all fire on the OkHttp
 * dispatcher thread; the [entries] StateFlow serialises reads so
 * the UI sees a consistent snapshot.
 */
class NetworkObserver(
    private val logSink: NetworkLogSink = NoopNetworkLogSink,
    private val enabled: () -> Boolean = { true },
) : EventListener() {

    /**
     * One per request. The mutable state ([status], [durationMs])
     * is carried via a [WeakHashMap] keyed by [Call] because OkHttp
     * does not otherwise let us carry per-call state through the
     * listener callbacks.
     */
    data class Entry(
        val id: Long,
        val timestampMs: Long,
        val method: String,
        val url: String,
        val host: String,
        val status: Int = 0,
        val durationMs: Long = 0,
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
        val errorMessage: String? = null,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val nextId = AtomicLong(1)

    // Per-call mutable state. The keys are weakly held; once OkHttp
    // drops the strong reference the entry is GC'd. Synchronised
    // because EventListener callbacks may run on different threads
    // for different calls in the same dispatcher.
    private val callState: MutableMap<Call, CallState> =
        java.util.Collections.synchronizedMap(WeakHashMap())
    private val callSpans: MutableMap<Call, Span> =
        java.util.Collections.synchronizedMap(WeakHashMap())

    override fun callStart(call: Call) {
        if (!enabled()) return
        val url: HttpUrl = call.request().url
        val id = nextId.getAndIncrement()
        val st = CallState(id, call.request().method, url.toString())
        callState[call] = st

        val span: Span = TracerHolder.controller().tracer("com.meshlit.net")
            .spanBuilder("http.client")
            .setAttribute(AttributeKey.stringKey("http.method"), call.request().method)
            .setAttribute(AttributeKey.stringKey("url.full"), url.toString())
            .setAttribute(AttributeKey.stringKey("server.address"), url.host)
            .startSpan()
        callSpans[call] = span

        logSink.onNetwork(
            LogSource.NETWORK,
            "http",
            "→ ${call.request().method} ${url}",
            mapOf("id" to id),
        )
    }

    override fun dnsStart(call: Call, domainName: String) {
        startPhase(call, "dns") { it.setAttribute(AttributeKey.stringKey("dns.name"), domainName) }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        endPhase(call, "dns") {
            it.setAttribute(AttributeKey.stringKey("dns.resolved"), inetAddressList.joinToString(",") { a -> a.hostAddress ?: "" })
        }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        startPhase(call, "connect") {
            it.setAttribute(AttributeKey.stringKey("net.peer"), inetSocketAddress.toString())
        }
    }

    override fun secureConnectStart(call: Call) {
        startPhase(call, "tls")
    }

    override fun responseHeadersStart(call: Call) {
        startPhase(call, "response-headers")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val st = callState[call] ?: return
        st.status = response.code
        st.bytesIn = response.body?.contentLength() ?: -1L
        st.bytesOut = call.request().body?.contentLength() ?: -1L
        endPhase(call, "response") {
            it.setAttribute(AttributeKey.longKey("http.status_code"), response.code.toLong())
        }
    }

    override fun callEnd(call: Call) {
        val st = callState.remove(call) ?: return
        val span = callSpans.remove(call)
        st.durationMs = System.currentTimeMillis() - st.timestampMs
        finish(st, error = null, span = span)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val st = callState.remove(call) ?: return
        val span = callSpans.remove(call)
        st.durationMs = System.currentTimeMillis() - st.timestampMs
        st.errorMessage = ioe.message
        finish(st, error = ioe, span = span)
    }

    private fun finish(st: CallState, error: IOException?, span: Span?) {
        if (span != null) {
            if (error != null) {
                span.recordException(error)
                span.setAttribute(AttributeKey.longKey("error"), 1)
            }
            span.end()
        }
        val entry = Entry(
            id = st.id,
            timestampMs = st.timestampMs,
            method = st.method,
            url = st.url,
            host = runCatching { URI(st.url).host ?: "" }.getOrDefault(""),
            status = st.status,
            durationMs = st.durationMs,
            bytesIn = if (st.bytesIn < 0) 0 else st.bytesIn,
            bytesOut = if (st.bytesOut < 0) 0 else st.bytesOut,
            errorMessage = st.errorMessage,
        )
        _entries.update { current ->
            val next = ArrayList<Entry>(current.size + 1)
            next.addAll(current)
            next.add(entry)
            if (next.size > MAX_NETWORK_ENTRIES) {
                next.subList(next.size - MAX_NETWORK_ENTRIES, next.size).toList()
            } else next
        }
        if (error != null) {
            logSink.onNetwork(
                LogSource.NETWORK,
                "http",
                "✗ ${st.method} ${st.url} → ${error.message ?: "io error"}",
                mapOf("id" to st.id, "duration_ms" to st.durationMs),
            )
        } else {
            logSink.onNetwork(
                LogSource.NETWORK,
                "http",
                "✓ ${st.method} ${st.url} ${st.status} (${st.durationMs}ms)",
                mapOf(
                    "id" to st.id,
                    "status" to st.status,
                    "duration_ms" to st.durationMs,
                    "bytes_in" to entry.bytesIn,
                    "bytes_out" to entry.bytesOut,
                ),
            )
        }
    }

    private fun startPhase(call: Call, name: String, decorate: (Span) -> Unit = {}) {
        if (!enabled()) return
        val parent = callSpans[call] ?: return
        val span: Span = TracerHolder.controller().tracer("com.meshlit.net")
            .spanBuilder("http.$name")
            .setParent(io.opentelemetry.context.Context.current().with(parent))
            .startSpan()
        try {
            span.makeCurrent()
            decorate(span)
        } finally {
            // We don't end the span here — responseHeadersEnd / dnsEnd
            // end it (or callEnd does if the phase was the last).
        }
    }

    private fun endPhase(call: Call, name: String, decorate: (Span) -> Unit = {}) {
        val parent = callSpans[call] ?: return
        // Re-fetch the phase span by walking the active context —
        // the start was attached to the parent via setParent so the
        // active span on the current OTel context is the phase span.
        val current = io.opentelemetry.api.trace.Span.current()
        if (current === parent) return
        try {
            decorate(current)
        } finally {
            current.end()
        }
    }

    /** Wipe entries (used by the "Clear" button on the Network Monitor). */
    fun clear() {
        _entries.value = emptyList()
    }

    private class CallState(
        val id: Long,
        val method: String,
        val url: String,
    ) {
        val timestampMs: Long = System.currentTimeMillis()
        var status: Int = 0
        var durationMs: Long = 0
        var bytesIn: Long = 0
        var bytesOut: Long = 0
        var errorMessage: String? = null
    }

    companion object {
        const val MAX_NETWORK_ENTRIES = 1_000
    }
}

/**
 * Lightweight sink interface — decouples :core-net from the app
 * module's LogBuffer. The app provides an implementation that
 * forwards to [com.meshlit.observability.LogBuffer].
 */
interface NetworkLogSink {
    fun onNetwork(source: LogSource, tag: String, message: String, context: Map<String, Any?> = emptyMap())
}

object NoopNetworkLogSink : NetworkLogSink {
    override fun onNetwork(source: LogSource, tag: String, message: String, context: Map<String, Any?>) = Unit
}
