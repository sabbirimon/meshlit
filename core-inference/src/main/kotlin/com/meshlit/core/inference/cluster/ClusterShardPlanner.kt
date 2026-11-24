package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.ShardSpec
import com.meshlit.core.inference.net.StageRole
import kotlin.math.min

/**
 * Pure (no I/O) shard planner.
 *
 * The planner is intentionally **deterministic** — given the same
 * inputs it always returns the same plan. This lets peers re-derive
 * the assignment table without a distributed consensus step; any
 * peer that has the manifest + the current peer roster can compute
 * the same plan.
 *
 * Policy (in order):
 *
 *  1. **Single-shard / whole-model plan** — if any peer (including
 *     `self`) has free disk ≥ model size, pick the peer with the
 *     most free disk and emit one assignment for all `totalLayers`.
 *     Cheapest, no reassembly cost.
 *
 *  2. **Multi-shard plan** — split the model into N shards of
 *     `shardSizeBytes` each (capped by `totalLayers`). Distribute
 *     shards to peers in descending free-disk order, preferring
 *     peers that already host the same shard (sticky), while charging
 *     every assignment against that peer's remaining disk budget.
 *
 *  3. **Fallback** — if `sum(peer.freeDiskMb * 1MiB) < modelSizeBytes`
 *     the cluster can't host the model. Caller should fall back to
 *     whole-model download via `ModelCatalog.downloadFromUrl`.
 *
 * Byte ranges cover the source exactly for storage/reassembly. Layer
 * ranges are proportional labels, not GGUF tensor boundaries: this
 * storage plan alone does not admit RAM or enable layer inference.
 */
