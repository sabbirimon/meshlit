package com.meshlit.core.federation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/**
 * Typed federation client. One instance per (peer node, OkHttp client)
 * tuple. The wire codec is JSON; every method either succeeds with a
 * typed result or surfaces a [FederationError].
 *
 * Threading: all suspend methods dispatch on [Dispatchers.IO] (OkHttp's
 * call dispatcher). The streaming methods return a cold [Flow] backed
 * by OkHttp's response body.
 *
 * Errors:
 *  - HTTP `4xx` / `5xx` responses are decoded as [FederationError]
 *    bodies and returned as failures.
 *  - Connection / timeout / TLS errors are mapped to
 *    [FederationError.internalError] with the underlying message.
 *
 * The client does **not** implement retries. Callers (the agent
 * kernel) decide retry policy per-endpoint. The retry contract lives
 * in `:core-agent-memory`.
 */
class FederationClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val localProtocolVersion: Int = FederationProtocol.PROTOCOL_VERSION,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = false
    }

    /**
     * `POST /v1/peer.hello`. Establishes a session with the peer. The
     * returned [PeerHelloResponse.sessionToken] MUST be passed to
     * [submitDispatch] / [submitHandoff] / [fetchCapabilityExchange]
     * via the `X-Meshlit-Session-Token` header.
     */
    suspend fun hello(
        baseUrl: String,
        request: PeerHelloRequest,
        extraHeaders: Map<String, String> = emptyMap(),
    ): FederationResult<PeerHelloResponse> = executeTyped(
        baseUrl = baseUrl,
        endpoint = FederationEndpoints.PEER_HELLO,
        body = json.encodeToString(request),
        extraHeaders = extraHeaders,
    )

    /** `POST /v1/health.capability` — lightweight probe. */
    suspend fun fetchCapabilityExchange(
        baseUrl: String,
        request: CapabilityExchange,
        sessionToken: String? = null,
    ): FederationResult<CapabilityExchange> = executeTyped(
        baseUrl = baseUrl,
        endpoint = FederationEndpoints.HEALTH_CAPABILITY,
        body = json.encodeToString(request),
        extraHeaders = sessionHeader(sessionToken),
    )

    /** `POST /v1/model.shardRanges`. v1 peers return
     *  [FederationError.unsupportedFeature] until Slice 4 lands. */
    suspend fun fetchShardLayout(
        baseUrl: String,
        modelId: String,
        sessionToken: String? = null,
    ): FederationResult<ModelShardLayout> {
        val body = json.encodeToString(ModelShardRangesRequest(modelId))
        return executeTyped(
            baseUrl = baseUrl,
            endpoint = FederationEndpoints.MODEL_SHARD_RANGES,
            body = body,
            extraHeaders = sessionHeader(sessionToken),
        )
    }

    /**
     * `POST /v1/inference.dispatch`. Returns a cold [Flow] of
     * [TokenChunk]s. The flow terminates when:
     *  - the server emits a chunk with a non-null [TokenChunk.finishReason], or
     *  - the underlying HTTP call fails (exceptions propagate to the
     *    flow's `catch` operator — the caller is responsible for
     *    wrapping if it wants typed error recovery).
     */
    fun streamDispatch(
        baseUrl: String,
        request: InferenceDispatchRequest,
        sessionToken: String? = null,
    ): Flow<TokenChunk> = callbackFlow {
        val url = baseUrl.trimEnd('/') + FederationEndpoints.INFERENCE_DISPATCH
        val payload = json.encodeToString(request)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(MEDIA_TYPE))
            .headers(headersFor(baseUrl, sessionHeader(sessionToken)))
        val call = httpClient.newCall(requestBuilder.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(FederationError.internalError("dispatch connect failure: ${e.message}").asException())
            }
            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        val bodyText = r.body?.string().orEmpty()
                        val err = FederationCodec.decodeError(bodyText)
                        close(FederationError.internalError(
                            "dispatch HTTP ${r.code}: ${err.message}"
                        ).asException())
                        return
                    }
                    val source = r.body?.source()
                    if (source == null) {
                        close(FederationError.internalError("dispatch: empty body").asException())
                        return
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val chunk = try {
                                json.decodeFromString(TokenChunk.serializer(), line)
                            } catch (t: Throwable) {
                                close(FederationError.internalError(
                                    "dispatch: malformed chunk: ${t.message}"
                                ).asException())
                                return
                            }
                            trySend(chunk)
                            if (chunk.finishReason != null) break
                        }
                        close()
                    } catch (t: Throwable) {
                        close(FederationError.internalError(
                            "dispatch: stream failure: ${t.message}"
                        ).asException())
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** `POST /v1/agent.handoff` — synchronous, returns
     *  [HandoffResponse] on success. */
    suspend fun submitHandoff(
        baseUrl: String,
        token: HandoffToken,
        sessionToken: String? = null,
    ): FederationResult<HandoffResponse> {
        val body = json.encodeToString(token)
        return executeTyped(
            baseUrl = baseUrl,
            endpoint = FederationEndpoints.AGENT_HANDOFF,
            body = body,
            extraHeaders = sessionHeader(sessionToken),
        )
    }

    // ---- internals ----

    private suspend inline fun <reified T> executeTyped(
        baseUrl: String,
        endpoint: String,
        body: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): FederationResult<T> {
        val url = baseUrl.trimEnd('/') + endpoint
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(MEDIA_TYPE))
            .headers(headersFor(baseUrl, extraHeaders))
        val call = httpClient.newCall(requestBuilder.build())
        return try {
            val response = call.awaitResponse()
            response.use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    val parsed = runCatching { json.decodeFromString<T>(text) }
                    when {
                        parsed.isSuccess -> FederationResult.Ok(parsed.getOrThrow())
                        else -> FederationResult.Err(
                            FederationError.internalError(
                                "decode ${T::class.simpleName} failed: ${parsed.exceptionOrNull()?.message}"
                            )
                        )
                    }
                } else {
                    FederationResult.Err(FederationCodec.decodeError(text))
                }
            }
        } catch (t: Throwable) {
            FederationResult.Err(FederationError.internalError("${endpoint}: ${t.message}"))
        }
    }

    private fun headersFor(
        baseUrl: String,
        extraHeaders: Map<String, String>,
    ): Headers {
        val builder = Headers.Builder()
            .add("Content-Type", MEDIA_TYPE.toString())
            .add("Accept", MEDIA_TYPE.toString())
            .add(FederationProtocol.HEADER_PROTOCOL_VERSION, localProtocolVersion.toString())
        extraHeaders.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }

    private fun sessionHeader(token: String?): Map<String, String> =
        if (token == null) emptyMap() else mapOf(HEADER_SESSION_TOKEN to token)

    private fun FederationError.asException(): Throwable = FederationWireException(this)

    companion object {
        val MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // streaming — no read timeout
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

/** Outcome of a typed request. Err carries a wire-level [FederationError]
 *  for the caller to match on. */
sealed interface FederationResult<out T> {
    data class Ok<T>(val value: T) : FederationResult<T>
    data class Err(val error: FederationError) : FederationResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): FederationError? = (this as? Err)?.error
    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    companion object {
        fun <T> ok(value: T): FederationResult<T> = Ok(value)
        fun err(error: FederationError): FederationResult<Nothing> = Err(error)
        fun <T> errFromMessage(message: String): FederationResult<T> = Err(FederationError.internalError(message))
    }
}

/** Marker exception wrapping a [FederationError] so the streaming
 *  flow can surface typed failures. Callers should catch this and
 *  extract `error` via [wireError]. */
class FederationWireException(val error: FederationError) : RuntimeException(error.message)

/** Awaitable wrapper around OkHttp's enqueue API. */
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resumeWith(Result.success(response))
        }
    })
    cont.invokeOnCancellation {
        try { cancel() } catch (_: Throwable) { /* ignore */ }
    }
}