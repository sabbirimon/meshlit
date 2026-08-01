package com.meshlit.core.inference.net

import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the embedded HTTP/SSE inference server.
 *
 * These types are the *public contract* between Meshlit devices on the
 * LAN. They live in `:core-inference` so both server-side (the Ktor
 * routes) and client-side (the Android `:app` client) share them. The
 * companion wire types for `RequestHints` (the `X-Meshlit-Hints`
 * header) live in `:app` because the router only consumes them on the
 * server side and they're not part of the JSON body schema.
 *
 * Conventions:
 *  - snake_case is used for JSON fields to match HTTP/REST norms.
 *  - All numeric fields use conservative defaults that match the
 *    `InferenceEngine.InferenceRequest` defaults.
 *  - The `InferDoneEvent` mirrors the fields of an `InferenceResult`
 *    but only the bits the wire needs (no internal tags, no
 *    embeddings).
 *
 * Stability:
 *  - The wire is **v1**. Field additions are fine; renames or removals
 *    require a new route prefix (`/v2/infer`).
 *  - See BUILD_GUIDE §2 Phase 4.5 for the OpenAI-compatible gateway
 *    that will subsume these endpoints later.
 */

/** Body of `POST /v1/infer`. */
@Serializable
data class InferRequest(
    val prompt: String,
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopSequences: List<String> = emptyList(),
    val seed: Long = -1L,
)

/** SSE event payload `event: token`. */
@Serializable
data class InferTokenEvent(
    val text: String,
)

/** SSE event payload `event: done`. */
@Serializable
data class InferDoneEvent(
    val finishReason: String,
    val generatedTokens: Int,
    val totalDurationMs: Long,
    val tokensPerSecond: Float = 0f,
)

/** SSE event payload `event: error`. */
@Serializable
data class InferErrorEvent(
    val tag: String,
    val message: String = "",
)

/** Body of `GET /v1/health`. */
@Serializable
data class HealthResponse(
    val status: String,
    val engine: String,
    val port: Int,
)

/** Body of `GET /v1/model`. */
@Serializable
data class ModelStateResponse(
    val loaded: Boolean,
    val name: String? = null,
    val contextSize: Int? = null,
    val parameterCount: Long? = null,
    val quantization: String? = null,
)

/** Names of the SSE event types. Reused by the client parser. */
object SseEvents {
    const val TOKEN = "token"
    const val DONE = "done"
    const val ERROR = "error"
}