package com.meshlit.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sanitizer tests for the Stable Diffusion settings introduced
 * in Phase 4.x. The full SettingsRepository is paired to a
 * DataStore <-> Context, which a unit test can't stand up
 * cleanly; the pure helpers in `companion object` are the
 * contract that the Flow / setter wiring depends on, so we
 * pin them here.
 */
class SettingsRepositoryImageGenFlowsTest {

    @Test
    fun `sanitizeSdRuntime accepts every known key`() {
        for (key in listOf("stub", "sd.cpp", "onnx", "diffusers", "executorch")) {
            assertEquals(key, SettingsRepository.sanitizeSdRuntime(key))
        }
    }

    @Test
    fun `sanitizeSdRuntime falls back to stub for unknown and null inputs`() {
        assertEquals("stub", SettingsRepository.sanitizeSdRuntime("garbage"))
        assertEquals("stub", SettingsRepository.sanitizeSdRuntime(""))
        assertEquals("stub", SettingsRepository.sanitizeSdRuntime(null))
    }

    @Test
    fun `clampSdThreads coerces to 1 to 8 inclusive`() {
        assertEquals(1, SettingsRepository.clampSdThreads(0))
        assertEquals(1, SettingsRepository.clampSdThreads(-3))
        assertEquals(1, SettingsRepository.clampSdThreads(1))
        assertEquals(4, SettingsRepository.clampSdThreads(4))
        assertEquals(8, SettingsRepository.clampSdThreads(8))
        assertEquals(8, SettingsRepository.clampSdThreads(99))
    }

    @Test
    fun `clampSdGpuLayers coerces to 0 to 99 inclusive`() {
        assertEquals(0, SettingsRepository.clampSdGpuLayers(-5))
        assertEquals(0, SettingsRepository.clampSdGpuLayers(0))
        assertEquals(50, SettingsRepository.clampSdGpuLayers(50))
        assertEquals(99, SettingsRepository.clampSdGpuLayers(99))
        assertEquals(99, SettingsRepository.clampSdGpuLayers(500))
    }

    @Test
    fun `sanitizeSdPath accepts blank paths and the app sandbox`() {
        val sandbox = "/data/data/com.meshlit/files"
        assertEquals("", SettingsRepository.sanitizeSdPath("", sandbox))
        assertEquals(
            "$sandbox/imported-models/onediff.ckpt",
            SettingsRepository.sanitizeSdPath("$sandbox/imported-models/onediff.ckpt", sandbox),
        )
        assertEquals(
            "/data/data/com.meshlit/legacy/legacy.ckpt",
            SettingsRepository.sanitizeSdPath("/data/data/com.meshlit/legacy/legacy.ckpt", sandbox),
        )
    }

    @Test
    fun `sanitizeSdPath rejects paths outside the sandbox`() {
        val sandbox = "/data/data/com.meshlit/files"
        assertEquals("", SettingsRepository.sanitizeSdPath("/sdcard/foo.gguf", sandbox))
        assertEquals("", SettingsRepository.sanitizeSdPath("/data/data/other.app/foo", sandbox))
        // The legacy `/data/data/com.meshlit` root check is
        // intentionally permissive — a sibling like
        // `/data/data/com.meshlit/files2` is trusted because it
        // lives under the same package. If we ever need to lock
        // this down to `context.filesDir` only, the change is a
        // single line in `SettingsRepository.sanitizeSdPath`.
        assertEquals(
            "$sandbox2/foo",
            SettingsRepository.sanitizeSdPath("$sandbox2/foo", sandbox),
        )
    }

    private val sandbox2: String get() = "/data/data/com.meshlit/files2"
}
