package com.meshlit.core.inference.net

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.InferenceRequest
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Embedded HTTP/SSE inference server. Lives in `:core-inference` and
 * is started by the foreground service in `onCreate`, stopped in
 * `onDestroy`.
 *
 * We use NanoHTTPD instead of Ktor 3 because Ktor 3.x emits bytecode
 * that requires DEX 040 (default from API 33). The user-mandated
 * `minSdk = 23` floor needs DEX 039-friendly code; NanoHTTPD is
 * pure-Java and works on Android 6+.
 *
 * Endpoints (same shape as the Ktor version that lived here before
 * the Phase 2 merge):
 *  - `GET  /v1/health`  → JSON snapshot of engine + port.
 *  - `GET  /v1/model`   → JSON snapshot of the loaded model.
 *  - `POST /v1/infer`   → `text/event-stream` SSE reply. One
 *    `event: token` per generated token, then `event: done` or
 *    `event: error`.
 *
 * Routing:
 *  - Before serving locally, the server calls [router.decideFor].
 *    If the router says FORWARD, the request is proxied via
 *    [forwarder] to a peer; the original caller cannot tell the
 *    difference.
 *
 * Concurrency:
 *  - NanoHTTPD spawns one thread per request. SSE output is driven
 *    from that thread via a piped `PipedInputStream` returned to the
 *    client; the inference engine callback writes events into the
 *    pipe.
 *  - The coordinator's `infer()` is already serialized through its
 *    internal mutex, so concurrent /v1/infer requests queue.
 */
