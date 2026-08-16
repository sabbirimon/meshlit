package com.meshlit.core.training.plan

import com.meshlit.core.training.config.DistributedConfig
import kotlinx.serialization.Serializable

/**
 * The output of [ShardingPlanner.compute]. Serializable so it can
 * ride the same wire envelope as `ClusterCoordinator` already uses
 * for plans (no new network surface).
 */
@Serializable
data class ShardingPlan(
    val model: ModelSpec,
    val assignments: List<ShardAssignment>,
    val strategy: DistributedConfig.Strategy,
    val mode: DistributedConfig.Sharding.Mode,
    val optimizerOffload: DistributedConfig.Sharding.Offload,
    /** Total bytes the plan reserves across all peers. Useful for
     *  the UI to display "this needs 4 GB of free RAM total". */
    val totalReservedMb: Long,
) {
    /** Stability guarantee: same input → same output. The plan is a
     *  `data class` and the assignments list is sorted by peerId at
     *  compute-time so iteration order is stable across launches. */
    fun findAssignmentForPeer(peerId: String): ShardAssignment? =
        assignments.firstOrNull { it.peerId == peerId }
}

/**
 * One peer's slice of the model. The fields are intentionally explicit:
 *  - [layerRange]: which decoder layers live on this peer
 *  - [vramBytes]: per-step activation budget
 *  - [optimizerBytes]: optimizer-state budget (AdamW: 2 floats per
 *    parameter)
 *  - [isCoordinator]: true iff this peer is the elected coordinator
 *  - [role]: training-aware role (SHARD_OWNER / PARAMETER_SERVER / OBSERVER)
 *
 * [layerRange] is serialized as `LayerRange` (start + endInclusive)
 * because the built-in `IntRange` does not have a built-in serializer.
 */
@Serializable
data class ShardAssignment(
    val peerId: String,
    val layerRange: LayerRange,
    val vramBytes: Long,
    val optimizerBytes: Long,
    val isCoordinator: Boolean,
    val role: String,
)

/**
 * Serializable form of an [IntRange]. The two fields are redundant
 * for empty ranges (where start > end) — callers MUST check
 * [isEmpty] before iterating.
 */
@Serializable
data class LayerRange(
    val start: Int,
    val endInclusive: Int,
) {
    val isEmpty: Boolean get() = start > endInclusive
    val size: Int get() = if (isEmpty) 0 else (endInclusive - start + 1)

    fun toIntRange(): IntRange = if (isEmpty) IntRange.EMPTY else start..endInclusive

    companion object {
        fun of(range: IntRange): LayerRange =
            if (range.isEmpty()) LayerRange(0, -1) else LayerRange(range.first, range.last)
    }
}
