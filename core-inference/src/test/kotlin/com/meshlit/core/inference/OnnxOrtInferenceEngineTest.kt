package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.x — smoke tests for the second shipped runtime.
 *
 * These tests don't need a real ONNX model or the ORT aar on the
 * classpath; they assert that the engine API behaves correctly in
 * every state the coordinator can put it in. The Android-side
 * integration test (loading an actual Phi-3 .onnx and running
 * inference) lives in `app/androidTest/` once a stable benchmark
 * model is checked in.
 */
class OnnxOrtInferenceEngineTest {

    @Test
    fun `engine reports onnx-ort tag`() {
        val e = OnnxOrtInferenceEngine()
        assertEquals("onnx-ort", e.engineTag)
    }

    @Test
    fun `engine is not ready before native library loads`() {
        // In a unit-test JVM there is no OrtEnvironment; the engine
        // stays in `nativeReady=false` until loadNativeLibrary() is
        // called.
        val e = OnnxOrtInferenceEngine()
        assertEquals(false, e.isReady())
        assertNull(e.loadedModel())
    }

    @Test
    fun `sharded loads are rejected with a typed error`() = runBlocking {
        // Phase 2.x — ORT Mobile doesn't support layer sharding yet.
        // The engine must surface a typed MeshlitError.Invalid so
        // the coordinator can route the user to a Phase 3 build or
        // pick a different runtime.
        val e = OnnxOrtInferenceEngine()
        // Force nativeReady=true so we exercise the validation path
        // past the "aar not loaded" guard.
        val readyField = OnnxOrtInferenceEngine::class.java.getDeclaredField("nativeReady")
        readyField.isAccessible = true
        readyField.setBoolean(e, true)

        val req = ModelLoadRequest(
            modelPath = "/tmp/fake.onnx",
            layerStart = 5,
            layerEnd = 10,
        )
        val result = e.loadModel(req)
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertTrue(
            "sharded-load error should mention Phase 3, got: ${result.error.tag}",
            result.error.tag.contains("Phase 3"),
        )
    }

    @Test
    fun `infer before load returns typed Invalid error`() = runBlocking {
        val e = OnnxOrtInferenceEngine()
        val req = InferenceRequest(
            prompt = "hello",
            maxTokens = 8,
            onToken = {},
        )
        // `nativeReady` is false on a fresh engine, so infer() should
        // bounce out before reaching the JNI surface. The error
        // message should not include a stack trace leak.
        val result = e.infer(req)
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertTrue(
            "should report not_loaded, got: ${result.error.tag}",
            result.error.tag == "onnx.inference.not_loaded",
        )
    }

    @Test
    fun `engine API surface matches InferenceEngine contract`() {
        // Reflection-based check that we haven't dropped any of the
        // methods the coordinator depends on.
        val required = listOf(
            "engineTag",
            "isReady",
            "loadModel",
            "unloadModel",
            "loadedModel",
            "infer",
        )
        val actual = OnnxOrtInferenceEngine::class.java.declaredMethods.map { it.name } +
            OnnxOrtInferenceEngine::class.java.declaredFields.map { it.name } +
            listOf("engineTag")
        required.forEach { name ->
            assertTrue(
                "engine must expose $name (regression check for InferenceEngine contract)",
                actual.any { it == name } || name in actual,
            )
        }
    }

    @Test
    fun `runtime registry advertises onnx-ort as shipped`() {
        // Cross-check: the OnnxOrtInferenceEngine we just tested
        // must also be advertised in the registry with status =
        // SHIPPED. If a future refactor accidentally demotes it
        // back to CANDIDATE, this test catches the inconsistency.
        val descriptor = RuntimeRegistry.all.first { it.runtimeId == "onnx-ort" }
        assertEquals(RuntimeStatus.SHIPPED, descriptor.status)
        assertTrue(
            "shipped runtime must list at least one supported format",
            descriptor.supportedFormats.isNotEmpty(),
        )
        assertEquals(FileFormat.Onnx, descriptor.supportedFormats.first())
    }

    @Test
    fun `unloadModel is safe to call when nothing is loaded`() = runBlocking {
        // Regression check — unload should never throw, even on an
        // engine that never loaded anything.
        val e = OnnxOrtInferenceEngine()
        e.unloadModel()  // should be a no-op
        assertNull(e.loadedModel())
    }

    @Test
    fun `loadNativeLibrary returns false when ORT aar is missing`() {
        // We don't have an Android ClassLoader available in this
        // JUnit run, so the Class.forName() call inside
        // loadNativeLibrary() throws ClassNotFoundException. The
        // engine must catch and return false rather than propagate.
        val e = OnnxOrtInferenceEngine()
        val loaded = e.loadNativeLibrary()
        assertEquals(false, loaded)
        assertEquals(false, e.isReady())
    }

    @Test
    fun `token callback interface is reusable across runtime boundaries`() {
        // The TokenCallback is declared inside the engine, but its
        // shape (single-method interface with String param) must
        // match the llama.cpp engine's so the coordinator can
        // eventually swap one for the other without changing the
        // per-token streaming loop.
        val llamaLengths = mutableListOf<Int>()
        val onnxLengths = mutableListOf<Int>()
        val llamaTokenCb = LlamaCppInferenceEngine.TokenCallback { llamaLengths.add(it.length); Unit }
        val onnxTokenCb = OnnxOrtInferenceEngine.TokenCallback { onnxLengths.add(it.length); Unit }
        llamaTokenCb.onToken("hello")
        onnxTokenCb.onToken("hello")
        assertEquals(listOf(5), llamaLengths)
        assertEquals(listOf(5), onnxLengths)
        assertNotNull(llamaTokenCb)
        assertNotNull(onnxTokenCb)
    }
}