class InferenceHttpServer(
    private val coordinator: InferenceCoordinator,
    private val router: RouterRef,
    private val forwarder: Forwarder,
    private val enricher: HealthEnricher = HealthEnricher.NONE,
    private val lifecycle: JobLifecycle = JobLifecycle.NOOP,
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
) {

    private val log = logger("InferenceHttpServer")

    @Volatile private var delegate: Delegate? = null

    val boundPort: Int get() = delegate?.listeningPort?.let { if (it > 0) it else port } ?: port

    /**
     * Start the server. Suspends until [stop] is called or the
     * coroutine is cancelled. The actual NanoHTTPD instance is
     * created on a worker thread.
     */
    fun start() {
        if (delegate != null) {
            log.warn("http.start.duplicate", "start() called twice without stop()")
            return
        }
        val d = Delegate()
        delegate = d
        log.info("http.start", "NanoHTTPD inference server starting", mapOf("host" to host, "port" to port))
        d.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
    }

    /**
     * Stop the server. Safe to call from any thread.
     */
    fun stop() {
        val d = delegate ?: return
        delegate = null
        try {
            d.stop()
            log.info("http.stop", "NanoHTTPD inference server stopped", mapOf("port" to port))
        } catch (t: Throwable) {
            log.warn("http.stop.exception", "stop threw", mapOf("err" to t.message.orEmpty()))
        }
    }

    /**
     * Inner NanoHTTPD subclass that wires the three routes. Lives as
     * an inner class so we can call back into [coordinator] / [router]
     * / [forwarder] directly.
     */
    private inner class Delegate : NanoHTTPD(host, port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimEnd('/').ifBlank { "/" }
            return try {
                when {
                    session.method == Method.GET && uri.endsWith("/v1/health") -> handleHealth()
                    session.method == Method.GET && uri.endsWith("/v1/model") -> handleModel()
                    session.method == Method.POST && uri.endsWith("/v1/infer") -> handleInfer(session)
                    else -> newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "not found",
                    )
                }
            } catch (t: Throwable) {
                log.error("http.unhandled", "unhandled serve() exception", t)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "internal: ${t.message ?: ""}",
                )
            }
        }

        private fun handleHealth(): Response {
            // The enricher is consulted once per reply. It's cheap
            // (just reads from in-memory flows) and we don't want a
            // slow enricher to block /v1/health.
            val snap = try {
                enricher.snapshot()
            } catch (t: Throwable) {
                log.warn("http.health.enricher.fail", "HealthEnricher threw; replying without enrichment", mapOf("err" to (t.message ?: "")))
                HealthEnricher.HealthSnapshot()
            }
            val resp = HealthResponse(
                status = "ok",
                engine = coordinator.engineTag,
                port = boundPort,
                capabilityTier = snap.capabilityTier,
                engineTag = snap.engineTag ?: coordinator.engineTag,
                loadedShards = snap.loadedShards,
                metrics = snap.metrics,
            )
            val body = json.encodeToString(HealthResponse.serializer(), resp)
            return newFixedLengthResponse(Response.Status.OK, "application/json", body)
        }

        private fun handleModel(): Response {
            val info = coordinator.loadedModel()
            val resp = if (info == null) {
                ModelStateResponse(loaded = false)
            } else {
                // Surface the layer range so the planner can pick
                // sharded assignments without an out-of-band query.
                // For a whole-model load the range collapses to
                // [0, totalLayers) once the manifest exists.
                val ranges = if (info.layerEnd != Int.MAX_VALUE && info.layerStart >= 0) {
                    listOf(ShardRange(layerStart = info.layerStart, layerEnd = info.layerEnd))
                } else emptyList()
                ModelStateResponse(
                    loaded = true,
                    name = info.modelName,
                    contextSize = info.contextSize,
                    parameterCount = info.parameterCount,
                    quantization = info.quantization,
                    shardRanges = ranges,
                )
            }
            val body = json.encodeToString(ModelStateResponse.serializer(), resp)
            return newFixedLengthResponse(Response.Status.OK, "application/json", body)
        }

        private fun handleInfer(session: IHTTPSession): Response {
            val bodyText = readBody(session)
            val req = try {
                json.decodeFromString(InferRequest.serializer(), bodyText)
            } catch (t: Throwable) {
                log.warn("http.infer.bad_request", "bad /v1/infer body", mapOf("err" to (t.message ?: "")))
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    """{"error":"${(t.message ?: "bad request").replace("\"", "\\\"")}"}""",
                )
            }
            val hints = RequestHints.parse(session.headers["x-meshlit-hints"])
            val decision = try {
                // decideFor is a suspend function; serve() is not. The
                // inference loop is already serialized through the
                // coordinator's mutex, so blocking this NanoHTTPD
                // worker thread is safe.
                runBlocking { router.decideFor(req, hints) }
            } catch (t: Throwable) {
                log.warn("http.router.fail", "router threw; falling back to local", mapOf("err" to (t.message ?: "")))
                RouterDecision.local(reason = "router-error-fallback")
            }
            return streamInferResponse(req, hints, decision)
        }

        /**
         * Build a chunked SSE response backed by a piped stream. The
         * inference engine callback writes `event: token` / `event:
         * done` / `event: error` lines into the pipe; NanoHTTPD
         * pushes them to the client as they arrive.
         *
         * The router decision is honored by switching the
         * coordinator path to the [forwarder] when FORWARD was
         * chosen.
         */
        private fun streamInferResponse(
            req: InferRequest,
            hints: RequestHints?,
            decision: RouterDecision,
        ): Response {
            val peer: String? = when (decision.where) {
                RouterDecision.Where.LOCAL -> null
                RouterDecision.Where.FORWARD -> decision.peerBaseUrl?.takeIf { it.isNotBlank() }
            }
            val pipedIn = PipedInputStream(64 * 1024)
            val pipedOut = PipedOutputStream(pipedIn)
            // Lifecycle hooks bookend the inference job so the
            // MetricsRegistry / MetricsScreen see accurate timings.
            val jobToken = lifecycle.start()
            // We write to pipedOut from a worker thread; NanoHTTPD
            // reads from pipedIn as the chunked response body. When
            // the writer closes pipedOut, NanoHTTPD sees EOF and
            // closes the connection cleanly.
            Thread({
                val started = System.currentTimeMillis()
                var outcome: JobLifecycle.Outcome = JobLifecycle.Outcome.Failure("no_tokens", "infer produced no events")
                try {
                    outcome = if (peer == null) {
                        runLocal(req, pipedOut)
                    } else {
                        runForward(peer, req, hints, pipedOut)
                    }
                    pipedOut.close()
                    log.info(
                        "http.infer.done",
                        "/v1/infer completed",
                        mapOf(
                            "peer" to (peer ?: "local"),
                            "ms" to (System.currentTimeMillis() - started),
                            "outcome" to outcome::class.simpleName.orEmpty(),
                        ),
                    )
                } catch (t: Throwable) {
                    log.warn("http.infer.stream.exception", "SSE writer threw", mapOf("err" to (t.message ?: "")))
                    outcome = JobLifecycle.Outcome.Failure("sse_writer_exception", t.message ?: "")
                    try { pipedOut.close() } catch (_: Throwable) { /* swallow */ }
                    try { pipedIn.close() } catch (_: Throwable) { /* swallow */ }
                } finally {
                    try { lifecycle.end(jobToken, outcome) } catch (_: Throwable) { /* swallow */ }
                }
            }, "meshlit-infer-sse").apply { isDaemon = true; start() }
            val response = newChunkedResponse(Response.Status.OK, "text/event-stream", pipedIn)
            response.addHeader("X-Accel-Buffering", "no")
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader(HEADER_FORWARDED, (peer != null).toString())
            response.addHeader(HEADER_ROUTER_REASON, decision.reason)
            return response
        }

        /**
         * Run the inference locally and return the final
         * [JobLifecycle.Outcome] so the HTTP caller can record it.
         */
        private fun runLocal(req: InferRequest, sink: java.io.OutputStream): JobLifecycle.Outcome {
            // runBlocking because the inference loop is a suspend
            // function but NanoHTTPD's serve() is plain. The SSE
            // writer thread is dedicated; blocking it is fine.
            return runBlocking {
                val result = coordinator.infer(
                    InferenceRequest(
                        prompt = req.prompt,
                        maxTokens = req.maxTokens,
                        temperature = req.temperature,
                        topP = req.topP,
                        topK = req.topK,
                        repeatPenalty = req.repeatPenalty,
                        stopSequences = req.stopSequences,
                        seed = req.seed,
                        onToken = { token -> emitEvent(sink, SseEvents.TOKEN, InferTokenEvent(text = token)) },
                    ),
                )
                sink.flush()
                when (result) {
                    is MeshlitResult.Success -> {
                        emitEvent(
                            sink,
                            SseEvents.DONE,
                            InferDoneEvent(
                                finishReason = result.value.finishReason.tag,
                                generatedTokens = result.value.generatedTokens,
                                totalDurationMs = result.value.totalDurationMs,
                                tokensPerSecond = result.value.tokensPerSecond,
                            ),
                        )
                        JobLifecycle.Outcome.Success(
                            generatedTokens = result.value.generatedTokens,
                            tokensPerSecond = result.value.tokensPerSecond,
                        )
                    }
                    is MeshlitResult.Failure -> {
                        emitEvent(
                            sink,
                            SseEvents.ERROR,
                            InferErrorEvent(tag = result.error.tag, message = result.error.message ?: ""),
                        )
                        JobLifecycle.Outcome.Failure(
                            tag = result.error.tag,
                            message = result.error.message ?: "",
                        )
                    }
                }
            }
        }

        private fun runForward(
            peer: String,
            req: InferRequest,
            hints: RequestHints?,
            sink: java.io.OutputStream,
        ): JobLifecycle.Outcome {
            return runBlocking {
                var capturedTokens = 0
                var lastTps = 0f
                var capturedTag: String? = null
                var capturedMsg: String? = null
                forwarder.forwardAndStream(
                    peerBaseUrl = peer,
                    request = req,
                    hints = hints,
                    onToken = { ev -> emitEvent(sink, SseEvents.TOKEN, ev) },
                    onDone = { ev ->
                        capturedTokens = ev.generatedTokens
                        lastTps = ev.tokensPerSecond
                        emitEvent(sink, SseEvents.DONE, ev)
                    },
                    onError = { ev ->
                        capturedTag = ev.tag
                        capturedMsg = ev.message
                        emitEvent(sink, SseEvents.ERROR, ev)
                    },
                )
                sink.flush()
                if (capturedTag != null) {
                    JobLifecycle.Outcome.Failure(capturedTag!!, capturedMsg ?: "")
                } else {
                    JobLifecycle.Outcome.Success(capturedTokens, lastTps)
                }
            }
        }

        private fun emitEvent(sink: java.io.OutputStream, name: String, payload: Any) {
            val jsonBody = when (payload) {
                is InferTokenEvent -> Json.encodeToString(InferTokenEvent.serializer(), payload)
                is InferDoneEvent -> Json.encodeToString(InferDoneEvent.serializer(), payload)
                is InferErrorEvent -> Json.encodeToString(InferErrorEvent.serializer(), payload)
                else -> error("no serializer for ${payload::class}")
            }
            val line = "event: $name\ndata: $jsonBody\n\n"
            synchronized(sink) {
                sink.write(line.toByteArray(Charsets.UTF_8))
                sink.flush()
            }
        }

        private fun readBody(session: IHTTPSession): String {
            val files = mutableMapOf<String, String>()
            try {
                session.parseBody(files)
            } catch (t: Throwable) {
                log.warn("http.infer.parse_body", "parseBody failed", mapOf("err" to (t.message ?: "")))
            }
            // POST JSON comes through as a "postData" parameter when
            // the Content-Length is set and the body is small enough.
            val direct = session.parameters["postData"]?.firstOrNull()
            if (direct != null) return direct
            // Fallback: read from input stream.
            return try {
                session.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (t: Throwable) {
                ""
            }
        }
    }

    companion object {
        /** Default port for the embedded server. */
        const val DEFAULT_PORT = 8080

        /** Bind address. 0.0.0.0 = reachable from the LAN; 127.0.0.1 = loopback only. */
        const val DEFAULT_HOST = "0.0.0.0"

        /** Header names. Exposed for clients. */
        const val HEADER_FORWARDED = "X-Meshlit-Forwarded"
        const val HEADER_ROUTER_REASON = "X-Meshlit-Router-Reason"
        const val HEADER_HINTS = "X-Meshlit-Hints"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}