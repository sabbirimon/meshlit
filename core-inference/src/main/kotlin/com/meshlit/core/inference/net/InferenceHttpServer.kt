package com.meshlit.core.inference.net

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.InferenceRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Embedded HTTP/SSE server that exposes the local [InferenceCoordinator]
 * to peers on the LAN. Started by `InferenceForegroundService.onCreate`
 * and stopped in `onDestroy`.
 *
 * Endpoints:
 *  - `GET  /v1/health`  → JSON snapshot of engine + port.
 *  - `GET  /v1/model`   → JSON snapshot of the loaded model.
 *  - `POST /v1/infer`   → `text/event-stream` SSE reply. One
 *    `event: token` per generated token, then `event: done`.
 *
 * Routing:
 *  - Before serving locally, the server calls the injected
 *    [RouterRef.decideFor]. If the router says FORWARD, the request
 *    is proxied through the injected [Forwarder] to a peer; the
 *    original caller cannot tell the difference.
 *
 * Concurrency:
 *  - The Ktor engine runs on its own thread pool. The SSE stream for
 *    a single request is driven by the engine thread; tokens are
 *    pushed via a `Channel` from the inference engine callback.
 *  - The coordinator's `infer()` is already serialized through its
 *    internal mutex, so concurrent /v1/infer requests simply queue.
 *
 * Lifecycle:
 *  - [start] is `suspend` because Ktor 3's `embeddedServer` requires
 *    its `start(wait=true)` call to be inside a coroutine.
 *  - [stop] is non-suspending; it triggers the engine shutdown.
 *  - Calling [start] twice without [stop] in between is a programming
 *    error and will log a warning.
 */
