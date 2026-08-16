package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitError
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
 *
 * After the stub-removal refactor (the old `JvmStubInferenceEngine`
 * is gone), the no-native-lib path lands on `NoOpInferenceEngine`.
 * NoOp intentionally surfaces typed failures rather than echoing
 * placeholders, so the assertion shapes below match that contract:
 * a load returns a typed `Failure`, never a `Success` containing
 * stub text.
 */
class InferenceCoordinatorEngineRoutingTest {

    @Test
    fun `fallback engine does not crash when loadNativeLibrary is missing`() = runBlocking {
        // Phase 2.x — on a unit-test JVM with no native libraries
        // available, the coordinator should resolve to the NoOp
        // fallback engine and a bundled `.gguf` load should produce
        // a *typed* failure (`no_engine_for_format:...`) rather than
        // crashing with UnsatisfiedLinkError (which is what the
        // pre-fix coordinator did when llama.cpp was missing and
        // ORT came up first) and rather than echoing a deterministic
        // placeholder reply (which is what `JvmStubInferenceEngine`
        // used to do).
        val coord = InferenceCoordinator()
        assertEquals(
            "no-op engine should be the active one when no native lib is present",
            "none",
            coord.engineTag,
        )
        // A GGUF load on a JVM with no native lib should return a
        // typed Failure carrying the no_engine_for_format code —
        // this is the contract the user-facing Jobs banner reads.
        val tempFile = java.io.File.createTempFile("meshlit-fake-bundled-", ".gguf")
        try {
            // NoOp only inspects the path string, not file content.
            // A 1 KB placeholder is enough.
            tempFile.writeBytes(ByteArray(1024))
            val result = coord.loadModel(
                modelPath = tempFile.absolutePath,
                contextSize = 4096,
            )
            val failure = result as? MeshlitResult.Failure
            assertTrue(
                "no-op engine must surface a typed Failure for a load, got: $result",
                failure != null,
            )
            val nativeError = failure?.error as? MeshlitError.Native
            assertTrue(
                "no-op engine failure must carry the no_engine_for_format tag, got: ${failure?.error}",
                nativeError?.tag?.startsWith("no_engine_for_format:") == true,
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
        // [pickEngineForInfer] always returns *some* engine (NoOp
        // in the no-native-lib case). The coordinator short-circuits
        // before NoOp's `infer()` is invoked because NoOp reports
        // `isReady() == false`, surfacing a typed
        // `coord.inference.not_loaded` Invalid failure.
        val coord = InferenceCoordinator()
        val req = InferenceRequest(
            prompt = "hello",
            maxTokens = 8,
            onToken = {},
        )
        val result = coord.infer(req)
        val failure = result as? MeshlitResult.Failure
        assertTrue(
            "infer before load must surface a typed MeshlitResult, got: $result",
            failure != null,
        )
        // The error tag is the canonical signal across the cluster —
        // MeshlitError surfaces `tag`, not `message`, for telemetry.
        assertEquals(
            "infer before load must carry the coord.inference.not_loaded tag, got: ${failure?.error}",
            "coord.inference.not_loaded",
            failure?.error?.tag,
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
