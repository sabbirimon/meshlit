package com.meshlit.stable_diffusion

import android.content.Context
import android.content.ContextWrapper
import com.meshlit.stable_diffusion.engines.DiffusersEngine
import com.meshlit.stable_diffusion.engines.ExecuTorchEngine
import com.meshlit.stable_diffusion.engines.OnnxSdEngine
import com.meshlit.stable_diffusion.engines.SdCppEngine
import com.meshlit.stable_diffusion.engines.StubSdEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SdEngineRouter]. Verifies the persisted runtime
 * key maps to the matching engine implementation and that
 * `pickForKey` honours an explicit override without touching
 * DataStore.
 *
 * The SdCppEngine branch needs a real Android Context to probe
 * System.loadLibrary; on the JVM the loadLibrary probe fails and
 * the engine still returns a stable `engineTag`. We pass a null
 * Context — the router only dereferences it on the SdCppEngine
 * branch, and the engine's own probe is robust to a null
 * receiver (the JNI call is what actually fails on the host).
 */
class SdEngineRouterTest {

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val nullContext: Context = ContextWrapper(null)

    @Test
    fun `pick returns StubSdEngine for stub runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("stub"))
        val engine = router.pick()
        assertEquals("sd-stub", engine.engineTag)
        assertTrue(engine is StubSdEngine)
    }

    @Test
    fun `pick returns SdCppEngine for sd-cpp runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("sd.cpp"))
        val engine = router.pick()
        assertEquals("sd.cpp-gguf", engine.engineTag)
        assertTrue(engine is SdCppEngine)
    }

    @Test
    fun `pick returns OnnxSdEngine for onnx runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("onnx"))
        val engine = router.pick()
        assertEquals("onnx-ort", engine.engineTag)
        assertTrue(engine is OnnxSdEngine)
    }

    @Test
    fun `pick returns DiffusersEngine for diffusers runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("diffusers"))
        val engine = router.pick()
        assertEquals("diffusers-py", engine.engineTag)
        assertTrue(engine is DiffusersEngine)
    }

    @Test
    fun `pick returns ExecuTorchEngine for executorch runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("executorch"))
        val engine = router.pick()
        assertEquals("executorch-pte", engine.engineTag)
        assertTrue(engine is ExecuTorchEngine)
    }

    @Test
    fun `pick falls back to StubSdEngine for unknown runtime`() = runTest {
        val router = SdEngineRouter(nullContext, MutableStateFlow("garbage"))
        val engine = router.pick()
        assertEquals("sd-stub", engine.engineTag)
    }

    @Test
    fun `pickForKey honours the explicit runtime override`() {
        val router = SdEngineRouter(nullContext, MutableStateFlow("stub"))
        val engine = router.pickForKey("onnx")
        assertEquals("onnx-ort", engine.engineTag)
        assertTrue(engine is OnnxSdEngine)
    }

    @Test
    fun `pickForKey falls back to the persisted runtime when key is null`() {
        val router = SdEngineRouter(nullContext, MutableStateFlow("executorch"))
        val engine = router.pickForKey(null)
        assertEquals("executorch-pte", engine.engineTag)
    }

    @Test
    fun `pickForKey falls back to the persisted runtime when key is unknown`() {
        val router = SdEngineRouter(nullContext, MutableStateFlow("diffusers"))
        val engine = router.pickForKey("does-not-exist")
        assertEquals("diffusers-py", engine.engineTag)
    }
}
