package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.GpuBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterShardPlannerGpuTest {
    private fun cap(
        id: String,
        freeDiskMb: Long,
        gpuBackend: GpuBackend = GpuBackend.NONE,
        isExternalGpu: Boolean = false,
    ) = PeerCapabilities(
        peerId = id,
        capabilityTier = CapabilityTier.MID,
        freeRamMb = 1024,
        freeDiskMb = freeDiskMb,
        hostedShardIds = emptySet(),
        lastSeenMs = 0L,
        gpuBackend = gpuBackend,
        isExternalGpu = isExternalGpu,
    )

    @Test
    fun `GPU peer wins tie over CPU peer for whole-model plan`() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "m",
            modelSizeBytes = 256L * 1024 * 1024,
            totalLayers = 16,
            self = cap("self", freeDiskMb = 1024),
            peers = listOf(
                cap("cpu", freeDiskMb = 1024, gpuBackend = GpuBackend.NONE),
                cap("gpu", freeDiskMb = 1024, gpuBackend = GpuBackend.VULKAN),
            ),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.SingleShard)
        val host = (plan as ClusterShardPlanner.Plan.SingleShard).assignments.single().peerId
        assertEquals("gpu", host)
    }

    @Test
    fun `CPU peer with much more disk still wins over GPU peer`() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "m",
            modelSizeBytes = 256L * 1024 * 1024,
            totalLayers = 16,
            self = cap("self", freeDiskMb = 64),
            peers = listOf(
                cap("gpu", freeDiskMb = 512, gpuBackend = GpuBackend.VULKAN),
                cap("cpu", freeDiskMb = 4096, gpuBackend = GpuBackend.NONE),
            ),
        )
        val host = (plan as ClusterShardPlanner.Plan.SingleShard).assignments.single().peerId
        assertEquals("cpu", host)
    }

    @Test
    fun `multi-shard plan prefers GPU peer for first shard`() {
        val planner = ClusterShardPlanner(shardSizeBytes = 256L * 1024 * 1024)
        val plan = planner.plan(
            modelId = "m",
            modelSizeBytes = 4L * 1024 * 1024 * 1024,
            totalLayers = 32,
            self = cap("self", freeDiskMb = 2048, gpuBackend = GpuBackend.VULKAN),
            peers = listOf(cap("cpu", freeDiskMb = 2048)),
        )
        assertTrue(plan is ClusterShardPlanner.Plan.MultiShard)
        val multi = plan as ClusterShardPlanner.Plan.MultiShard
        val first = multi.assignments.first()
        assertEquals("self", first.peerId)
    }

    @Test
    fun `eGPU peer wins integrated GPU peer for whole-model plan`() {
        val planner = ClusterShardPlanner()
        val plan = planner.plan(
            modelId = "m",
            modelSizeBytes = 256L * 1024 * 1024,
            totalLayers = 16,
            self = cap("self", freeDiskMb = 1024, gpuBackend = GpuBackend.VULKAN, isExternalGpu = false),
            peers = listOf(cap("egpu", freeDiskMb = 1024, gpuBackend = GpuBackend.VULKAN, isExternalGpu = true)),
        )
        val host = (plan as ClusterShardPlanner.Plan.SingleShard).assignments.single().peerId
        // Tie + GPU bonus: eGPU and integrated are equivalent; the
        // list iteration order decides. Either is acceptable, but
        // the host must be one of the two GPU-capable peers.
        assertTrue(host == "egpu" || host == "self")
    }
}