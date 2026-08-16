package com.meshlit.stable_diffusion.engines

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.stable_diffusion.SdConstraints
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StubSdEngine] — the always-fallback engine
 * the router picks when the user has explicitly chosen
 * "Disabled (stub)" or DataStore returns garbage.
 *
 * The stub is the safety net: every op must return a typed
 * failure with a stable `sd.stub` tag so callers can render
 * a consistent "Runtime disabled" UI.
 */
class StubSdEngineTest {

    @Test
    fun `engineTag is sd-stub`() {
        assertEquals("sd-stub", StubSdEngine().engineTag)
    }

    @Test
    fun `isReady is always false`() {
        assertFalse(StubSdEngine().isReady)
    }

    @Test
    fun `loadedModel is always null`() {
        assertNull(StubSdEngine().loadedModel)
    }

    @Test
    fun `txt2img returns Failure with sd-stub tag`() = runTest {
        val result = StubSdEngine().txt2img(SdConstraints(prompt = "a cat"))
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertEquals("sd.stub", result.error.tag)
        assertTrue(result.error is MeshlitError.Native)
    }

    @Test
    fun `img2img returns Failure with sd-stub tag`() = runTest {
        val result = StubSdEngine().img2img(SdConstraints(prompt = "a cat"))
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertEquals("sd.stub", result.error.tag)
    }

    @Test
    fun `loadModel returns Failure with sd-stub tag`() = runTest {
        val result = StubSdEngine().loadModel(
            com.meshlit.stable_diffusion.SdLoadRequest(
                runtime = com.meshlit.stable_diffusion.SdRuntime.Stub,
                unetPath = "/data/x",
            ),
        )
        assertTrue(result is MeshlitResult.Failure)
        result as MeshlitResult.Failure
        assertEquals("sd.stub", result.error.tag)
    }

    @Test
    fun `unloadModel is a no-op`() = runTest {
        // Must not throw.
        StubSdEngine().unloadModel()
    }

    @Test
    fun `interrupt is a no-op`() = runTest {
        StubSdEngine().interrupt()
    }
}