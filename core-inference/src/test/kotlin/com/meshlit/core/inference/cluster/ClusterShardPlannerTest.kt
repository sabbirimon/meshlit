package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterShardPlannerTest {

    private fun cap(
        id: String,
        freeRamMb: Long = 1024,
        freeDiskMb: Long = 1024,
        tier: CapabilityTier = CapabilityTier.MID,
        hosted: Set<String> = emptySet(),
    ) = PeerCapabilities(
        peerId = id,
        capabilityTier = tier,
        freeRamMb = freeRamMb,
        freeDiskMb = freeDiskMb,
        hostedShardIds = hosted,
        lastSeenMs = 0L,
    )

    @Test fun picks_single_shard_when_one_peer_has_disk() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "model",
            modelSizeBytes = 500L * 1024 * 1024,
            totalLayers = 24,
            self = cap("self", freeDiskMb = 100),
            peers = listOf(cap("p1", freeDiskMb = 4096)),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.SingleShard)
        val assignments = (plan as ClusterShardPlanner.Plan.SingleShard).assignments
        assertEquals(1, assignments.size)
        assertEquals("p1", assignments.first().peerId)
        assertEquals(0, assignments.first().layerStart)
        assertEquals(24, assignments.first().layerEnd)
    }

    @Test fun falls_back_when_no_peer_has_enough_disk() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "huge",
            modelSizeBytes = 4L * 1024 * 1024 * 1024,
            totalLayers = 64,
            self = cap("self", freeDiskMb = 256),
            peers = listOf(
                cap("p1", freeDiskMb = 256),
                cap("p2", freeDiskMb = 256),
            ),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.FallbackToWholeModel)
    }

    @Test fun multi_shard_when_cluster_total_meets_requirement() {
        val planner = ClusterShardPlanner(shardSizeBytes = 512L * 1024 * 1024)
        val plan = planner.plan(
            modelId = "split",
            modelSizeBytes = 4L * 1024 * 1024 * 1024,
            totalLayers = 32,
            self = cap("self", freeDiskMb = 2048),
            peers = listOf(cap("p1", freeDiskMb = 2048)),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.MultiShard)
        val multi = plan as ClusterShardPlanner.Plan.MultiShard
        // 4 GiB / 512 MiB = 8 shards, capped by totalLayers=32 → 8 shards.
        assertEquals(8, multi.assignments.size)
        // All layers covered exactly once.
        val totalLayersCovered = multi.assignments.sumOf { it.layerEnd - it.layerStart }
        assertEquals(32, totalLayersCovered)
    }

    @Test fun planner_is_deterministic_for_same_inputs() {
        val planner = ClusterShardPlanner(shardSizeBytes = 256L * 1024 * 1024)
        val peers = listOf(cap("p1", freeDiskMb = 2048), cap("p2", freeDiskMb = 2048))
        val self = cap("self", freeDiskMb = 64)
        val plan1 = planner.plan("det", 2L * 1024 * 1024 * 1024, 16, self, peers)
        val plan2 = planner.plan("det", 2L * 1024 * 1024 * 1024, 16, self, peers)
        assertEquals(plan1.toString(), plan2.toString())
    }

    @Test fun sticky_assignment_preserves_existing_host() {
        // Policy: when any single peer has enough disk for the whole
        // model the planner picks SingleShard and "stickiness" is a
        // no-op (nothing to preserve). To exercise the sticky path
        // we size the model so no peer can host it alone, yet the
        // cluster total exceeds the model — only then does the
        // planner enter MultiShard and honor hostedShardIds.
        val planner = ClusterShardPlanner(shardSizeBytes = 256L * 1024 * 1024)
        val stickySet = setOf("sticky/shard-001", "sticky/shard-003")
        val plan = planner.plan(
            modelId = "sticky",
            modelSizeBytes = 3L * 1024 * 1024 * 1024, // 3 GiB
            totalLayers = 12,
            self = cap("self", freeDiskMb = 2048, hosted = stickySet),
            peers = listOf(cap("p1", freeDiskMb = 2048)),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.MultiShard)
        val assignments = (plan as ClusterShardPlanner.Plan.MultiShard).assignments
        // 3 GiB / 256 MiB = 12 shards, capped by totalLayers=12.
        assertEquals(12, assignments.size)
        val sticky = assignments.filter { it.peerId == "self" }.map { it.shardId }.toSet()
        // Self keeps at least one of its previous shards.
        assertTrue(sticky.contains("shard-001") || sticky.contains("shard-003"))
    }

    @Test fun empty_roster_falls_back() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "lonely",
            modelSizeBytes = 1024L,
            totalLayers = 1,
            self = cap("self", freeDiskMb = 0),
            peers = emptyList(),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.FallbackToWholeModel)
    }

    @Test fun shard_assignment_to_shard_spec_round_trips() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "rt",
            modelSizeBytes = 1024L,
            totalLayers = 4,
            self = cap("self", freeDiskMb = 8),
            peers = emptyList(),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.SingleShard)
        val a = (plan as ClusterShardPlanner.Plan.SingleShard).assignments.first()
        val spec = a.toShardSpec(CapabilityTier.MID, estimatedRamMb = 64)
        assertEquals(a.shardId, spec.shardId)
        assertEquals(a.layerStart, spec.layerStart)
        assertEquals(a.layerEnd, spec.layerEnd)
        assertEquals(CapabilityTier.MID, spec.preferredCapabilityTier)
    }

    /**
     * Regression: a peer with `freeDiskMb = 0L` is the placeholder the
     * `ClusterStorageInstaller` falls back to when /v1/capabilities
     * fetch fails. Such peers must be filtered out before the planner
     * tries to assign any shards to them — otherwise the planner
     * would emit an assignment pointing at a peer with zero bytes free
     * and the assembler would crash on the next write.
     */
    @Test fun peer_with_zero_disk_is_filtered_out() {
        val planner = ClusterShardPlanner(shardSizeBytes = 256L * 1024 * 1024)
        val plan = planner.plan(
            modelId = "filter",
            modelSizeBytes = 1L * 1024 * 1024 * 1024,
            totalLayers = 8,
            self = cap("self", freeDiskMb = 0L),
            peers = listOf(
                cap("p1", freeDiskMb = 0L),
                cap("p2", freeDiskMb = 2048),
            ),
        )
        // Only p2 (real free disk) survives the filter; planner should
        // emit a SingleShard plan assigning the whole model to p2.
        assertTrue(plan is ClusterShardPlanner.Plan.SingleShard)
        val assignments = (plan as ClusterShardPlanner.Plan.SingleShard).assignments
        assertEquals(1, assignments.size)
        assertEquals("p2", assignments.first().peerId)
    }
}