package com.meshlit.core.net.openrouter

import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.Flow

/**
 * Phase 4 — bridge between Meshlit's `InferRequest` / SSE event
 * surface and OpenRouter's `/api/v1/chat/completions`.
 *
 * The dispatcher is the unit `MiniRouter.cloud(...)` calls when
 * a request lands on the OpenRouter branch. It:
 *
 *  1. Looks up the API key via the supplied [keyProvider] (the
 *     Keystore-backed [OpenRouterKeyVault] in production).
 *  2. Builds the [OpenRouterChatRequest] from the Meshlit
 *     request fields + the supplied [modelId].
 *  3. Streams the SSE response, demuxing each [OpenRouterStreamEvent.Delta]
 *     into [InferTokenEvent] callbacks and the terminal
 *     `Done` / `Failure` into [InferDoneEvent] / [InferErrorEvent].
 *
 * The dispatcher's surface is **synchronous-looking** (callback
 * style) so it slots into the existing `Forwarder` / SSE plumbing
 * without changes — the server keeps streaming SSE regardless of
 * whether the work ran on a LAN peer, the local engine, or the
 * cloud.
 *
 * Wire-type decoupling: the dispatcher takes a plain
 * [CloudChatRequest] record (not `:core-inference`'s
 * `InferRequest`) so `core-net` doesn't have to depend on
 * `core-inference`. The `:app` layer translates Meshlit's wire
 * types into this record before invoking the dispatcher.
 *
 * Threading: callers (the SSE handler) MUST invoke this from a
 * coroutine. All OkHttp work runs on `Dispatchers.IO` via the
 * underlying [OpenRouterClient].
 *
 * Testability: the `streamChatFn` override lets tests inject a
 * stub `Flow<OpenRouterStreamEvent>` without subclassing
 * [OpenRouterClient] (which is final).
 */
class OpenRouterDispatcher(
    private val client: OpenRouterClient = OpenRouterClient(),
    private val keyProvider: OpenRouterKeyProvider,
    private val streamChatFn: (
        apiKey: String,
        request: OpenRouterChatRequest,
    ) -> Flow<OpenRouterStreamEvent> = { k, r -> client.streamChat(k, r) },
) {
    private val log = logger("OpenRouterDispatcher")

    /**
     * Plain request record. The :app layer maps `InferRequest`
     * into this shape before invoking [streamToSse].
     */
    data class CloudChatRequest(
        val prompt: String,
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
        val topP: Float = 0.95f,
    )

    /** One token delta event for the SSE handler. */
    data class InferTokenEvent(val text: String)

    /** Terminal success event for the SSE handler. */
    data class InferDoneEvent(
        val finishReason: String,
        val generatedTokens: Int,
        val totalDurationMs: Long,
        val tokensPerSecond: Float = 0f,
    )

    /** Terminal failure event for the SSE handler. */
    data class InferErrorEvent(
        val tag: String,
        val message: String,
    )

    /**
     * Stream one Meshlit [request] to OpenRouter's [modelId],
     * demuxing the response into the callback trio.
     *
     * Returns `Result.success(Unit)` when the stream ended cleanly
     * (or via a non-fatal finish reason); `Result.failure(...)`
     * when the key was missing / invalid, the upstream returned a
     * hard error, or the SSE stream broke.
     */
    suspend fun streamToSse(
        request: CloudChatRequest,
        modelId: String,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ): Result<Unit> {
        val key = keyProvider.provide()
            ?: return Result.failure(
                OpenRouterException.Unauthorized("OpenRouter API key not configured"),
            )
        val chatRequest = OpenRouterChatRequest(
            model = modelId,
            messages = listOf(
                OpenRouterMessage(role = "user", content = request.prompt),
            ),
            stream = true,
            temperature = request.temperature.toDouble(),
            topP = request.topP.toDouble(),
            maxTokens = request.maxTokens,
        )
        val started = System.currentTimeMillis()
        var generatedTokens = 0
        return runCatching {
            streamChatFn(key, chatRequest).collect { event ->
                when (event) {
                    is OpenRouterStreamEvent.Delta -> {
                        if (event.content.isNotEmpty()) {
                            onToken(InferTokenEvent(text = event.content))
                        }
                        event.finishReason?.let { reason ->
                            onDone(
                                InferDoneEvent(
                                    finishReason = reason,
                                    generatedTokens = generatedTokens,
                                    totalDurationMs = System.currentTimeMillis() - started,
                                    tokensPerSecond = computeTps(
                                        generatedTokens,
                                        System.currentTimeMillis() - started,
                                    ),
                                ),
                            )
                        }
                    }
                    is OpenRouterStreamEvent.UsageOnly -> {
                        generatedTokens = event.usage.completionTokens
                    }
                    OpenRouterStreamEvent.Done -> {
                        // Many providers don't include finishReason
                        // in the final delta — synthesize "stop"
                        // when the stream completes cleanly.
                        onDone(
                            InferDoneEvent(
                                finishReason = "stop",
                                generatedTokens = generatedTokens,
                                totalDurationMs = System.currentTimeMillis() - started,
                                tokensPerSecond = computeTps(
                                    generatedTokens,
                                    System.currentTimeMillis() - started,
                                ),
                            ),
                        )
                    }
                    is OpenRouterStreamEvent.Failure -> {
                        onError(
                            InferErrorEvent(
                                tag = "openrouter_stream_failure",
                                message = event.message,
                            ),
                        )
                    }
                }
            }
        }.onFailure { e ->
            log.warn(
                "OpenRouterDispatcher",
                "stream failed: ${e.message}",
            )
            onError(
                InferErrorEvent(
                    tag = classifyError(e),
                    message = e.message ?: e::class.java.simpleName,
                ),
            )
        }
    }

    /**
     * Token-accounting helper. Returns 0 when the call took less
     * than a millisecond (avoids divide-by-zero) and clamps to 1
     * decimal place.
     */
    private fun computeTps(tokens: Int, elapsedMs: Long): Float {
        if (elapsedMs <= 0L || tokens <= 0) return 0f
        val tps = (tokens.toDouble() * 1000.0) / elapsedMs.toDouble()
        return tps.coerceAtMost(9_999.9).toFloat()
    }

    /**
     * Classify an exception into the SSE `error.tag` namespace so
     * the UI can branch on the failure mode without string matching.
     */
    private fun classifyError(e: Throwable): String = when (e) {
        is OpenRouterException.Unauthorized -> "openrouter_unauthorized"
        is OpenRouterException.RateLimited -> "openrouter_rate_limited"
        is OpenRouterException.Http -> "openrouter_http_${e.code}"
        is OpenRouterException.Network -> "openrouter_network"
        else -> "openrouter_unknown"
    }

    companion object {
        /**
         * Convenience callback for wiring the dispatcher's key
         * lookup. Returns null when no key is stored yet (the
         * caller surfaces this as `Result.failure` so the SSE
         * handler can render a "Set up OpenRouter" hint).
         */
        fun interface OpenRouterKeyProvider {
            suspend fun provide(): String?
        }
    }
}