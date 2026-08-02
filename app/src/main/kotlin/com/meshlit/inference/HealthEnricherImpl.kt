package com.meshlit.inference

import com.meshlit.capability.CapabilityTier
import com.meshlit.core.inference.net.HealthEnricher
import com.meshlit.core.inference.net.ShardRef

/**
 * Backs the enriched `/v1/health` reply. Reads the local tier from
 * [CapabilityTier], the engine tag from the coordinator, and the live
 * `MetricsSnapshot` from [MetricsRegistry].
 *
 * `loadedShards` is sourced from the coordinator's
 * [com.meshlit.core.inference.InferenceCoordinator.loadedShards]
 * StateFlow — non-empty when this device is hosting a slice of a
 * sharded model. We map each `ShardRef` to a stable string id
 * (`modelId@layerStart-layerEnd`) so peers see the same keys over
 * restarts.
 */
class HealthEnricherImpl(
    private val tierProvider: () -> CapabilityTier,
    private val engineTagProvider: () -> String,
    private val metrics: MetricsRegistry,
    private val loadedShardsProvider: () -> List<ShardRef> = { emptyList() },
) : HealthEnricher {

    override fun snapshot(): HealthEnricher.HealthSnapshot {
        val shards = loadedShardsProvider().map { it.toShardId() }
        return HealthEnricher.HealthSnapshot(
            capabilityTier = tierProvider(),
            engineTag = engineTagProvider(),
            loadedShards = shards,
            metrics = metrics.snapshot(),
        )
    }

    private fun ShardRef.toShardId(): String =
        "$modelId@$layerStart-$layerEnd"
}