class ClusterShardPlanner(
    private val shardSizeBytes: Long = DEFAULT_SHARD_SIZE_BYTES,
) {
    init { require(shardSizeBytes > 0) { "Shard size must be positive" } }

    private val log = logger("ClusterShardPlanner")

    /**
     * GPU bonus added to the planner's sort key. A Vulkan peer
     * (integrated or eGPU) wins over a CPU peer with the same free
     * disk because the GPU peer will run inference faster once the
     * shard is loaded. The bonus is intentionally small (256 MiB
     * equivalent) so it never overrides a ≥ 1 GiB free-disk gap;
     * otherwise we'd starve CPU-heavy clusters just for a few ms of
     * inference latency.
     */
    private fun gpuBonusMb(peer: PeerCapabilities): Long =
        if (peer.hasGpu) GPU_BONUS_MB else 0L

    fun plan(
        modelId: String,
        modelSizeBytes: Long,
        totalLayers: Int,
        self: PeerCapabilities,
        peers: List<PeerCapabilities>,
    ): Plan {
        if (totalLayers !in 1..MAX_LAYERS || modelSizeBytes <= 0) {
            return Plan.FallbackToWholeModel(
                reason = "invalid model size or unsupported layer count (1–$MAX_LAYERS)",
            )
        }
        val roster = (listOf(self) + peers).distinctBy { it.peerId }

        // Ignore invalid/unrepresentable reports rather than allowing a MiB
        // conversion or GPU preference score to wrap into usable capacity.
        val eligibleByTier = roster.filter { it.freeDiskMb in 1..Long.MAX_VALUE / BYTES_PER_MB }
        if (eligibleByTier.isEmpty()) {
            return Plan.FallbackToWholeModel(reason = "no peers report free disk")
        }

        // 1. Single-shard / whole-model.
        val withEnough = eligibleByTier.filter {
            it.freeDiskMb * BYTES_PER_MB >= modelSizeBytes
        }
        if (withEnough.isNotEmpty()) {
            val host = withEnough.sortedWith(
                compareByDescending<PeerCapabilities> { it.freeDiskMb + gpuBonusMb(it) }.thenBy { it.peerId },
            ).first()
            val shardId = "whole-${modelId}"
            log.info(
                "shard.plan.single",
                "single-shard plan",
                mapOf(
                    "modelId" to modelId,
                    "host" to host.peerId,
                    "sizeBytes" to modelSizeBytes,
                ),
            )
            return Plan.SingleShard(
                assignments = listOf(
                    ShardAssignment(
                        shardId = shardId,
                        layerStart = 0,
                        layerEnd = totalLayers,
                        peerId = host.peerId,
                        byteOffset = 0L,
                        byteLength = modelSizeBytes,
                    ),
                ),
            )
        }

        // 2. Multi-shard.
        val shardsByCapacity = modelSizeBytes / shardSizeBytes + if (modelSizeBytes % shardSizeBytes == 0L) 0 else 1
        val shardCount = minOf(shardsByCapacity, totalLayers.toLong()).toInt()
        val sorted = eligibleByTier.sortedWith(compareByDescending<PeerCapabilities> { peer ->
            // GPU peers get a tiny tie-breaker so the planner picks
            // them for early layers when disk capacity is comparable.
            // 256 MB equivalent worth of "preferredness" — enough to
            // outrank a peer with 256 MB less free disk.
            peer.freeDiskMb + gpuBonusMb(peer)
        }.thenBy { it.peerId })

        // Sum only up to the requested model size; independent valid reports
        // may otherwise overflow Long when combined.
        val totalClusterFree = sorted.fold(0L) { total, peer ->
            total + minOf(modelSizeBytes - total, peer.freeDiskMb * BYTES_PER_MB)
        }
        if (totalClusterFree < modelSizeBytes) {
            log.warn(
                "shard.plan.no_capacity",
                "cluster has insufficient free disk",
                mapOf(
                    "modelId" to modelId,
                    "required" to modelSizeBytes,
                    "available" to totalClusterFree,
                ),
            )
            return Plan.FallbackToWholeModel(
                reason = "cluster free disk $totalClusterFree < model size $modelSizeBytes",
            )
        }

        // Sticky assignment: any peer that already hosts a shard of
        // this model wins its previous slot. We rebuild the layer
        // order so the sticky shards keep their place.
        val shardsById = mutableMapOf<String, PeerCapabilities>()
        sorted.forEach { peer ->
            peer.hostedShardIds.forEach { key ->
                if (key.startsWith("$modelId/")) {
                    shardsById.putIfAbsent(key.removePrefix("$modelId/"), peer)
                }
            }
        }

        // floor(size * layer / layers), without an overflowing multiplication
        // or floating-point rounding. Taking differences preserves every byte.
        fun byteBoundary(layer: Int): Long = modelSizeBytes / totalLayers * layer +
            (modelSizeBytes % totalLayers) * layer / totalLayers
        val remaining = sorted.associate { it.peerId to it.freeDiskMb * BYTES_PER_MB }.toMutableMap()
        val assignments = ArrayList<ShardAssignment>(shardCount)
        for (i in 0 until shardCount) {
            val layerStart = layerForShard(i, shardCount, totalLayers)
            val layerEnd = layerForShard(i + 1, shardCount, totalLayers)
            val byteOffset = byteBoundary(layerStart)
            val byteLength = byteBoundary(layerEnd) - byteOffset
            val shardId = "shard-${i.toString().padStart(3, '0')}"
            val sticky = shardsById[shardId]
            val peer = sticky?.takeIf { remaining.getValue(it.peerId) >= byteLength }
                ?: (sorted.indices).asSequence().map { sorted[(i + it) % sorted.size] }
                    .firstOrNull { remaining.getValue(it.peerId) >= byteLength }
                ?: return Plan.FallbackToWholeModel("no peer has remaining disk for shard $shardId ($byteLength bytes)")
            remaining[peer.peerId] = remaining.getValue(peer.peerId) - byteLength
            assignments += ShardAssignment(
                shardId = shardId,
                layerStart = layerStart,
                layerEnd = layerEnd,
                peerId = peer.peerId,
                byteOffset = byteOffset,
                byteLength = byteLength,
            )
        }
        // Role tagging — first shard runs FirstStage, last runs
        // LastStage, middle ones are MiddleStage(idx). Mirrors
        // `ShardSpec.stageRole`.
        val tagged = assignments.mapIndexed { idx, a ->
            val role = when {
                assignments.size == 1 -> StageRole.FirstStage
                idx == 0 -> StageRole.FirstStage
                idx == assignments.lastIndex -> StageRole.LastStage
                else -> StageRole.MiddleStage(idx - 1)
            }
            a.copy(stageRole = role)
        }
        log.info(
            "shard.plan.multi",
            "multi-shard plan",
            mapOf(
                "modelId" to modelId,
                "shards" to shardCount,
                "peers" to sorted.size,
            ),
        )
        return Plan.MultiShard(assignments = tagged)
    }

    /**
     * Distribute `totalLayers` across `shardCount` shards as evenly
     * as possible. The first shards absorb the remainder so we never
     * produce an empty range. Example: 7 layers / 3 shards →
     * [0,3), [3,5), [5,7) — i.e. 3 + 2 + 2.
     */
    private fun layerForShard(shardIndex: Int, shardCount: Int, totalLayers: Int): Int {
        val base = totalLayers / shardCount
        val remainder = totalLayers - base * shardCount
        return base * shardIndex + min(shardIndex, remainder)
    }

    /**
     * A single shard's assignment. `byteOffset` is the position
     * inside the final contiguous GGUF; `byteLength` is the byte
     * span to read from the peer (or local copy). The fields
     * mirror `ShardSpec` plus storage hints so the assembler can
     * plan its disk writes.
     */
    data class ShardAssignment(
        val shardId: String,
        val layerStart: Int,
        val layerEnd: Int,
        val peerId: String,
        val byteOffset: Long,
        val byteLength: Long,
        val stageRole: StageRole? = null,
    ) {
        /** Convert to the wire-format `ShardSpec` for the manifest. */
        fun toShardSpec(preferredTier: CapabilityTier, estimatedRamMb: Long): ShardSpec =
            ShardSpec(
                shardId = shardId,
                layerStart = layerStart,
                layerEnd = layerEnd,
                preferredCapabilityTier = preferredTier,
                estimatedRamMb = estimatedRamMb,
                stageRole = stageRole ?: StageRole.MiddleStage(0),
            )
    }

    /** Result of a planning run. */
    sealed class Plan {
        data class SingleShard(val assignments: List<ShardAssignment>) : Plan()
        data class MultiShard(val assignments: List<ShardAssignment>) : Plan()
        data class FallbackToWholeModel(val reason: String) : Plan()
    }

    companion object {
        /** 512 MiB default shard size — fits on phones with ≥ 4 GB free disk. */
        const val DEFAULT_SHARD_SIZE_BYTES: Long = 512L * 1024 * 1024
        private const val MAX_LAYERS = 1024
        private const val BYTES_PER_MB: Long = 1024L * 1024
        /** Planner tie-breaker for peers with a working GPU backend. */
        private const val GPU_BONUS_MB: Long = 256L
    }
}