class InferenceHttpServer(
    private val coordinator: InferenceCoordinator,
    private val router: RouterRef,
    private val forwarder: Forwarder,
    private val port: Int = DEFAULT_PORT,
    private val host: String = DEFAULT_HOST,
) {

    private val log = logger("InferenceHttpServer")

    @Volatile private var engine: io.ktor.server.engine.EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    val boundPort: Int get() = port

    /**
     * Start the server. Suspends until [stop] is called or the
     * coroutine is cancelled. Returns when the server is bound and
     * accepting connections.
     *
     * The launched engine is owned by the caller's scope — passing
     * [scope] ensures [stop] can target the right Job.
     */
    suspend fun start(scope: CoroutineScope) {
        if (engine != null) {
            log.warn("http.start.duplicate", "start() called twice without stop()")
            return
        }
        val srv = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            routing {
                get("/v1/health") {
                    val resp = HealthResponse(
                        status = "ok",
                        engine = coordinator.engineTag,
                        port = port,
                    )
                    call.respond(resp)
                }

                get("/v1/model") {
                    val info = coordinator.loadedModel()
                    if (info == null) {
                        call.respond(ModelStateResponse(loaded = false))
                    } else {
                        call.respond(
                            ModelStateResponse(
                                loaded = true,
                                name = info.modelName,
                                contextSize = info.contextSize,
                                parameterCount = info.parameterCount,
                                quantization = info.quantization,
                            ),
                        )
                    }
                }

                post("/v1/infer") {
                    val req = try {
                        call.receive<InferRequest>()
                    } catch (t: Throwable) {
                        log.warn("http.infer.bad_request", "bad /v1/infer body", mapOf("err" to t.message.orEmpty()))
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (t.message ?: "bad request")))
                        return@post
                    }

                    val hints = RequestHints.parse(call.request.headers["X-Meshlit-Hints"])
                    val decision = try {
                        router.decideFor(req, hints)
                    } catch (t: Throwable) {
                        log.warn("http.router.fail", "router threw; falling back to local", mapOf("err" to t.message.orEmpty()))
                        RouterDecision.local(reason = "router-error-fallback")
                    }

                    streamInfer(call, req, hints, decision)
                }
            }
        }
        engine = srv
        log.info("http.start", "embedded inference HTTP server bound", mapOf("host" to host, "port" to port))
        // Start non-blocking; the engine runs on its own thread pool.
        // We still suspend the caller's coroutine until stop().
        scope.launch {
            try {
                srv.start(wait = true)
            } catch (t: Throwable) {
                log.warn("http.start.exception", "engine start threw", mapOf("err" to t.message.orEmpty()))
            }
        }
    }

    /**
     * Stop the server. Safe to call from any thread. No-op when the
     * server isn't running.
     */
    fun stop() {
        val srv = engine ?: return
        engine = null
        try {
            // graceful: 500ms quiescence, 1000ms timeout total.
            srv.stop(500, 1000)
            log.info("http.stop", "embedded inference HTTP server stopped", mapOf("port" to port))
        } catch (t: Throwable) {
            log.warn("http.stop.exception", "stop threw", mapOf("err" to t.message.orEmpty()))
        }
    }

    /**
     * Run the actual inference pipeline for one /v1/infer request.
     *
     * Flow:
     *  1. If router says LOCAL → call `coordinator.infer`, push
     *     `event: token` per token, then `event: done`.
     *  2. If router says FORWARD → call `forwarder.forwardAndStream`,
     *     pipe tokens through to the original caller's SSE channel.
     *  3. On any error mid-stream, push `event: error` then close.
     *
     * Both branches share the same SSE response shape so callers
     * don't need to care which path served them.
     */
    private suspend fun streamInfer(
        call: io.ktor.server.application.ApplicationCall,
        req: InferRequest,
        hints: RequestHints?,
        decision: RouterDecision,
    ) {
        // Decide forwarding up front so we can set the headers before
        // starting the streaming body.
        val peer: String? = when (decision.where) {
            RouterDecision.Where.LOCAL -> null
            RouterDecision.Where.FORWARD -> decision.peerBaseUrl?.takeIf { it.isNotBlank() }
        }
        val forwarded = (peer != null).toString()
        call.response.header(HEADER_FORWARDED, forwarded)
        call.response.header(HEADER_ROUTER_REASON, decision.reason)
        call.response.header("X-Accel-Buffering", "no")
        call.response.header("Cache-Control", "no-cache")

        // respondTextWriter is the streaming responder: it must be
        // the last statement in the handler. Inside the lambda we
        // drive the SSE body directly via the underlying writer.
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            // `this` here is a Writer (java.io.Writer) per Ktor's
            // signature. We push events with explicit flushes so the
            // client sees tokens as they arrive.
            suspend fun emitEvent(name: String, payload: Any) {
                val json = when (payload) {
                    is InferTokenEvent -> Json.encodeToString(InferTokenEvent.serializer(), payload)
                    is InferDoneEvent -> Json.encodeToString(InferDoneEvent.serializer(), payload)
                    is InferErrorEvent -> Json.encodeToString(InferErrorEvent.serializer(), payload)
                    else -> error("no serializer for ${payload::class}")
                }
                write("event: $name\ndata: $json\n\n")
                flush()
            }

            if (peer == null) {
                val started = System.currentTimeMillis()
                var anyToken = false
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
                        onToken = { token ->
                            anyToken = true
                            emitEvent(SseEvents.TOKEN, InferTokenEvent(text = token))
                        },
                    ),
                )
                when (result) {
                    is MeshlitResult.Success -> {
                        emitEvent(
                            SseEvents.DONE,
                            InferDoneEvent(
                                finishReason = result.value.finishReason.tag,
                                generatedTokens = result.value.generatedTokens,
                                totalDurationMs = result.value.totalDurationMs,
                                tokensPerSecond = result.value.tokensPerSecond,
                            ),
                        )
                    }
                    is MeshlitResult.Failure -> {
                        emitEvent(
                            SseEvents.ERROR,
                            InferErrorEvent(tag = result.error.tag, message = result.error.message ?: ""),
                        )
                    }
                }
                flush()
                log.info(
                    "http.infer.local",
                    "/v1/infer local completed",
                    mapOf(
                        "anyToken" to anyToken,
                        "ms" to (System.currentTimeMillis() - started),
                    ),
                )
            } else {
                val started = System.currentTimeMillis()
                val outcome = forwarder.forwardAndStream(
                    peerBaseUrl = peer,
                    request = req,
                    hints = hints,
                    onToken = { ev -> emitEvent(SseEvents.TOKEN, ev) },
                    onDone = { ev -> emitEvent(SseEvents.DONE, ev) },
                    onError = { ev -> emitEvent(SseEvents.ERROR, ev) },
                )
                flush()
                if (outcome.isSuccess) {
                    log.info(
                        "http.infer.forward.ok",
                        "/v1/infer forward ok",
                        mapOf("peer" to peer, "ms" to (System.currentTimeMillis() - started)),
                    )
                } else {
                    log.warn(
                        "http.infer.forward.fail",
                        "/v1/infer forward failed",
                        mapOf(
                            "peer" to peer,
                            "ms" to (System.currentTimeMillis() - started),
                            "err" to (outcome.exceptionOrNull()?.message.orEmpty()),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /** Default port for the embedded server. Phase 1 hardcoded; Phase 2 picks from registry. */
        const val DEFAULT_PORT = 8080

        /** Bind address. 0.0.0.0 = reachable from the LAN; 127.0.0.1 = loopback only. */
        const val DEFAULT_HOST = "0.0.0.0"

        /** Header names. Exposed for clients. */
        const val HEADER_FORWARDED = "X-Meshlit-Forwarded"
        const val HEADER_ROUTER_REASON = "X-Meshlit-Router-Reason"
        const val HEADER_HINTS = "X-Meshlit-Hints"
    }
}