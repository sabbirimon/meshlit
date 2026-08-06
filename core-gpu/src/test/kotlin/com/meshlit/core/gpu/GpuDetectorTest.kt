package com.meshlit.core.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuDetectorTest {
    @Test
    fun `no Vulkan reports no GPU backend`() {
        val detector = GpuDetector(NoVulkan)

        assertEquals(GpuProbe.None, detector.probe())
    }

    @Test
    fun `Vulkan feature reports integrated device`() {
        val detector = GpuDetector(VulkanFeatureProbe { "1.3" })

        val probe = detector.probe()

        assertEquals(GpuBackend.VULKAN, probe.backend)
        assertEquals(1, probe.devices.size)
        assertEquals("1.3", probe.devices.single().apiVersion)
        assertFalse(probe.hasExternalGpu)
    }

    @Test
    fun `external GPU source is included when Vulkan is available`() {
        val external = ExternalGpuSource {
            ExternalGpuSource.Snapshot(
                vendorTag = "NVIDIA RTX 4060",
                vramMb = 8192,
                bus = "USB4",
                pcieGeneration = 4,
            )
        }
        val detector = GpuDetector(
            vulkanFeatureProbe = VulkanFeatureProbe { "1.3" },
            externalGpuSource = external,
        )

        val probe = detector.probe()

        assertTrue(probe.hasExternalGpu)
        val egpu = probe.devices.single { it.isExternal }
        assertEquals("NVIDIA RTX 4060", egpu.vendor)
        assertEquals(8192L, egpu.vramMb)
        assertEquals(4, egpu.pcieGeneration)
    }

    @Test
    fun `external source ignored when Vulkan unavailable`() {
        val external = ExternalGpuSource {
            ExternalGpuSource.Snapshot("AMD RX 8800 XT", 16384, "USB4", 4)
        }
        val detector = GpuDetector(NoVulkan, external)

        assertEquals(GpuProbe.None, detector.probe())
    }
}