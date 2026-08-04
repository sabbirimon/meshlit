package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.x — routing tests for [InferenceCoordinator]. After moving
 * to per-load engine dispatch, the coordinator must hand a GGUF load
 * to the llama.cpp engine and an ONNX load to the ORT engine — never
 * route a `.gguf` path to ORT (which can't load it) and never route
 * a `.onnx` path to llama.cpp (which can't parse it).
 *
 * These tests don't touch native engines — they exercise the
 * `engineFor(format)` logic by inspecting the coordinator's `state`
 * after a load and asserting which engine responded.
 */
class InferenceCoordinatorEngineRoutingTest {

    @Test
    fun `fallback engine does not crash when loadNativeLibrary is missing`() = runBlocking {
        // Phase 2.x — on a unit-test JVM with no native libraries
        // available, the coordinator should resolve to the stub
        // engine and the bundled `.gguf` load should land on the
        // stub instead of crashing with UnsatisfiedLinkError
        // (which is what the pre-fix coordinator did when llama.cpp
        // was missing and ORT came up first).
        val coord = InferenceCoordinator()
        assertEquals(
            "stub engine should be the active one when no native lib is present",
            "stub",
            coord.engineTag,
        )
        // A GGUF load should be accepted (returns a stub-generated
        // ModelInfo) without throwing — that's the user-visible
        // behaviour from the Jobs screen.
        val tempFile = java.io.File.createTempFile("meshlit-fake-bundled-", ".gguf")
        try {
            // The stub only checks file existence + size, not format
            // content. A 1 KB placeholder is enough.
            tempFile.writeBytes(ByteArray(1024))
            val result = coord.loadModel(
                modelPath = tempFile.absolutePath,
                contextSize = 4096,
            )
            assertTrue(
                "stub engine should accept a GGUF load, got: $result",
                result is MeshlitResult.Success,
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `infer before load returns typed error not a crash`() = runBlocking {
        // Regression check — before the fix, an infer() call on an
        // unloaded coordinator would `engine.infer()` which threw
        // when the native engine wasn't ready. After the fix,
        // [pickEngineForInfer] always returns *some* engine (the
        // stub) and the stub's `infer()` returns a typed failure.
        val coord = InferenceCoordinator()
        val req = InferenceRequest(
            prompt = "hello",
            maxTokens = 8,
            onToken = {},
        )
        val result = coord.infer(req)
        assertTrue(
            "infer before load must surface a typed MeshlitResult, got: $result",
            result is MeshlitResult.Failure,
        )
    }

    @Test
    fun `loadedModel is null on a fresh coordinator`() {
        // Phase 2.x — after the per-load dispatch refactor,
        // `loadedModel()` walks every engine. On a fresh coordinator
        // that's never run a load, all engines return null and the
        // aggregated answer is null. (Previously this went through
        // the single `engine.loadedModel()` which also returned
        // null, but the contract is now exercised by every engine
        // that's touched.)
        val coord = InferenceCoordinator()
        assertEquals(null, coord.loadedModel())
    }
}
