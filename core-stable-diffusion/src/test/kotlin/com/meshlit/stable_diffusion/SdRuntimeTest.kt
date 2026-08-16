package com.meshlit.stable_diffusion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SdRuntime] — the runtime enum that maps the
 * persisted DataStore key to the engine tag the UI renders.
 *
 * Phase 4.x — these guard the most-edited file in the SD
 * pipeline. If they break, the LocalSdModelCard runtime picker
 * and the SdEngineRouter dispatch table drifted apart.
 */
class SdRuntimeTest {

    @Test
    fun `fromKey returns the matching runtime for every known key`() {
        for (r in SdRuntime.entries) {
            assertEquals(r, SdRuntime.fromKey(r.key))
        }
    }

    @Test
    fun `fromKey returns default for null and unknown values`() {
        assertEquals(SdRuntime.default, SdRuntime.fromKey(null))
        assertEquals(SdRuntime.default, SdRuntime.fromKey(""))
        assertEquals(SdRuntime.default, SdRuntime.fromKey("garbage"))
        // Sanity: the default really is Stub.
        assertEquals(SdRuntime.Stub, SdRuntime.default)
    }

    @Test
    fun `engine tags are stable for telemetry`() {
        // These strings are persisted in SettingsRepository and
        // emitted to logs / telemetry. Changing them breaks the
        // on-disk schema.
        assertEquals("sd-stub", SdRuntime.Stub.engineTag)
        assertEquals("sd.cpp-gguf", SdRuntime.StableDiffusionCpp.engineTag)
        assertEquals("onnx-ort", SdRuntime.OnnxRuntime.engineTag)
        assertEquals("diffusers-py", SdRuntime.DiffusersPython.engineTag)
        assertEquals("executorch-pte", SdRuntime.ExecuTorch.engineTag)
    }

    @Test
    fun `keys match the persisted DataStore schema`() {
        // These strings are what the user has on disk. Changing
        // them forces a migration step.
        assertEquals("stub", SdRuntime.Stub.key)
        assertEquals("sd.cpp", SdRuntime.StableDiffusionCpp.key)
        assertEquals("onnx", SdRuntime.OnnxRuntime.key)
        assertEquals("diffusers", SdRuntime.DiffusersPython.key)
        assertEquals("executorch", SdRuntime.ExecuTorch.key)
    }
}