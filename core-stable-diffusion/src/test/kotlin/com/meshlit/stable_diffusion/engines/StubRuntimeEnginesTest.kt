package com.meshlit.stable_diffusion.engines

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.stable_diffusion.SdConstraints
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4.x — Unit tests for the three MVP1 stub engines:
 * [OnnxSdEngine], [DiffusersEngine], [ExecuTorchEngine].
 *
 * Each one is a thin class that returns a typed "not
 * implemented" failure so the runtime picker slot is exercised
 * end-to-end. The tests assert:
 *  - engineTag is the persisted / telemetry string
 *  - isReady is always false
 *  - loadedModel is always null
 *  - txt2img / img2img / loadModel return the right typed
 *    failure tag (sd.onnx_unimplemented / sd.diffusers_not_bundled
 *    / sd.executorch_unimplemented)
 */
class OnnxSdEngineTest {
    private val engine = OnnxSdEngine()

    @Test fun `engineTag is onnx-ort`() = assertEquals("onnx-ort", engine.engineTag)
    @Test fun `isReady is false`() = assert(!engine.isReady)
    @Test fun `loadedModel is null`() = assert(engine.loadedModel == null)

    @Test
    fun `txt2img returns sd_onnx_unimplemented`() = runTest {
        val r = engine.txt2img(SdConstraints(prompt = "x"))
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.onnx_unimplemented", (r as MeshlitResult.Failure).error.tag)
        assertTrue(r.error is MeshlitError.Native)
    }

    @Test
    fun `loadModel returns sd_onnx_unimplemented`() = runTest {
        val r = engine.loadModel(
            com.meshlit.stable_diffusion.SdLoadRequest(
                runtime = com.meshlit.stable_diffusion.SdRuntime.OnnxRuntime,
                unetPath = "/x",
            ),
        )
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.onnx_unimplemented", (r as MeshlitResult.Failure).error.tag)
    }
}

class DiffusersEngineTest {
    private val engine = DiffusersEngine()

    @Test fun `engineTag is diffusers-py`() = assertEquals("diffusers-py", engine.engineTag)
    @Test fun `isReady is false`() = assert(!engine.isReady)
    @Test fun `loadedModel is null`() = assert(engine.loadedModel == null)

    @Test
    fun `txt2img returns sd_diffusers_not_bundled`() = runTest {
        val r = engine.txt2img(SdConstraints(prompt = "x"))
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.diffusers_not_bundled", (r as MeshlitResult.Failure).error.tag)
    }

    @Test
    fun `loadModel returns sd_diffusers_not_bundled`() = runTest {
        val r = engine.loadModel(
            com.meshlit.stable_diffusion.SdLoadRequest(
                runtime = com.meshlit.stable_diffusion.SdRuntime.DiffusersPython,
                unetPath = "/x",
            ),
        )
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.diffusers_not_bundled", (r as MeshlitResult.Failure).error.tag)
    }
}

class ExecuTorchEngineTest {
    private val engine = ExecuTorchEngine()

    @Test fun `engineTag is executorch-pte`() = assertEquals("executorch-pte", engine.engineTag)
    @Test fun `isReady is false`() = assert(!engine.isReady)
    @Test fun `loadedModel is null`() = assert(engine.loadedModel == null)

    @Test
    fun `txt2img returns sd_executorch_unimplemented`() = runTest {
        val r = engine.txt2img(SdConstraints(prompt = "x"))
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.executorch_unimplemented", (r as MeshlitResult.Failure).error.tag)
    }

    @Test
    fun `loadModel returns sd_executorch_unimplemented`() = runTest {
        val r = engine.loadModel(
            com.meshlit.stable_diffusion.SdLoadRequest(
                runtime = com.meshlit.stable_diffusion.SdRuntime.ExecuTorch,
                unetPath = "/x",
            ),
        )
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("sd.executorch_unimplemented", (r as MeshlitResult.Failure).error.tag)
    }
}