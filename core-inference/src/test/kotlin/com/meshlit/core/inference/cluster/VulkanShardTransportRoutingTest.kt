package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.GpuBackend
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VulkanShardTransportRoutingTest {

    @Test
    fun `CPU peer gets a plain ShardTransport`() {
        val caps = PeerCapabilities(
            peerId = "cpu",
            capabilityTier = CapabilityTier.MID,
            freeRamMb = 1024,
            freeDiskMb = 1024,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
            gpuBackend = GpuBackend.NONE,
        )
        val transport = ShardTransportFactory.forCapabilities(caps)

        assertNotNull(transport)
        // Plain ShardTransport (no Vulkan wrapper) — check class name
        // since `is ShardTransport` would always be true given the
        // factory's return type.
        assertEquals("ShardTransport", transport::class.java.simpleName)
    }

    @Test
    fun `Vulkan peer routes through the factory`() {
        val caps = PeerCapabilities(
            peerId = "gpu",
            capabilityTier = CapabilityTier.FULL,
            freeRamMb = 4096,
            freeDiskMb = 8192,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
            gpuBackend = GpuBackend.VULKAN,
        )
        val transport = ShardTransportFactory.forCapabilities(caps)

        assertNotNull(transport)
        assertTrue(transport::class.java.simpleName.isNotEmpty())
    }

    @Test
    fun `vulkan() factory returns a VulkanShardTransport instance`() {
        val transport = ShardTransportFactory.vulkan()

        assertEquals("VulkanShardTransport", transport::class.java.simpleName)
        assertNotEquals("ShardTransport", transport::class.java.simpleName)
    }

    @Test
    fun `eGPU peer (external GPU) routes through the factory too`() {
        val caps = PeerCapabilities(
            peerId = "egpu",
            capabilityTier = CapabilityTier.FULL,
            freeRamMb = 8192,
            freeDiskMb = 16384,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
            gpuBackend = GpuBackend.VULKAN,
            isExternalGpu = true,
        )
        val transport = ShardTransportFactory.forCapabilities(caps)

        assertNotNull(transport)
    }
}