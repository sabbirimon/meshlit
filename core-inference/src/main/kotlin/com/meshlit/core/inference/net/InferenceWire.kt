package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the embedded HTTP/SSE inference server.
 *
 * These types are the *public contract* between Meshlit devices on the
 * LAN. They live in `:core-inference` so both server-side (the
 * NanoHTTPD routes) and client-side (the Android `:app` client) share
 * them. The companion wire types for `RequestHints` (the
 * `X-Meshlit-Hints` header) live next to `RouterRef` because the
 * router only consumes them on the server side and they're not part
 * of the JSON body schema.
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
 *
 * Phase M — monitoring:
 *  - `HealthResponse` gained four new fields (`capability_tier`,
 *    `engine_tag`, `loaded_shards`, `metrics`). All nullable so old
 *    clients still decode cleanly.
 *  - `MetricsSnapshot` is the in-process tail emitted with every
 *    `/v1/health` reply; the cluster pulls it to drive the
 *    `MetricsScreen` queue gauge + failure breakdown.
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

/**
 * Body of `GET /v1/health`.
 *
 * Backward compatibility: every field added in Phase M is nullable
 * so a peer built before the monitoring enrichment still parses. Old
 * clients continue to read `status`, `engine`, `port`.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val engine: String,
    val port: Int,
    val capabilityTier: CapabilityTier? = null,
    val engineTag: String? = null,
    /**
     * Phase 2 — multi-runtime support. The runtime + format the
     * coordinator has resolved for the most recent load (or the
     * default startup runtime if nothing has been loaded yet). Older
     * peers ignore this field.
     */
    val runtimeId: String? = null,
    val runtimeDisplayName: String? = null,
    val fileFormat: String? = null,
    val loadedShards: List<String> = emptyList(),
    val metrics: MetricsSnapshot? = null,
)

/**
 * Body of `GET /v1/model`.
 *
 * `shardRanges` is empty for whole-model loads and populated for
 * sharded loads so a coordinator can pick which peer hosts which
 * layer range without an out-of-band query.
 */
@Serializable
data class ModelStateResponse(
    val loaded: Boolean,
    val name: String? = null,
    val contextSize: Int? = null,
    val parameterCount: Long? = null,
    val quantization: String? = null,
    val shardRanges: List<ShardRange> = emptyList(),
)

/**
 * Inclusive-exclusive layer range this peer is hosting. Used by the
 * planner when picking sharded assignments.
 */
@Serializable
data class ShardRange(
    @SerialName("layer_start") val layerStart: Int,
    @SerialName("layer_end") val layerEnd: Int,
)

/**
 * Phase 2 — body of `GET /v1/runtimes`. Carries the full runtime
 * catalog this device is willing to host, the runtime the device is
 * *currently* using (may differ from the catalog when a runtime
 * promotion happened mid-session), and a small summary so callers
 * can pick peers without parsing the full list.
 *
 * The wire format is intentionally compact: peers running on
 * metered connections (cellular) should be able to answer the
 * "which peer hosts runtime X?" question with a single small
 * request. The full descriptor list is still useful for the
 * planner in `:app` to draw the device roster.
 */
@Serializable
data class RuntimesResponse(
    /** The runtime this device is currently serving from. May be `null`
     *  if no load has been attempted yet (e.g. fresh start). */
    val deviceRuntimeId: String? = null,
    /** Human-readable name of the active runtime. */
    val deviceRuntimeDisplayName: String? = null,
    /** Full catalog of runtimes this APK ships or knows about. */
    val runtimes: List<RuntimeDescriptor> = emptyList(),
    /** Cheap summary for routing decisions. */
    val summary: RuntimeCatalogSummary = RuntimeCatalogSummary(),
)

/**
 * One entry in the runtime catalog. Mirrors
 * [com.meshlit.core.inference.RuntimeEngine] 1:1 but lives in the
 * wire package so peers don't need to import a sealed type.
 */
@Serializable
data class RuntimeDescriptor(
    val runtimeId: String,
    val displayName: String,
    /** One of: "shipped", "candidate", "apple_only", "unavailable". */
    val status: String,
    /** File extensions this runtime can load, e.g. ["gguf"]. */
    val supportedFormats: List<String> = emptyList(),
    /** Approximate native lib size contribution in bytes. 0 for
     *  Apple-only / unavailable runtimes. */
    val approxApkFootprintBytes: Long = 0L,
)

/** Compact summary of the catalog. Used by peers to pick a target
 *  device without parsing the full descriptor list. */
@Serializable
data class RuntimeCatalogSummary(
    val shippedCount: Int = 0,
    val candidateCount: Int = 0,
    val appleOnlyCount: Int = 0,
)

/**
 * Lightweight in-process snapshot shipped with every `/v1/health`
 * reply. All counters are monotonic since process start.
 *
 * The shape mirrors what the `MetricsRegistry` in `:app` exposes via
 * the `peerHealthMap` flow — so the screen that reads one can also
 * read the other without re-deriving.
 */
@Serializable
data class MetricsSnapshot(
    val queueDepth: Int = 0,
    val totalJobs: Long = 0L,
    val successJobs: Long = 0L,
    val failureTags: Map<String, Long> = emptyMap(),
    val totalTokensGenerated: Long = 0L,
    val avgTokensPerSecond: Float = 0f,
    val uptimeSeconds: Long = 0L,
)

/** Names of the SSE event types. Reused by the client parser. */
object SseEvents {
    const val TOKEN = "token"
    const val DONE = "done"
    const val ERROR = "error"
}