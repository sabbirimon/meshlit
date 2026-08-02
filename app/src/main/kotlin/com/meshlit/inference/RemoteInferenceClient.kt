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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Single-use HTTP/SSE client for talking to one peer. Construct via
 * [RemoteInferenceClientFactory.build]; the factory owns the shared
 * [OkHttpClient] so we don't open multiple engines.
 *
 * We use OkHttp directly (not Ktor client) because Ktor 3's bytecode
 * requires DEX 040 (default from API 33) which would break the
 * user-mandated `minSdk = 23` floor. OkHttp is pure-Java and dexes
 * everywhere.
 *
 * Each [streamInfer] call opens one HTTP request and reads the SSE
 * stream to completion. Errors are caught and returned as
 * [MeshlitResult.Failure] — no exceptions leak to callers.
 *
 * SSE parsing: we use a true line-streaming reader that fires
 * callbacks as soon as a complete event is parsed, instead of
 * buffering the whole body.
 */
class RemoteInferenceClient internal constructor(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("RemoteInferenceClient")

    suspend fun health(): MeshlitResult<HealthResponse> = withContext(dispatcher) {
        runCall("health") {
            val req = Request.Builder()
                .url("$baseUrl/v1/health")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.requireOk("health")
                json.decodeFromString(HealthResponse.serializer(), resp.body!!.string())
            }
        }
    }

    suspend fun modelState(): MeshlitResult<ModelStateResponse> = withContext(dispatcher) {
        runCall("modelState") {
            val req = Request.Builder()
                .url("$baseUrl/v1/model")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.requireOk("modelState")
                json.decodeFromString(ModelStateResponse.serializer(), resp.body!!.string())
            }
        }
    }

    suspend fun streamInfer(
        request: InferRequest,
        hints: RequestHints? = null,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ): MeshlitResult<Unit> = withContext(dispatcher) {
        try {
            val body = json.encodeToString(InferRequest.serializer(), request)
                .toRequestBody(JSON_MEDIA)
            val reqBuilder = Request.Builder()
                .url("$baseUrl/v1/infer")
                .post(body)
            hints?.let { reqBuilder.header(InferenceHttpServer.HEADER_HINTS, it.toHeaderValue()) }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val tag = "peer.status.${resp.code}"
                    log.warn("streamInfer.bad_status", "non-OK status", mapOf("status" to resp.code))
                    return@withContext MeshlitResult.Failure(MeshlitError.Network(tag))
                }
                val source = resp.body?.source()
                if (source == null) {
                    log.warn("streamInfer.empty_body", "peer returned empty body")
                    return@withContext MeshlitResult.Failure(MeshlitError.Network("peer.empty_body"))
                }
                parseSse(source, onToken, onDone, onError)
                MeshlitResult.Success(Unit)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: java.net.SocketTimeoutException) {
            log.warn("streamInfer.timeout", "peer request timed out", mapOf("err" to (t.message ?: "")))
            MeshlitResult.Failure(MeshlitError.Network("peer.timeout"))
        } catch (t: Throwable) {
            log.warn("streamInfer.exception", "peer request threw", mapOf("err" to (t.message ?: "")))
            MeshlitResult.Failure(MeshlitError.Network("peer.exception", t))
        }
    }

    private suspend fun parseSse(
        source: okio.BufferedSource,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ) {
        var pendingEvent: String? = null
        val pendingData = StringBuilder()
        // OkHttp's BufferedSource exposes readUtf8Line which respects
        // both \n and \r\n — perfect for SSE.
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
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
                line.startsWith(":") -> { /* SSE comment */ }
            }
        }
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
                else -> log.info("sse.unknown", "unknown event name", mapOf("name" to name))
            }
        } catch (t: Throwable) {
            log.warn("sse.decode.fail", "failed to decode SSE event", mapOf("name" to name, "err" to (t.message ?: "")))
        }
    }

    private suspend inline fun <T> runCall(
        label: String,
        block: () -> T,
    ): MeshlitResult<T> {
        try {
            return MeshlitResult.Success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: java.net.SocketTimeoutException) {
            log.warn("$label.timeout", "peer request timed out", mapOf("err" to (t.message ?: "")))
            return MeshlitResult.Failure(MeshlitError.Network("peer.$label.timeout"))
        } catch (t: Throwable) {
            log.warn("$label.exception", "peer request threw", mapOf("err" to (t.message ?: "")))
            return MeshlitResult.Failure(MeshlitError.Network("peer.$label.exception", t))
        }
    }

    private fun Response.requireOk(label: String) {
        if (!isSuccessful) {
            val tag = "peer.$label.status.${code}"
            log.warn("$label.bad_status", "non-OK status", mapOf("status" to code))
            throw MeshlitError.Network(tag) as Throwable
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}