package com.meshlit.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 — unit tests for the runtime registry. The registry is the
 * authority on which (format, runtime) pairs Meshlit can load today.
 * If these tests break, the Models screen and the coordinator's
 * runtime-resolution logic were updated inconsistently.
 */
class RuntimeRegistryTest {

    @Test
    fun `gguf path resolves to gguf-llama-cpp runtime`() {
        val path = "/data/local/tmp/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue("expected Found, got $r", r is RuntimeResolution.Found)
        r as RuntimeResolution.Found
        assertEquals("gguf-llama.cpp", r.runtime.runtimeId)
        assertEquals(FileFormat.Gguf, r.format)
    }

    @Test
    fun `onnx path resolves to candidate (not shipped)`() {
        val path = "/data/local/tmp/phi-3.5-mini.onnx"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue("expected NotShipped, got $r", r is RuntimeResolution.NotShipped)
        r as RuntimeResolution.NotShipped
        assertEquals("onnx-ort", r.runtime.runtimeId)
        assertEquals(FileFormat.Onnx, r.format)
        assertTrue(
            "NotShipped message should mention Phase 2",
            r.message.contains("Phase 2"),
        )
    }

    @Test
    fun `safetensors path resolves to candidate`() {
        val path = "/data/local/tmp/weights.safetensors"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue(r is RuntimeResolution.NotShipped)
        r as RuntimeResolution.NotShipped
        assertEquals(FileFormat.Safetensors, r.format)
    }

    @Test
    fun `tflite path resolves to candidate`() {
        val path = "/data/local/tmp/model.tflite"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue(r is RuntimeResolution.NotShipped)
        r as RuntimeResolution.NotShipped
        assertEquals(FileFormat.Tflite, r.format)
    }

    @Test
    fun `mlx path resolves to Apple-only`() {
        val path = "/data/local/tmp/model.mlx"
        val r = RuntimeRegistry.pickForPath(path)
        // MLX is Apple-only; no shippable runtime, no candidate either.
        assertTrue(r is RuntimeResolution.Unsupported)
        r as RuntimeResolution.Unsupported
        assertEquals(FileFormat.Mlx, r.format)
    }

    @Test
    fun `unknown extension resolves to UnknownFormat`() {
        val path = "/data/local/tmp/random.bin"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue(r is RuntimeResolution.UnknownFormat)
        r as RuntimeResolution.UnknownFormat
        assertNull(r.format)
    }

    @Test
    fun `case insensitive extension matching`() {
        val path = "/data/local/tmp/MODEL.GGUF"
        val r = RuntimeRegistry.pickForPath(path)
        assertTrue(r is RuntimeResolution.Found)
    }

    @Test
    fun `pickForFormat skips extension detection`() {
        val r = RuntimeRegistry.pickForFormat(FileFormat.Gguf)
        assertTrue(r is RuntimeResolution.Found)
        r as RuntimeResolution.Found
        assertEquals("gguf-llama.cpp", r.runtime.runtimeId)
    }

    @Test
    fun `registry ships exactly one runtime today`() {
        assertEquals(1, RuntimeRegistry.shippable.size)
        assertEquals("gguf-llama.cpp", RuntimeRegistry.shippable.first().runtimeId)
    }

    @Test
    fun `registry includes six candidate runtimes`() {
        val ids = RuntimeRegistry.all.map { it.runtimeId }.toSet()
        assertEquals(
            setOf(
                "gguf-llama.cpp",
                "onnx-ort",
                "safetensors-candle",
                "tflite-litert",
                "mlx-apple",
                "coreml-apple",
            ),
            ids,
        )
    }

    @Test
    fun `summary reports shipped and candidate counts`() {
        val s = RuntimeRegistry.summary()
        assertEquals(1, s.shippedCount)
        assertEquals(3, s.candidateCount)  // onnx-ort, safetensors-candle, tflite-litert
        assertEquals(2, s.appleOnlyCount)  // mlx-apple, coreml-apple
        assertTrue("shipped APK footprint should be ≥ 12 MB", s.shippedBytes >= 12L * 1024L * 1024L)
        assertTrue("candidate APK footprint should be ≥ 17 MB", s.candidateBytes >= 17L * 1024L * 1024L)
    }

    @Test
    fun `each runtime exposes a stable runtimeId`() {
        val seen = mutableSetOf<String>()
        RuntimeRegistry.all.forEach { rt ->
            assertTrue("runtimeId must be non-empty", rt.runtimeId.isNotEmpty())
            assertTrue("runtimeId must be unique", seen.add(rt.runtimeId))
            assertTrue("displayName must be non-empty", rt.displayName.isNotEmpty())
            assertNotNull(rt.supportedFormats)
            assertTrue("supportedFormats must be non-empty", rt.supportedFormats.isNotEmpty())
        }
    }
}
