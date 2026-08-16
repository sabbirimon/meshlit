package com.meshlit.core.net.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phase 4 — wire DTOs for the OpenRouter HTTP API.
 *
 * OpenRouter is OpenAI-compatible. We model the subset we need:
 *  - `/api/v1/chat/completions` (streaming + non-streaming)
 *  - `/api/v1/models` (catalog browser)
 *  - `/api/v1/auth/key` (key validation + usage + free-tier flag)
 *
 * **Wire compatibility**: OpenRouter extends the OpenAI schema
 * with `route`, `provider`, `models`, and `plugins`. We only model
 * the fields we use; kotlinx.serialization's
 * `ignoreUnknownKeys = true` keeps us forward-compatible.
 *
 * **Pricing**: OpenRouter reports prices as strings like
 * `"0.000005"` (USD per token). The UI multiplies by 1M to show
 * "$5.00 / 1M tokens". We don't pre-convert — callers get the raw
 * string and decide their own display precision.
 */
object OpenRouterConfig {
    /** Base URL for OpenRouter. OpenAI-compat lives at `/api/v1/`. */
    const val BASE_URL: String = "https://openrouter.ai/api/v1"

    /** Required for OpenRouter's leaderboard ranking. Meshlit
     *  sets these on every request. */
    const val APP_REFERER: String = "https://meshlit.ai"
    const val APP_TITLE: String = "Meshlit"

    /** Network timeout for non-streaming calls. OpenRouter's
     *  upstream models typically respond in <10s for short prompts. */
    const val DEFAULT_TIMEOUT_MS: Long = 30_000L

    /** Network timeout for the first chunk of a streaming call. */
    const val STREAM_FIRST_CHUNK_TIMEOUT_MS: Long = 20_000L

    /** Maximum tokens for a streaming chunk buffer. OpenRouter
     *  chunks are small (~32 tokens / chunk) but we cap the
     *  buffer so a runaway upstream doesn't OOM the device. */
    const val STREAM_CHUNK_BUFFER_BYTES: Int = 64 * 1024
}

/**
 * Wire shape for `POST /api/v1/chat/completions`. Mirrors OpenAI's
 * `ChatCompletionRequest` with one Meshlit-specific twist:
 * `provider` lets the caller pin a specific upstream (e.g.
 * `{"only": ["anthropic"]}`) when free models are routed by
 * default.
 */
@Serializable
data class OpenRouterChatRequest(
    /** Model id. OpenRouter uses `provider/model` format
     *  (`anthropic/claude-3.5-sonnet`, `meta-llama/llama-3-70b`,
     *  `qwen/qwen-2.5-72b-instruct:free`, etc.). */
    val model: String,
    /** Chat history. OpenAI format — `system` / `user` /
     *  `assistant` / `tool` roles. We don't carry `tool` messages
     *  in v1; the wire DTO is permissive so a future Meshlit
     *  release can add them without bumping. */
    val messages: List<OpenRouterMessage>,
    /** `true` for SSE streaming. We always stream from Meshlit so
     *  the UI's per-token callback works the same as it does for
     *  on-device inference. */
    val stream: Boolean = true,
    /** Optional sampling knobs. Defaults are conservative. */
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    /** OpenRouter-only: pin the upstream provider to avoid
     *  rate-limited endpoints. Free-tier users get this
     *  defaulted to `{"sort": "throughput"}`. */
    val provider: OpenRouterProviderPrefs? = null,
    /** Stable per-request id used for log correlation. Meshlit
     *  sets this to `request-${inferRequestId}` so a chat token
     *  stream can be matched across Meshlit's UI + OpenRouter's
     *  logs. */
    val user: String? = null,
)

/** One chat turn. */
@Serializable
data class OpenRouterMessage(
    /** Default "" so streaming delta chunks that omit role still
     *  decode. Non-streaming responses always include role. */
    val role: String = "",
    /** Default "" so empty-content chunks decode. */
    val content: String = "",
)

/** OpenRouter's provider routing hints. */
@Serializable
data class OpenRouterProviderPrefs(
    /** Restrict to these providers. Empty = any. */
    val only: List<String> = emptyList(),
    /** Avoid these providers (e.g. known-down). Empty = none. */
    val ignore: List<String> = emptyList(),
    /** `price` | `throughput` | `latency`. Default is throughput. */
    val sort: String = "throughput",
)

/**
 * Wire shape for a non-streaming chat completion response.
 * Streaming responses use the same JSON but wrapped in SSE
 * `data: {...}` frames — the [OpenRouterSseParser] demuxes them.
 */
