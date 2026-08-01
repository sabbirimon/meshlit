package com.meshlit.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.HealthResponse
import com.meshlit.core.inference.net.InferenceHttpServer
import com.meshlit.core.inference.net.InferDoneEvent
import com.meshlit.core.inference.net.InferErrorEvent
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.InferTokenEvent
import com.meshlit.core.inference.net.ModelStateResponse
import com.meshlit.core.inference.net.SseEvents
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Single-use HTTP/SSE client for talking to one peer. Construct via
 * [RemoteInferenceClientFactory.build]; the factory owns the shared
 * [HttpClient] so we don't open multiple engines.
 *
 * Each [streamInfer] call opens one HTTP request and reads the SSE
 * stream to completion. Errors are caught and returned as
 * [MeshlitResult.Failure] — no exceptions leak to callers, which is
 * the cluster contract: every node can vanish mid-call.
 *
 * Lifetime: a single [streamInfer] call. The [HttpClient] it borrows
 * from the factory is shared across all calls; closing happens on
 * factory shutdown (FGS `onDestroy`).
 */
class RemoteInferenceClient internal constructor(
    private val baseUrl: String,
    private val client: HttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("RemoteInferenceClient")

    /**
     * Hit `GET /v1/health`. Returns a [MeshlitResult] so the caller
     * can react without try/catch.
     */
    suspend fun health(): MeshlitResult<HealthResponse> = withContext(dispatcher) {
        runCall("health") {
            val resp: HttpResponse = client.get("$baseUrl/v1/health")
            json.decodeFromString(HealthResponse.serializer(), resp.bodyAsText())
        }
    }

    /**
     * Hit `GET /v1/model`. Returns a [MeshlitResult].
     */
    suspend fun modelState(): MeshlitResult<ModelStateResponse> = withContext(dispatcher) {
        runCall("modelState") {
            val resp: HttpResponse = client.get("$baseUrl/v1/model")
            json.decodeFromString(ModelStateResponse.serializer(), resp.bodyAsText())
        }
    }

    /**
     * Stream one inference. Opens a POST to `/v1/infer`, parses SSE
     * events as they arrive, and fires:
     *  - [onToken] per `event: token`
     *  - [onDone]  on `event: done`
     *  - [onError] on `event: error`
     *
     * If [hints] is non-null, the `X-Meshlit-Hints` header is set so
     * the server-side router can decide whether to serve locally or
     * forward to another peer.
     *
     * Implementation note: Ktor 3 + kotlinx-io makes per-byte SSE
     * parsing awkward without internal APIs. For Phase 1 we read the
     * whole body as text and parse the well-formed SSE block; the
     * server flushes per event so a long-running inference still
     * looks like "tokens arrived" to the user, just delayed by the
     * final HTTP close. Phase 2 swaps in a streaming reader.
     */
    suspend fun streamInfer(
        request: InferRequest,
        hints: RequestHints? = null,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ): MeshlitResult<Unit> = withContext(dispatcher) {
        try {
            val response: HttpResponse = client.post("$baseUrl/v1/infer") {
                contentType(ContentType.Application.Json)
                setBody(request)
                hints?.let { h ->
                    headers {
                        append(InferenceHttpServer.HEADER_HINTS, h.toHeaderValue())
                    }
                }
            }
            if (response.status != HttpStatusCode.OK) {
                val tag = "peer.status.${response.status.value}"
                log.warn("streamInfer.bad_status", "non-OK status", mapOf("status" to response.status.value))
                return@withContext MeshlitResult.Failure(MeshlitError.Network(tag))
            }
            val body = response.bodyAsText()
            parseSse(body, onToken, onDone, onError)
            MeshlitResult.Success(Unit)
        } catch (ce: CancellationException) {
            throw ce  // cooperative cancellation
        } catch (t: HttpRequestTimeoutException) {
            log.warn("streamInfer.timeout", "peer request timed out", mapOf("err" to (t.message ?: "")))
            MeshlitResult.Failure(MeshlitError.Network("peer.timeout"))
        } catch (t: Throwable) {
            log.warn("streamInfer.exception", "peer request threw", mapOf("err" to (t.message ?: "")))
            MeshlitResult.Failure(MeshlitError.Network("peer.exception", t))
        }
    }

    /**
     * SSE parser. Walks the [body] line by line. SSE events come as:
     *
     *     event: <name>\n
     *     data: <json>\n
     *     \n
     *
     * We accumulate per-event fields, then dispatch when we see the
     * blank line.
     */
    private suspend fun parseSse(
        body: String,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ) {
        var pendingEvent: String? = null
        val pendingData = StringBuilder()
        for (rawLine in body.split(Regex("\r\n|\n|\r"))) {
            val line = rawLine
            when {
                line.isEmpty() -> {
                    val name = pendingEvent
                    val data = pendingData.toString()
                    if (name != null && data.isNotEmpty()) {
                        dispatch(name, data, onToken, onDone, onError)
                    }
                    pendingEvent = null
                    pendingData.clear()
                }
                line.startsWith("event:") -> {
                    pendingEvent = line.substringAfter("event:").trim()
                }
                line.startsWith("data:") -> {
                    val piece = line.substringAfter("data:")
                    if (pendingData.isNotEmpty()) pendingData.append('\n')
                    pendingData.append(piece.trimStart())
                }
                line.startsWith(":") -> {
                    // SSE comment line; ignore.
                }
                // Anything else (e.g. id:, retry:) is irrelevant for
                // our wire.
            }
        }
        // Flush any trailing event without a terminator.
        val name = pendingEvent
        val data = pendingData.toString()
        if (name != null && data.isNotEmpty()) {
            dispatch(name, data, onToken, onDone, onError)
        }
    }

    private suspend fun dispatch(
        name: String,
        data: String,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ) {
        try {
            when (name) {
                SseEvents.TOKEN -> onToken(json.decodeFromString(InferTokenEvent.serializer(), data))
                SseEvents.DONE -> onDone(json.decodeFromString(InferDoneEvent.serializer(), data))
                SseEvents.ERROR -> onError(json.decodeFromString(InferErrorEvent.serializer(), data))
                // Unknown event: log and ignore.
                else -> log.info("sse.unknown", "unknown event name", mapOf("name" to name))
            }
        } catch (t: Throwable) {
            log.warn("sse.decode.fail", "failed to decode SSE event", mapOf("name" to name, "err" to (t.message ?: "")))
        }
    }

    /**
     * Convenience helper to run a typed GET, returning a typed
     * MeshlitResult.
     */
    private suspend inline fun <T> runCall(
        label: String,
        block: () -> T,
    ): MeshlitResult<T> {
        try {
            return MeshlitResult.Success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: HttpRequestTimeoutException) {
            log.warn("$label.timeout", "peer request timed out", mapOf("err" to (t.message ?: "")))
            return MeshlitResult.Failure(MeshlitError.Network("peer.$label.timeout"))
        } catch (t: Throwable) {
            log.warn("$label.exception", "peer request threw", mapOf("err" to (t.message ?: "")))
            return MeshlitResult.Failure(MeshlitError.Network("peer.$label.exception", t))
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}