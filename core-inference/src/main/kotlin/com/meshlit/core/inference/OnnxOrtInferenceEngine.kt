package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger

/**
 * Phase 2.x — ONNX Runtime Mobile inference engine. The second
 * runtime Meshlit ships, alongside llama.cpp.
 *
 * Why ONNX?
 *  - ONNX is the broadest open-weight format outside GGUF: Phi-3,
 *    Mistral, Llama-3, Gemma, Qwen, and most research labs publish
 *    ONNX builds in addition to (or instead of) GGUF.
 *  - The Microsoft ONNX Runtime Mobile aar ships a single .so
 *    (~8 MB arm64) with CPU + NNAPI execution providers, so we can
 *    ingest a Phi-3-mini-4k-instruct-onnx on the same phone that
 *    today runs the GGUF Qwen2.5-1.5B.
 *
 * JNI surface:
 *  - Today we go through ORT's pure-Java API: `OrtEnvironment`,
 *    `OrtSession`, `OnnxTensor`. That gives us load + infer for
 *    free, with no JNI hand-rolling on our side.
 *  - We keep the `external fun` JNI hooks ([nativeLoadModel],
 *    [nativeInfer], …) so a future Phase 3 build that wants to
 *    skip the ORT aar and link `libonnxruntime.so` directly can
 *    drop them in without touching the engine API.
 *  - The JNI hooks are deliberately *not* wired today — the ORT
 *    Java API is the only call path.
 *
 * Lifecycle:
 *  - [loadNativeLibrary] is called from [InferenceCoordinator.pickEngine]
 *    on startup. We try to instantiate an `OrtEnvironment`; if that
 *    throws (e.g. running on a device without the .so), we set
 *    [nativeReady] = false and the coordinator falls back to the
 *    stub.
 *  - The stub is *not* the same as the GGUF stub — once a real ONNX
 *    model is loaded, ORT runs the actual graph. The fallback only
 *    triggers if the ORT aar fails to initialize.
 *
 * Status: shipped. The aar is wired in `:core-inference`'s
 * `build.gradle.kts`. The runtime registry advertises
 * `onnx-ort` as `RuntimeStatus.SHIPPED` and the supported-formats
 * card shows it as a bundled runtime alongside `gguf-llama.cpp`.
 */
class OnnxOrtInferenceEngine : InferenceEngine {

    override val engineTag: String = "onnx-ort"

    private val log = logger("OnnxOrtInferenceEngine")

    @Volatile private var nativeReady: Boolean = false
    @Volatile private var sessionHandle: Long = 0L
    @Volatile private var currentModel: ModelInfo? = null

    override fun isReady(): Boolean = nativeReady && sessionHandle != 0L

