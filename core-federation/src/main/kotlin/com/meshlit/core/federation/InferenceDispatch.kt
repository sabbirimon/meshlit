package com.meshlit.core.federation

import kotlinx.serialization.Serializable

/**
 * Body of `POST /v1/inference.dispatch`. The client (caller) sends
 * this once; the server responds with a chunked stream of [TokenChunk]s.
 *
 * The caller chooses the model by stable [modelId] (e.g.
 * `smollm2-360m-instruct-q8_0`). The server refuses with
 * [FederationError.modelUnavailable] if it cannot serve that model.
 *
 * Wire fields:
 *  - [requestId]    — opaque id assigned by the client. Echoed in
 *                     every [TokenChunk] so the client can demux
 *                     multiple concurrent dispatches.
 *  - [modelId]      — see above.
 *  - [prompt]       — the user's prompt. The server is responsible for
 *                     applying its own template (chat template, BOS
 *                     token, etc.) — the client sends raw text.
 *  - [maxTokens]    — upper bound on generated tokens. The server MAY
 *                     stop earlier (e.g. on EOS, or on `cancel`).
 *  - [temperature], [topP], [topK], [repeatPenalty] — generation
 *                     hyperparameters. Server uses its defaults for any
 *                     missing field.
 *  - [seed]         — optional RNG seed. `null` ⇒ random.
 *  - [stopSequences] — strings at which the server MUST stop.
 */
@Serializable
data class InferenceDispatchRequest(
    val requestId: String,
    val modelId: String,
    val prompt: String,
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val seed: Long? = null,
    val stopSequences: List<String> = emptyList(),
)

/**
 * One chunk of a streaming inference response. The server emits one
 * per generated token; the connection closes after the chunk whose
 * [finishReason] is non-null.
 *
 * Wire fields:
 *  - [requestId]     — echoes the client's request id.
 *  - [seq]           — monotonically increasing per-request sequence
 *                      number. Lets the client detect out-of-order or
 *                      duplicate chunks even over a TLS-reordered
 *                      transport.
 *  - [tokenId]       — the llama.cpp / engine token id. `-1` for the
 *                      sentinel chunks.
 *  - [text]          — the rendered token text (utf-8). Empty for
 *                      sentinel chunks.
 *  - [finishReason]  — non-null on the final chunk: one of `natural`,
 *                      `length`, `stop_sequence`, `cancelled`, `error`.
 *                      `null` for intermediate chunks.
 *  - [error]         — non-null when [finishReason] == `error`. Wire
 *                      shape matches [FederationError].
 */
@Serializable
data class TokenChunk(
    val requestId: String,
    val seq: Long,
    val tokenId: Long,
    val text: String,
    val finishReason: String? = null,
    val error: FederationError? = null,
) {
    companion object {
        // Finish reasons — kept as constants so consumers can match
        // without mistyping a string.
        const val FINISH_NATURAL: String = "natural"
        const val FINISH_LENGTH: String = "length"
        const val FINISH_STOP_SEQUENCE: String = "stop_sequence"
        const val FINISH_CANCELLED: String = "cancelled"
        const val FINISH_ERROR: String = "error"

        /** Sentinel for "no more chunks coming". Clients close the
         *  stream when they see this. */
        val TERMINAL = setOf(FINISH_NATURAL, FINISH_LENGTH, FINISH_STOP_SEQUENCE, FINISH_CANCELLED, FINISH_ERROR)
    }
}
