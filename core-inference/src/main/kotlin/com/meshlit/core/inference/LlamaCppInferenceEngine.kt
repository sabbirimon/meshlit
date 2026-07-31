package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger

/**
 * Production inference engine — wraps llama.cpp via JNI.
 *
 * Phase 1 ships this as a stub that fails to load and forces the
 * coordinator to fall back to [JvmStubInferenceEngine]. The actual
 * JNI surface is defined here as `external` declarations so the
 * project compiles before the native code is built.
 *
 * To enable in production:
 *  1. Build `libmeshlit_inference.so` from `core-inference/src/main/cpp/`
 *     (Phase 1.x — CMakeLists.txt + llama.cpp source tree).
 *  2. Set `System.setProperty("meshlit.inference.stub", "false")` at
 *     app startup.
 *  3. The coordinator calls [loadNativeLibrary] which returns
 *     `true` when the .so is found and JNI_OnLoad succeeds.
 *
 * JNI surface (one symbol per `external fun`):
 *  - `nativeInit` — call llama.cpp's `llama_backend_init()` once
 *  - `nativeLoadModel` — `llama_load_model_from_file` + `llama_new_context_with_model`
 *  - `nativeInfer` — tokenize → loop (sample + eval + decode) → detokenize
 *  - `nativeUnload` — `llama_free` + `llama_free_model`
 *  - `nativeCancel` — sets a flag the worker loop checks per token
 *
 * The Java side here is intentionally minimal — all real work
 * happens in the .so. Java only marshals strings, ints, and a
 * JNIEnv token callback.
 */
class LlamaCppInferenceEngine : InferenceEngine {

    override val engineTag: String = "llama.cpp"

    private val log = logger("LlamaCppInferenceEngine")

    @Volatile private var nativeHandle: Long = 0L
    @Volatile private var nativeReady: Boolean = false

    override fun isReady(): Boolean = nativeReady

    override fun loadedModel(): ModelInfo? = null  // populated from native side

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        if (!nativeReady) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "llama.cpp native library not loaded — falling back to stub",
                ),
            )
        }
        // Real call would block on JNI; we'd suspend and dispatch on IO.
        // For Phase 1 we return an error so the coordinator picks the stub.
        return MeshlitResult.Failure(
            com.meshlit.core.common.MeshlitError.Native(
                "llama.cpp JNI surface declared but not yet implemented",
            ),
        )
    }

    override suspend fun unloadModel() {
        if (nativeReady && nativeHandle != 0L) {
            nativeUnload(nativeHandle)
            nativeHandle = 0L
            nativeReady = false
        }
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> {
        return MeshlitResult.Failure(
            com.meshlit.core.common.MeshlitError.Native(
                "llama.cpp not yet implemented — use JvmStubInferenceEngine",
            ),
        )
    }

    /**
     * Try to load `libmeshlit_inference.so`. Returns true on
     * success. The coordinator calls this at startup; on failure
     * it picks [JvmStubInferenceEngine] instead.
     */
    fun loadNativeLibrary(): Boolean {
        return try {
            System.loadLibrary("meshlit_inference")
            nativeInit()
            nativeReady = true
            log.info("llama.cpp.loaded", "native library loaded")
            true
        } catch (t: Throwable) {
            nativeReady = false
            log.warn("llama.cpp.missing", "native library missing — falling back to stub: ${t.message}")
            false
        }
    }

    // --- JNI surface -----------------------------------------------------
    // Each of these is implemented in core-inference/src/main/cpp/meshlit_inference.cpp.
    // Until that file is added, calling these throws UnsatisfiedLinkError.

    private external fun nativeInit(): Boolean
    private external fun nativeLoadModel(
        path: String,
        contextSize: Int,
        gpuLayers: Int,
        threads: Int,
    ): Long  // returns opaque handle, 0 on failure
    private external fun nativeInfer(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        seed: Long,
        tokenCallback: TokenCallback,
    ): Int  // 0 on success, negative on error
    private external fun nativeUnload(handle: Long)
    private external fun nativeCancel(handle: Long)

    /** Token callback passed across JNI. The native side calls
     *  `callback.onToken(string)` for each generated token. */
    fun interface TokenCallback {
        fun onToken(token: String)
    }
}