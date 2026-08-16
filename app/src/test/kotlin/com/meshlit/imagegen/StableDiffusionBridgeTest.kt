package com.meshlit.imagegen

import com.meshlit.stable_diffusion.SdProgressEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the StableDiffusionBridge progress mapping.
 *
 * The bridge itself is a final class with a `MeshlitApplication`
 * dependency, so this suite pins only the pure, side-effect-free
 * parts:
 *  - SdProgressEvent → ProgressEvent mapper (so the UI receives a
 *    stable shape regardless of the active backend).
 *
 * End-to-end bridge dispatch is exercised through the Smoke
 * Manual on-device; the constraintsToSd / sdGeneratedToBridge
 * conversion helpers are intentionally tested in the core-stable
 * -diffusion core (the engine surface is the contract).
 */
class StableDiffusionBridgeTest {

    @Test
    fun `Loading maps to a placeholder Step frame`() {
        val mapped = StableDiffusionBridge.mapSdProgress(SdProgressEvent.Loading(percent = 42))
        assertTrue(mapped is StableDiffusionBridge.ProgressEvent.Step)
        mapped as StableDiffusionBridge.ProgressEvent.Step
        assertEquals(0, mapped.current)
        assertEquals(100, mapped.total)
        assertEquals(null, mapped.image)
    }

    @Test
    fun `Step passes through current total and preview`() {
        val mapped = StableDiffusionBridge.mapSdProgress(
            SdProgressEvent.Step(current = 7, total = 28, previewB64 = "iVBORw0KGgo="),
        )
        assertTrue(mapped is StableDiffusionBridge.ProgressEvent.Step)
        mapped as StableDiffusionBridge.ProgressEvent.Step
        assertEquals(7, mapped.current)
        assertEquals(28, mapped.total)
        assertEquals("iVBORw0KGgo=", mapped.image)
    }

    @Test
    fun `Decoding maps to a Decoding frame with preview`() {
        val mapped = StableDiffusionBridge.mapSdProgress(
            SdProgressEvent.Decoding(previewB64 = "iVBORw0KGgo="),
        )
        assertTrue(mapped is StableDiffusionBridge.ProgressEvent.Decoding)
        mapped as StableDiffusionBridge.ProgressEvent.Decoding
        assertEquals("iVBORw0KGgo=", mapped.image)
    }

    @Test
    fun `Completed maps to Completed`() {
        val mapped = StableDiffusionBridge.mapSdProgress(SdProgressEvent.Completed)
        assertEquals(StableDiffusionBridge.ProgressEvent.Completed, mapped)
    }

    @Test
    fun `Failed maps to Failed with the reason`() {
        val mapped = StableDiffusionBridge.mapSdProgress(SdProgressEvent.Failed("sd.native_stub"))
        assertTrue(mapped is StableDiffusionBridge.ProgressEvent.Failed)
        mapped as StableDiffusionBridge.ProgressEvent.Failed
        assertEquals("sd.native_stub", mapped.reason)
    }
}
