package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.GpuBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerCapabilitiesGpuTest {
    @Test
    fun `defaults to CPU and no VRAM`() {
        val caps = PeerCapabilities(
            peerId = "p",
            capabilityTier = CapabilityTier.MID,
            freeRamMb = 1024,
            freeDiskMb = 1024,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
        )

        assertEquals(GpuBackend.NONE, caps.gpuBackend)
        assertEquals(0L, caps.vramMb)
        assertFalse(caps.isExternalGpu)
        assertFalse(caps.hasGpu)
        assertFalse(caps.isEgpu)
    }

    @Test
    fun `integrated Vulkan peer is gpu but not egpu`() {
        val caps = PeerCapabilities(
            peerId = "p",
            capabilityTier = CapabilityTier.FULL,
            freeRamMb = 8192,
            freeDiskMb = 4096,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
            gpuBackend = GpuBackend.VULKAN,
        )

        assertTrue(caps.hasGpu)
        assertFalse(caps.isEgpu)
    }

    @Test
    fun `external Vulkan peer is both gpu and egpu`() {
        val caps = PeerCapabilities(
            peerId = "p",
            capabilityTier = CapabilityTier.FULL,
            freeRamMb = 16384,
            freeDiskMb = 8192,
            hostedShardIds = emptySet(),
            lastSeenMs = 0L,
            gpuBackend = GpuBackend.VULKAN,
            vramMb = 8192L,
            isExternalGpu = true,
        )

        assertTrue(caps.hasGpu)
        assertTrue(caps.isEgpu)
    }
}