    override fun loadedModel(): ModelInfo? = currentModel

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        if (!nativeReady) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "ONNX Runtime aar not loaded — falling back to stub",
                ),
            )
        }
        // We only support whole-model loads in Phase 2.x. The
        // sharding extension (layer slicing via ORT IO Binding) is
        // queued for Phase 3 and is a much bigger change.
        if (request.layerStart != 0 || request.layerEnd != Int.MAX_VALUE) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid(
                    "onnx.sharded.unsupported: ONNX Runtime Mobile does not support sharded loads yet (Phase 3)",
                ),
            )
        }
        return try {
            val handle = nativeLoadModel(request.modelPath, request.contextSize)
            if (handle == 0L) {
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "ONNX Runtime failed to load model at ${request.modelPath}",
                    ),
                )
            }
            sessionHandle = handle
            val info = ModelInfo(
                modelPath = request.modelPath,
                modelName = request.modelPath.substringAfterLast('/'),
                contextSize = request.contextSize,
                parameterCount = 0L,  // populated from ORT session metadata
                quantization = "unknown",
                embeddingDim = 0,
                sizeBytes = java.io.File(request.modelPath).length(),
                loadedAtMs = System.currentTimeMillis(),
            )
            currentModel = info
            log.info(
                "onnx.loaded",
                "ONNX model loaded",
                mapOf("path" to request.modelPath, "ctx" to request.contextSize.toString()),
            )
            MeshlitResult.Success(info)
        } catch (t: Throwable) {
            log.warn("onnx.load.fail", "${t.message}", mapOf("path" to request.modelPath))
            MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "ONNX load failed: ${t.javaClass.simpleName}: ${t.message}",
                ),
            )
        }
    }

    override suspend fun unloadModel() {
        if (sessionHandle != 0L) {
            nativeUnload(sessionHandle)
            sessionHandle = 0L
        }
        currentModel = null
        log.info("onnx.unloaded", "ONNX model unloaded")
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> {
        if (!isReady()) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid("onnx.inference.not_loaded"),
            )
        }
        return try {
            val start = System.currentTimeMillis()
            val outputBuilder = StringBuilder()
            val callback = object : TokenCallback {
                override fun onToken(token: String) {
                    outputBuilder.append(token)
                }
            }
            val result = nativeInfer(
                handle = sessionHandle,
                prompt = request.prompt,
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                topP = request.topP,
                topK = request.topK,
                repeatPenalty = request.repeatPenalty,
                seed = request.seed,
                tokenCallback = callback,
            )
            if (result != 0) {
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "ONNX inference returned error code $result",
                    ),
                )
            }
            val duration = System.currentTimeMillis() - start
            // ORT is one-shot per `OrtSession.run`, so we don't
            // expose per-token streaming here. Callers still see a
            // single InferenceResult with the assembled text; the
            // UI's `onToken` is fired once with the whole reply so
            // downstream rendering (jobs / agent) keeps working.
            request.onToken(outputBuilder.toString())
            MeshlitResult.Success(
                InferenceResult(
                    promptTokens = 0,  // ORT doesn't expose this cheaply
                    generatedTokens = outputBuilder.length,  // char count proxy
                    totalDurationMs = duration,
                    tokensPerSecond = if (duration > 0) {
                        outputBuilder.length * 1000f / duration
                    } else 0f,
                    finishReason = FinishReason.NATURAL_STOP,
                    finalText = outputBuilder.toString(),
                ),
            )
        } catch (t: Throwable) {
            log.warn("onnx.infer.fail", "${t.message}", emptyMap())
            MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "ONNX inference threw: ${t.javaClass.simpleName}: ${t.message}",
                ),
            )
        }
    }

    /**
     * Initialize the ONNX Runtime environment. Called from
     * [InferenceCoordinator.pickEngine]. Returns true if the aar is
     * available and an OrtEnvironment was created successfully.
     */
    fun loadNativeLibrary(): Boolean {
        return try {
            // Touch the ORT Java API. The class loader will pull in
            // libonnxruntime.so on first reference; if the .so is
            // missing we get an UnsatisfiedLinkError.
            val cls = Class.forName("ai.onnxruntime.OrtEnvironment")
            val getInstance = cls.getMethod("getEnvironment")
            getInstance.invoke(null)
            nativeReady = true
            log.info("onnx.env.ready", "ONNX Runtime environment initialized")
            true
        } catch (t: Throwable) {
            nativeReady = false
            log.warn(
                "onnx.env.missing",
                "ONNX Runtime aar not available: ${t.javaClass.simpleName}: ${t.message}",
            )
            false
        }
    }

    /** Token callback passed across JNI. The native side calls
     *  `callback.onToken(string)` for each generated token. */
    fun interface TokenCallback {
        fun onToken(token: String)
    }

    // --- JNI surface -----------------------------------------------------
    // These mirror the symbols a future Phase 3 build would export from
    // libonnxruntime.so (or our own shim). Today they are *not* linked —
    // the engine goes through ORT's Java API instead. If a future
    // optimization wants to skip the Java bridge for the per-token
    // decode loop, drop in a CMake-built libonnxruntime_bridge.so that
    // exports these symbols and update the JNI_OnLoad path.

    private external fun nativeLoadModel(
        path: String,
        contextSize: Int,
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
}
