package com.meshlit.stable_diffusion.engines

import android.content.Context
import android.content.ContextWrapper
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.stable_diffusion.SdConstraints
import com.meshlit.stable_diffusion.SdLoadRequest
import com.meshlit.stable_diffusion.SdRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MVP1 contract tests for the native-backed sd.cpp adapter. */
class SdCppEngineTest {

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val context: Context = ContextWrapper(null)

    @Test
    fun `engineTag identifies the native runtime`() {
        assertEquals("sd.cpp-gguf", SdCppEngine(context).engineTag)
    }

    @Test
    fun `engine is not ready before model load`() {
        val engine = SdCppEngine(context)
        assertFalse(engine.isReady)
        assertNull(engine.loadedModel)
    }

    @Test
    fun `loadModel returns typed failure when native library is unavailable`() = runTest {
        val result = SdCppEngine(context).loadModel(
            SdLoadRequest(
                runtime = SdRuntime.StableDiffusionCpp,
                unetPath = "/data/data/com.meshlit/models/unet.gguf",
                textEncoderPath = "/data/data/com.meshlit/models/clip.safetensors",
                threads = 4,
            ),
        )
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        // `sd.lib_not_linked` is the Android-host contract. On a
        // device where the JNI library is packaged, this test still
        // exercises the load path (the CMake stub returns a magic
        // handle); the test harness intentionally doesn't link it.
        assertEquals("sd.lib_not_linked", result.error.tag)
    }

    @Test
    fun `txt2img returns typed native stub failure`() = runTest {
        val result = SdCppEngine(context).txt2img(
            SdConstraints(prompt = "a red panda", width = 512, height = 512),
        )
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        // `sd.not_loaded` is the contract when no model has been
        // loaded yet (loadModel was never called). `sd.native_stub`
        // is what callers see when JNI is wired but the C++ body
        // returns nullptr. The engine routes the user to the Load
        // button in either case.
        assertEquals("sd.not_loaded", result.error.tag)
        assertTrue(result.error is MeshlitError.Native)
    }

    @Test
    fun `img2img returns typed unsupported failure`() = runTest {
        val result = SdCppEngine(context).img2img(
            SdConstraints(prompt = "red panda", baseImage = "iVBORw0KGgo="),
        )
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertEquals("sd.img2img_unsupported", result.error.tag)
    }
}
