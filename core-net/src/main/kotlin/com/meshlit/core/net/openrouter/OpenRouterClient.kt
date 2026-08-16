package com.meshlit.core.net.openrouter

import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Phase 4 — OpenRouter HTTP client.
 *
 * Mirrors the OpenAI schema over OkHttp. The client carries no
 * per-instance API key; callers pass the key into each method.
 * That makes the key lifecycle easy to audit (`load()` once, use
 * the resulting String only inside this class).
 *
 * Why OkHttp (already in the dep tree) over Ktor: the existing
 * `core-net/.../monitor/TrafficStatsMonitor.kt` already builds an
 * OkHttp client with the same socket-tag / tracing conventions.
 * OpenRouter rides the same client for free.
 */
class OpenRouterClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = OpenRouterConfig.BASE_URL,
    private val json: Json = OpenRouterJson,
) {

    private val log = logger("OpenRouterClient")

    /**
     * Validate the API key by hitting `/api/v1/auth/key`. Returns
     * the [OpenRouterAuthKeyData] on success, or
     * [OpenRouterException.Unauthorized] when the key is rejected.
     * Any other non-200 is wrapped in [OpenRouterException.Http]
     * with the status code + message.
     */
    suspend fun validateKey(apiKey: String): OpenRouterAuthKeyData = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/auth/key".toHttpUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", OpenRouterConfig.APP_REFERER)
            .header("X-Title", OpenRouterConfig.APP_TITLE)
            .get()
            .build()
        val resp = httpClient.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> {
                    val parsed = json.decodeFromString(
                        OpenRouterAuthKey.serializer(),
                        body,
                    )
                    parsed.data
                }
                r.code == 401 -> throw OpenRouterException.Unauthorized(
                    "OpenRouter rejected the API key",
                )
                else -> throw OpenRouterException.Http(
                    code = r.code,
                    message = "validateKey failed: $body",
                )
            }
        }
    }

    /**
     * Fetch the model catalog. Returns the full list — OpenRouter
     * doesn't paginate. Used by the model browser screen.
     */
    suspend fun listModels(apiKey: String): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/models".toHttpUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", OpenRouterConfig.APP_REFERER)
            .header("X-Title", OpenRouterConfig.APP_TITLE)
            .get()
            .build()
        val resp = httpClient.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw OpenRouterException.Http(
                    code = r.code,
                    message = "listModels failed: $body",
                )
            }
            json.decodeFromString(OpenRouterModelsResponse.serializer(), body).data
        }
    }

    /**
     * Stream a chat completion. Emits one [OpenRouterStreamEvent]
     * per SSE `data: {…}` frame. The final frame before `[DONE]`
     * carries the usage block; the dispatcher demuxes that into
     * Meshlit's `TokenChunk.usage` so the Jobs card can show the
     * per-request cost.
     */
    fun streamChat(
        apiKey: String,
        request: OpenRouterChatRequest,
    ): Flow<OpenRouterStreamEvent> = callbackFlow {
        val body = json.encodeToString(OpenRouterChatRequest.serializer(), request.copy(stream = true))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpReq = Request.Builder()
            .url("$baseUrl/chat/completions".toHttpUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", OpenRouterConfig.APP_REFERER)
            .header("X-Title", OpenRouterConfig.APP_TITLE)
            .header("Accept", "text/event-stream")
            .post(body)
            .build()
        val call = httpClient.newCall(httpReq)
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    trySend(OpenRouterStreamEvent.Failure(e.message ?: "stream_io_error"))
                    close(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            val errBody = r.body?.string().orEmpty()
                            trySend(
                                OpenRouterStreamEvent.Failure(
                                    "HTTP ${r.code}: ${errBody.take(2048)}",
                                ),
                            )
                            close()
                            return
                        }
                        val source = r.body?.source()
                        if (source == null) {
                            trySend(OpenRouterStreamEvent.Failure("empty_response_body"))
                            close()
                            return
                        }
                        try {
                            while (!source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val payload = line.removePrefix("data:").trim()
                                if (payload.isEmpty()) continue
                                if (payload == "[DONE]") {
                                    trySend(OpenRouterStreamEvent.Done)
                                    break
                                }
                                val parsed = parseStreamChunk(payload)
                                if (parsed != null) trySend(parsed)
                            }
                            close()
                        } catch (e: Throwable) {
                            trySend(OpenRouterStreamEvent.Failure("stream_parse_error: ${e.message}"))
                            close(e)
                        }
                    }
                }
            })
        } catch (e: Throwable) {
            trySend(OpenRouterStreamEvent.Failure("enqueue_failed: ${e.message}"))
            close(e)
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming chat completion. Used by callers that need
     * the full assembled text (e.g. cost-attribution snapshots,
     * Jobs card summaries). Returns the final assistant message
     * plus usage.
     */
    suspend fun chatCompletions(
        apiKey: String,
        request: OpenRouterChatRequest,
    ): OpenRouterChatResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            OpenRouterChatRequest.serializer(),
            request.copy(stream = false),
        ).toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/chat/completions".toHttpUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", OpenRouterConfig.APP_REFERER)
            .header("X-Title", OpenRouterConfig.APP_TITLE)
            .header("Accept", "application/json")
            .post(body)
            .build()
        val resp = httpClient.newCall(req).execute()
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 401) {
                throw OpenRouterException.Unauthorized("OpenRouter rejected the API key")
            }
            if (!r.isSuccessful) {
                throw OpenRouterException.Http(
                    code = r.code,
                    message = "chatCompletions failed: $body",
                )
            }
            json.decodeFromString(OpenRouterChatResponse.serializer(), body)
        }
    }

    /**
     * Parse a single SSE `data: {…}` chunk. The streaming shape
     * mirrors a [OpenRouterChatResponse] but only the `id`,
     * `model`, `choices[].delta.content`, `choices[].finish_reason`,
     * and `usage` fields are populated. We use kotlinx.serialization
     * for the strongly-typed fields and JsonObject for the delta
     * so we tolerate providers that emit slightly different keys.
     */
    private fun parseStreamChunk(payload: String): OpenRouterStreamEvent? {
        return try {
            val obj = json.parseToJsonElement(payload).jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val model = obj["model"]?.jsonPrimitive?.contentOrNull ?: ""
            val usage = obj["usage"]?.let { parseUsage(it.jsonObject) }
            val choices = obj["choices"]?.jsonArray ?: emptyList()
            if (choices.isEmpty()) {
                if (usage != null) return OpenRouterStreamEvent.UsageOnly(id, model, usage)
                return null
            }
            val firstChoice = choices.first().jsonObject
            val index = firstChoice["index"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.contentOrNull
            val deltaContent = firstChoice["delta"]?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
                ?: firstChoice["message"]?.jsonObject?.get("content")
                    ?.jsonPrimitive?.contentOrNull
                ?: ""
            OpenRouterStreamEvent.Delta(
                id = id,
                model = model,
                choiceIndex = index,
                content = deltaContent,
                finishReason = finishReason,
                usage = usage,
            )
        } catch (e: Throwable) {
            log.warn("OpenRouterClient", "parseStreamChunk failed: ${e.message}")
            null
        }
    }

    private fun parseUsage(obj: JsonObject): OpenRouterUsage {
        return OpenRouterUsage(
            promptTokens = obj["prompt_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            completionTokens = obj["completion_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            totalTokens = obj["total_tokens"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0,
            cost = obj["cost"]?.jsonPrimitive?.contentOrNull,
        )
    }

    companion object {
        /**
         * Default OkHttp client. 30s total timeout, 20s first-byte
         * timeout (so a hung OpenRouter upstream doesn't keep the
         * Meshlit UI spinner spinning forever).
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(OpenRouterConfig.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Shared Json instance — `ignoreUnknownKeys` so a new
         *  OpenRouter field doesn't break Meshlit. */
        val OpenRouterJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }
    }
}

/** One event on the streaming chat flow. The dispatcher converts
 *  these into Meshlit's existing `TokenChunk` shape so the SSE
 *  callback surface stays the same as the on-device path. */
sealed interface OpenRouterStreamEvent {
    val id: String
    val model: String

    /** Token delta + (optionally) the final usage + finish_reason. */
    data class Delta(
        override val id: String,
        override val model: String,
        val choiceIndex: Int,
        val content: String,
        val finishReason: String? = null,
        val usage: OpenRouterUsage? = null,
    ) : OpenRouterStreamEvent

    /** Some providers emit a usage-only frame with no content;
     *  we surface that so the dispatcher can attribute cost. */
    data class UsageOnly(
        override val id: String,
        override val model: String,
        val usage: OpenRouterUsage,
    ) : OpenRouterStreamEvent

    /** The `[DONE]` sentinel. After this, no more events arrive. */
    data object Done : OpenRouterStreamEvent {
        override val id: String = ""
        override val model: String = ""
    }

    /** Network / parse failure — the dispatcher surfaces this as
     *  a typed failure on the TokenChunk stream. */
    data class Failure(val message: String) : OpenRouterStreamEvent {
        override val id: String = ""
        override val model: String = ""
    }
}

/** Typed errors. Distinct subclasses let callers branch on
 *  auth-vs-network-vs-rate-limit without string-matching. */
sealed class OpenRouterException(message: String) : RuntimeException(message) {
    /** 401 from /auth/key or /chat/completions — user must
     *  re-enter a valid key. */
    class Unauthorized(message: String) : OpenRouterException(message)
    /** Any other non-2xx response. */
    class Http(val code: Int, message: String) : OpenRouterException(message)
    /** Socket / parse / timeout failure. */
    class Network(message: String) : OpenRouterException(message)
    /** 429 — rate-limited. The UI surfaces this as "slow down". */
    class RateLimited(val retryAfterMs: Long?, message: String) : OpenRouterException(message)
}