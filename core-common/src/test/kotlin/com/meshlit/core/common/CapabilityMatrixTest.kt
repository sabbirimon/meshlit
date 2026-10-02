package com.meshlit.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test for [CapabilityMatrix] + its companion enums.
 *
 * This test exists primarily to gate [CapabilityMatrix] in CI (Slice 1 deliverable:
 * `:core-common:testDebugUnitTest`). It is not a substitute for the integration
 * tests in `:core-inference` — those exercise the wire / coordinator path.
 */
class CapabilityMatrixTest {

    @Test
    fun `NONE has no capabilities`() {
        val m = CapabilityMatrix.NONE
        assertEquals(0, m.androidApi)
        assertTrue(m.abis.isEmpty())
        assertTrue(m.formats.isEmpty())
        assertTrue(m.quants.isEmpty())
        assertEquals(0, m.contextLimit)
        assertEquals(RamTier.LIGHT, m.ramTier)
        assertFalse(m.gpu.isAvailable)
        assertEquals(NpuAvailability.UNAVAILABLE, m.npu)
        // NONE supports nothing.
        assertFalse(m.supports(FileFormat.GGUF, Quantization.Q8_0, 128))
    }

    @Test
    fun `tier derives from androidApi via CapabilityTier`() {
        val m = CapabilityMatrix.NONE.copy(androidApi = 35)
        // Whatever CapabilityTier maps SDK 35 to — make sure we get a real value, not a default.
        assertNotNull(m.tier)
        // The summary should round-trip the api + tier.
        assertTrue(m.summary().contains("API 35"))
    }

    @Test
    fun `supports honours every field`() {
        val m = CapabilityMatrix(
            androidApi = 35,
            abis = setOf(Abi.ARM64_V8A),
            ramTier = RamTier.HIGH,
            gpu = GpuBackendAvailability.VULKAN,
            npu = NpuAvailability.AVAILABLE,
            formats = setOf(FileFormat.GGUF),
            contextLimit = 4096,
            quants = setOf(Quantization.Q8_0, Quantization.Q4_K_M),
        )
        assertTrue(m.supports(FileFormat.GGUF, Quantization.Q8_0, 4096))
        assertFalse(m.supports(FileFormat.GGUF, Quantization.Q8_0, 4097))
        assertFalse(m.supports(FileFormat.ONNX, Quantization.Q8_0, 128))
        assertFalse(m.supports(FileFormat.GGUF, Quantization.Q2_K, 128))
        assertFalse(m.gpu == GpuBackendAvailability.NONE) // sanity: this matrix has GPU
    }

    @Test
    fun `Abi fromBuildAbis parses the canonical set`() {
        val parsed = Abi.fromBuildAbis(arrayOf("arm64-v8a", "x86_64", "made-up"))
        assertEquals(setOf(Abi.ARM64_V8A, Abi.X86_64), parsed)
    }

    @Test
    fun `RamTier thresholds match DEVICE_COMPATIBILITY doc`() {
        // Don't tighten these without updating docs/DEVICE_COMPATIBILITY.md.
        assertEquals(RamTier.LIGHT, RamTier.fromTotalRamGb(1))
        assertEquals(RamTier.MID, RamTier.fromTotalRamGb(4))
        assertEquals(RamTier.HIGH, RamTier.fromTotalRamGb(6))
        assertEquals(RamTier.FRONTIER, RamTier.fromTotalRamGb(8))
        assertEquals(RamTier.FRONTIER, RamTier.fromTotalRamGb(16))
    }

    @Test
    fun `FileFormat fromPath detects by extension`() {
        assertEquals(FileFormat.GGUF, FileFormat.fromPath("/sdcard/models/foo.gguf"))
        assertEquals(FileFormat.ONNX, FileFormat.fromPath("/sdcard/models/foo.ONNX"))
        assertEquals(FileFormat.SAFETENSORS, FileFormat.fromPath("/sdcard/models/foo.safetensors"))
        assertNull(FileFormat.fromPath("/sdcard/models/foo.bin"))
        assertNull(FileFormat.fromPath(""))
    }

    @Test
    fun `GpuBackendAvailability isAvailable excludes NONE`() {
        assertFalse(GpuBackendAvailability.NONE.isAvailable)
        assertTrue(GpuBackendAvailability.VULKAN.isAvailable)
        assertTrue(GpuBackendAvailability.OPENCL.isAvailable)
    }

    @Test
    fun `Quantization STANDARD_GGUF contains the four canonical quants`() {
        val standard = Quantization.STANDARD_GGUF
        assertTrue(Quantization.Q8_0 in standard)
        assertTrue(Quantization.Q4_K_M in standard)
        assertTrue(Quantization.Q5_K_M in standard)
        assertTrue(Quantization.Q6_K in standard)
        assertEquals(4, standard.size)
    }

    @Test
    fun `summary round-trips every field as a string`() {
        val m = CapabilityMatrix(
            androidApi = 35,
            abis = setOf(Abi.ARM64_V8A, Abi.X86_64),
            ramTier = RamTier.HIGH,
            gpu = GpuBackendAvailability.VULKAN,
            npu = NpuAvailability.AVAILABLE,
            formats = setOf(FileFormat.GGUF, FileFormat.ONNX),
            contextLimit = 8192,
            quants = Quantization.STANDARD_GGUF,
        )
        val s = m.summary()
        assertTrue("missing api", s.contains("API 35"))
        assertTrue("missing ABI", s.contains("arm64-v8a"))
        assertTrue("missing x86_64 ABI", s.contains("x86_64"))
        assertTrue("missing RAM", s.contains("HIGH"))
        assertTrue("missing GPU", s.contains("VULKAN"))
        assertTrue("missing NPU", s.contains("AVAILABLE"))
        assertTrue("missing GGUF format", s.contains("gguf"))
        assertTrue("missing Q8_0 quant", s.contains("Q8_0"))
        assertTrue("missing context", s.contains("8192"))
    }
}
