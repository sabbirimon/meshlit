package com.meshlit.core.federation

import kotlinx.serialization.Serializable

/**
 * Body of `POST /v1/health.capability`. Lightweight, stateless
 * exchange used by the agent kernel to decide whether a peer is a
 * viable target for a `Task`.
 *
 * Unlike [PeerHello], this endpoint does NOT establish a session token
 * and does NOT mutate the trust store. It is safe to call as often as
 * the UI wants without side effects.
 *
 * Wire fields:
 *  - [matrix]    — sender's static [CapabilityMatrixDto]. The
 *                  receiver merges this with its own view (and any
 *                  dynamic `CapabilitySnapshot` from Slice 5) to
 *                  decide.
 *  - [loadedModels] — the model ids currently loaded in the sender's
 *                  runtime. Lets the receiver route a `Task` to a
 *                  peer that already has the right model in memory.
 *  - [busy]      — `true` if the sender is currently at capacity and
 *                  would refuse new dispatches. The receiver treats
 *                  this as a soft hint, not a hard rejection.
 *  - [asOfMillis] — sender wall-clock. Lets the receiver age out
 *                  stale responses (e.g. if it's been > 5 s since
 *                  `asOfMillis`, treat the busy/matrix data as
 *                  suspect).
 */
@Serializable
data class CapabilityExchange(
    val matrix: CapabilityMatrixDto,
    val loadedModels: Set<String> = emptySet(),
    val busy: Boolean = false,
    val asOfMillis: Long,
)

/**
 * Body of `POST /v1/model.shardRanges` — returns the GGUF shard
 * layout for a model. Used by Slice 4 (Phase 7). v1 returns
 * `501 Not Implemented` with [FederationError.unsupportedFeature] on
 * peers that haven't implemented sharding yet.
 *
 * The [ModelShardLayout] shape is the server's authoritative view.
 */
@Serializable
data class ModelShardRangesRequest(
    val modelId: String,
)

@Serializable
data class ModelShardLayout(
    val modelId: String,
    val totalSizeBytes: Long,
    val shards: List<ShardDescriptor>,
) {
    @Serializable
    data class ShardDescriptor(
        val index: Int,
        val offsetBytes: Long,
        val sizeBytes: Long,
        val sha256: String,
    )
}