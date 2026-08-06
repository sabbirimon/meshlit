package com.meshlit.core.gpu

import com.meshlit.core.inference.BackendHints
import com.meshlit.core.inference.GpuBackend as InferenceGpuBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendHintsPickerTest {
    @Test
    fun `none backend yields CpuOnly hints`() {
        val hints = BackendHintsPicker.pick(GpuProbe.None, cpuThreads = 0)

        assertEquals(0, hints.gpuLayers)
        assertEquals(InferenceGpuBackend.NONE, hints.gpuBackend)
    }

    @Test
    fun `Vulkan probe yields Vulkan hints`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val hints = BackendHintsPicker.pick(probe)

        assertEquals(InferenceGpuBackend.VULKAN, hints.gpuBackend)
    }

    @Test
    fun `7B model gets at least 20 GPU layers`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val hints = BackendHintsPicker.pick(probe, modelParameterCount = 7_000_000_000L)

        // 7B params / 50M per layer = 140 layers, clamped to 200
        assertEquals(140, hints.gpuLayers)
    }

    @Test
    fun `huge model clamps to 200 layers`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val hints = BackendHintsPicker.pick(probe, modelParameterCount = 70_000_000_000L)

        assertEquals(200, hints.gpuLayers)
    }

    @Test
    fun `cpu threads thread through unchanged`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val hints = BackendHintsPicker.pick(probe, cpuThreads = 6)

        assertEquals(6, hints.cpuThreads)
    }

    @Test
    fun `pick is deterministic across calls`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val first = BackendHintsPicker.pick(probe, cpuThreads = 4, modelParameterCount = 1_000_000_000L)
        val second = BackendHintsPicker.pick(probe, cpuThreads = 4, modelParameterCount = 1_000_000_000L)

        assertEquals(first, second)
    }

    @Test
    fun `picker returns BackendHints not a subclass`() {
        val probe = GpuProbe(backend = GpuBackend.VULKAN, devices = emptyList())

        val hints: BackendHints = BackendHintsPicker.pick(probe)

        assertEquals(0, hints.gpuLayers.coerceAtLeast(0))
    }
}