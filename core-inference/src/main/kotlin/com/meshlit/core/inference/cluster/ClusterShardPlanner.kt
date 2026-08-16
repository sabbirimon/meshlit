package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.ShardSpec
import com.meshlit.core.inference.net.StageRole
import kotlin.math.ceil
import kotlin.math.max
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
 *     peers that already host the same shard (sticky).
 *
 *  3. **Fallback** — if `sum(peer.freeDiskMb * 1MiB) < modelSizeBytes`
 *     the cluster can't host the model. Caller should fall back to
 *     whole-model download via `ModelCatalog.downloadFromUrl`.
 *
 * The plan keeps shard ranges as `[layerStart, layerEnd)` so the
 * downstream `ShardAssembler` can map them to byte offsets in the
 * final GGUF.
 */
class ClusterShardPlanner(
    private val shardSizeBytes: Long = DEFAULT_SHARD_SIZE_BYTES,
) {

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
        if (totalLayers <= 0) {
            return Plan.FallbackToWholeModel(
                reason = "model has 0 layers",
            )
        }
        val roster = (listOf(self) + peers).distinctBy { it.peerId }

        val eligibleByTier = roster.filter { it.freeDiskMb > 0 }
        if (eligibleByTier.isEmpty()) {
            return Plan.FallbackToWholeModel(reason = "no peers report free disk")
        }

        // 1. Single-shard / whole-model.
        val withEnough = eligibleByTier.filter {
            it.freeDiskMb * BYTES_PER_MB >= modelSizeBytes
        }
        if (withEnough.isNotEmpty()) {
            val host = withEnough.maxBy { it.freeDiskMb + gpuBonusMb(it) }
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
        val shardsByCapacity = max(1L, ceil(modelSizeBytes.toDouble() / shardSizeBytes.toDouble()).toLong())
        val shardCount = min(shardsByCapacity.toInt(), totalLayers)
        val sorted = eligibleByTier.sortedByDescending { peer ->
            // GPU peers get a tiny tie-breaker so the planner picks
            // them for early layers when disk capacity is comparable.
            // 256 MB equivalent worth of "preferredness" — enough to
            // outrank a peer with 256 MB less free disk.
            peer.freeDiskMb + gpuBonusMb(peer)
        }

        val totalClusterFree = sorted.sumOf { it.freeDiskMb * BYTES_PER_MB }
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
                    shardsById[key.removePrefix("$modelId/")] = peer
                }
            }
        }

        val bytesPerLayer = modelSizeBytes.toDouble() / totalLayers.toDouble()
        val assignments = ArrayList<ShardAssignment>(shardCount)
        for (i in 0 until shardCount) {
            val layerStart = layerForShard(i, shardCount, totalLayers)
            val layerEnd = layerForShard(i + 1, shardCount, totalLayers)
            val byteOffset = (layerStart * bytesPerLayer).toLong()
            val byteLength = ((layerEnd - layerStart) * bytesPerLayer).toLong()
            val shardId = "shard-${i.toString().padStart(3, '0')}"
            val sticky = shardsById[shardId]
            val peer = sticky ?: sorted[i % sorted.size]
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
        private const val BYTES_PER_MB: Long = 1024L * 1024
        /** Planner tie-breaker for peers with a working GPU backend. */
        private const val GPU_BONUS_MB: Long = 256L
    }
}