@Serializable
data class OpenRouterChatResponse(
    val id: String,
    /** OpenRouter echoes `provider/model` so a multi-provider
     *  fallback is observable from the response. */
    val model: String,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage? = null,
    /** "stop" | "length" | "tool_calls" | "content_filter" |
     *  "error". OpenRouter normalizes these across providers. */
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

/** One choice in the response. */
@Serializable
data class OpenRouterChoice(
    val index: Int = 0,
    /** Non-streaming chunks carry `message`; streaming chunks carry
     *  `delta` instead. Either is optional so the deserialiser
     *  tolerates both shapes. */
    val message: OpenRouterMessage? = null,
    val delta: OpenRouterMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

/** Token + cost accounting. */
@Serializable
data class OpenRouterUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
    /** USD, as a string like "0.000014". Multiply by 1M for the
     *  "price per 1M tokens" UI display. */
    val cost: String? = null,
)

/**
 * Wire shape for `GET /api/v1/models`. The catalog is a `data`
 * array of [OpenRouterModel] entries, with metadata around it
 * (we only model what we need).
 */
@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel>,
)

/** One model in the OpenRouter catalog. */
@Serializable
data class OpenRouterModel(
    /** `provider/model` — what we send back in
     *  [OpenRouterChatRequest.model]. */
    val id: String,
    /** Human-readable name. */
    val name: String,
    /** Max context window in tokens. */
    @SerialName("context_length")
    val contextLength: Long = 0L,
    /** Pricing strings. USD per token. */
    val pricing: OpenRouterPricing = OpenRouterPricing(),
    /** Modality flags. We only model the text path; future
     *  Meshlit vision support can read input/output modalities. */
    val architecture: OpenRouterArchitecture? = null,
    /** `top_provider.max_completion_tokens` is the per-request
     *  cap, not the context window. */
    @SerialName("top_provider")
    val topProvider: OpenRouterTopProvider? = null,
    /** Subset of OpenAI params OpenRouter honours. Used by the UI
     *  to grey-out temperature / top_p when the model doesn't
     *  support them. */
    @SerialName("supported_parameters")
    val supportedParameters: List<String> = emptyList(),
) {
    /** Provider slug: `"anthropic/claude-3.5-sonnet"` →
     *  `"anthropic"`. */
    val providerSlug: String get() = id.substringBefore('/', missingDelimiterValue = "")

    /** Provider display name: `"anthropic"` → `"anthropic"` (the
     *  slug already renders cleanly for UI group headers). */
    val providerDisplay: String get() = providerSlug
}

@Serializable
data class OpenRouterPricing(
    /** USD per prompt token, as a string. */
    val prompt: String = "0",
    /** USD per completion token, as a string. */
    val completion: String = "0",
    /** USD per request (some image / audio models). */
    val request: String = "0",
    /** USD per image (image generation models). */
    val image: String = "0",
)

@Serializable
data class OpenRouterArchitecture(
    val modality: String = "text",
    @SerialName("input_modalities")
    val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities")
    val outputModalities: List<String> = emptyList(),
    val tokenizer: String = "Unknown",
)

@Serializable
data class OpenRouterTopProvider(
    @SerialName("context_length")
    val contextLength: Long = 0L,
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Long? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean = false,
)

/**
 * Wire shape for `GET /api/v1/auth/key`. Returns metadata for the
 * API key the caller authenticated with: usage, spend limit,
 * free-tier flag.
 *
 * Meshlit surfaces the free-tier flag on the Settings card so the
 * user knows they're on a rate-limited path.
 */
@Serializable
data class OpenRouterAuthKey(
    val data: OpenRouterAuthKeyData,
)

@Serializable
data class OpenRouterAuthKeyData(
    /** The label the user gave the key (e.g. "iPhone"). */
    val label: String? = null,
    /** USD limit set on this key (e.g. monthly cap). Null = none. */
    val limit: Double? = null,
    /** USD spent on this key in the current period. */
    val usage: Double = 0.0,
    /** `true` when this is a free-tier (no credits purchased) key.
     *  OpenRouter flags these so they can be rate-limited. */
    @SerialName("is_free_tier")
    val isFreeTier: Boolean = false,
    /** Token count in the current period. */
    @SerialName("usage_total_tokens")
    val usageTotalTokens: Long = 0L,
) {
    /** "Free tier" / "Pro tier" — UI copy. */
    val tierLabel: String get() = if (isFreeTier) "Free tier" else "Paid tier"

    /** "$3.21 / $10.00" or "$3.21 (no limit)" — UI copy. */
    val usageLabel: String get() {
        val spent = "$${"%.2f".format(usage)}"
        val cap = limit?.let { " / $${"%.2f".format(it)}" } ?: " (no limit)"
        return "$spent$cap"
    }
}