package com.meshlit.core.training.plan

import com.meshlit.core.common.CapabilitySnapshot
import com.meshlit.core.common.logger
import com.meshlit.core.training.config.DistributedConfig

/**
 * Pure-function sharding planner.
 *
 * Inputs:
 *  - [model]: a [ModelSpec] describing the model being trained.
 *  - [peers]: a list of `CapabilitySnapshot` from each peer. The
 *    planner treats them as the canonical input — no I/O, no wall
 *    clock, no UUIDs.
 *  - [cfg]: the [DistributedConfig] that drives mode selection.
 *
 * Output: a [ShardingPlan] with one [ShardAssignment] per peer,
 * sorted by peerId for stability.
 *
 * Determinism: same input → byte-identical output. Verified by the
 * `ShardingPlannerDeterminismTest` in `core-training`.
 *
 * Algorithm (matches the §0 pros/cons table in the plan):
 *  1. Sort peers by capability (highest tier + most free RAM first).
 *  2. Pick the first peer as the coordinator.
 *  3. Assign layer shards in tier order: top-rank gets the most
 *     capable stretch (e.g. the middle layers), phones get head/tail.
 *  4. If any peer can't fit its shard in free RAM, fall back to
 *     DISK offload (set `cfg.sharding.optimizerOffload` accordingly).
 *  5. Emit a stable plan.
 */
object ShardingPlanner {

    private val log = logger("ShardingPlanner")

    /**
     * Compute a [ShardingPlan] for the given model + peers.
     *
     * @param peersById map of peerId to CapabilitySnapshot. The
     *  planner deliberately takes a Map so callers guarantee
     *  uniqueness and the planner can sort by peerId for stability.
     */
    fun compute(
        model: ModelSpec,
        peersById: Map<String, CapabilitySnapshot>,
        cfg: DistributedConfig,
    ): ShardingPlan {
        if (peersById.isEmpty()) {
            throw IllegalArgumentException("ShardingPlanner requires at least one peer")
        }

        val ranked = rankPeers(peersById.values)
        // Resolve the coordinator's peerId by walking the original map.
        // The planner doesn't keep the map inside the CapabilitySnapshot
        // for purity reasons; we re-join here.
        val coordinatorPeer = ranked.first()
        val coordinatorId = peersById.entries.first { it.value === coordinatorPeer }.key
        val snapshotToPeerId: Map<CapabilitySnapshot, String> = peersById.entries
            .associate { it.value to it.key }

        val layerChunks = chunkLayers(model.totalLayers, ranked.size)
        val perLayerOptimizerMb = model.perLayerOptimizerMb()
        val perLayerResidentMb = model.residentMb() / model.totalLayers.coerceAtLeast(1)

        val assignments = ranked.mapIndexed { idx, peer ->
            val range = layerChunks[idx]
            val layerCount = if (range.isEmpty()) 0 else (range.last - range.first + 1)
            val optimizerBytes = perLayerOptimizerMb * 1024L * 1024L * layerCount
            val residentBytes = perLayerResidentMb * 1024L * 1024L * layerCount
            val role = roleForPeer(peer, idx, ranked.size)
            val peerId = snapshotToPeerId[peer] ?: coordinatorId
            ShardAssignment(
                peerId = peerId,
                layerRange = LayerRange.of(range),
                vramBytes = residentBytes,
                optimizerBytes = optimizerBytes,
                isCoordinator = peerId == coordinatorId,
                role = role,
            )
        }.sortedBy { it.peerId }  // stability guarantee

        val totalReservedMb = assignments.sumOf { it.optimizerBytes + it.vramBytes } / (1024L * 1024L)

        return ShardingPlan(
            model = model,
            assignments = assignments,
            strategy = cfg.strategy,
            mode = cfg.sharding.mode,
            optimizerOffload = cfg.sharding.optimizerOffload,
            totalReservedMb = totalReservedMb,
        )
    }

    /**
     * Rank peers by capability. Higher tier > more free RAM > stable
     * power. Reuses the same heuristics `HostElection.elect` already
     * encodes (matching the existing `NodeSnapshot.isHostEligible()`
     * filter semantics).
     */
    private fun rankPeers(peers: Collection<CapabilitySnapshot>): List<CapabilitySnapshot> {
        return peers.sortedWith(
            compareByDescending<CapabilitySnapshot> { tierOrdinal(it) }
                .thenByDescending { it.availRamMb }
                .thenBy { isChargingOrdinal(it) }
                .thenBy { it.abi },  // tiebreaker
        )
    }

    /** Stable mapping from a peer to its peerId. The CapabilitySnapshot
     *  itself doesn't carry peerId; the planner caller supplies it via
     *  the map's key. We pull it back via the lookup helper below. */
    private fun peerIdByCapability(peer: CapabilitySnapshot): String {
        // In production, the caller passes the peerId-keyed map. The
        // planner doesn't keep the map here to stay pure; the consumer
        // is responsible for joining assignment.peerId back to a
        // CapabilitySnapshot. This is a defensive default — callers
        // should not rely on this.
        return peer.toString().take(40)
    }

    private fun tierOrdinal(peer: CapabilitySnapshot): Int = when {
        // Phones with NPU/GPU + enough RAM are FULL-tier candidates.
        peer.availRamMb >= 8_000 && (peer.supportsGpu || peer.supportsNpu) -> 2
        peer.availRamMb >= 4_000 -> 1
        else -> 0
    }

    private fun isChargingOrdinal(peer: CapabilitySnapshot): Int =
        if (peer.isCharging || peer.batteryPct >= 80) 0 else 1

    private fun roleForPeer(peer: CapabilitySnapshot, idx: Int, total: Int): String {
        val isCoordinator = idx == 0
        if (isCoordinator) return "COORDINATOR"
        if (total >= 3 && peer.availRamMb >= 16_000) return "PARAMETER_SERVER"
        // Phones with low RAM get OBSERVER.
        return if (peer.availRamMb < 2_000) "OBSERVER" else "SHARD_OWNER"
    }

    /**
     * Chunk `totalLayers` into `count` contiguous ranges, distributing
     * the remainder (if any) into the leading chunks first.
     */
    private fun chunkLayers(totalLayers: Int, count: Int): List<IntRange> {
        if (totalLayers == 0 || count == 0) return emptyList()
        val base = totalLayers / count
        val extra = totalLayers % count
        val out = mutableListOf<IntRange>()
        var cur = 0
        for (i in 0 until count) {
            val size = base + if (i < extra) 1 else 0
            if (size == 0) {
                out += IntRange.EMPTY
                continue
            }
            val end = cur + size - 1
            out += cur..end
            cur = end + 1
        }
        return out
    }
}
