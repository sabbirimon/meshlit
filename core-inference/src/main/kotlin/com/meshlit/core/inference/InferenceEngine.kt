package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult

/**
 * Pluggable inference engine. The actual llama.cpp native binding
 * (NDK / JNI) lives behind this interface so the rest of the app
 * (orchestration, prompt dispatcher, scheduler) doesn't depend on
 * the native library being loadable.
 *
 * Implementations:
 *  - [LlamaCppInferenceEngine] — production. Calls `libmeshlit_inference.so`
 *    via JNI. Not yet linked; Phase 1 ships [JvmStubInferenceEngine] so
 *    the upper layers can be exercised end-to-end without native code.
 *  - [JvmStubInferenceEngine] — development. Echoes the prompt back as
 *    a stream of tokens. Lets UI / orchestration / service plumbing be
 *    tested on any device, including emulators without NDK support.
 *
 * Lifecycle:
 *  - [loadModel] / [unloadModel] are called by [com.meshlit.core.inference.InferenceCoordinator]
 *    when the foreground service starts / stops.
 *  - [infer] is the streaming call. Tokens arrive via [onToken]. The
 *    coroutine completes when [onComplete] fires.
 *  - [infer] is a `suspend` function that yields tokens via a [Flow]
 *    so the UI can collect incrementally.
 *
 * Backend selection:
 *  - The engine picks an internal backend (CPU / GPU / eGPU) based on
 *    [BackendHints]. The hint table is built from the device profile
 *    (chipset, GPU family, eGPU) at coordinator startup.
 */
interface InferenceEngine {

    val engineTag: String

    /** Whether the native library is loaded and ready. */
    fun isReady(): Boolean

    /** Load a GGUF model from disk. Returns when the model is in
     *  memory and ready to serve prompts. Cancelling the coroutine
     *  mid-load leaves the engine in [isReady]=false. */
    suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo>

    /** Drop the loaded model. Safe to call even when nothing is loaded. */
    suspend fun unloadModel()

    /** Currently loaded model, or null when nothing is loaded. */
    fun loadedModel(): ModelInfo?

    /** Stream a single inference. Tokens arrive via [InferenceRequest.onToken].
     *  Returns when generation completes or is cancelled. */
    suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult>
}

/** What to load. Fields map 1:1 to llama.cpp's `llama_model_params`. */
data class ModelLoadRequest(
    val modelPath: String,
    val contextSize: Int = 4096,
    val gpuLayers: Int = 0,
    val threads: Int = 0,
    val useMmap: Boolean = true,
    val backendHints: BackendHints = BackendHints.CpuOnly,
    /**
     * Inclusive layer start for a sharded load. `0` is the default
     * and means "load the embed + first layer". The JNI layer uses
     * this to filter tensor descriptors before allocating buffers.
     */
    val layerStart: Int = 0,
    /**
     * Exclusive layer end. `Int.MAX_VALUE` means "all remaining
     * layers". Pair with [layerStart] to load only your shard's
     * slice of the GGUF.
     */
    val layerEnd: Int = Int.MAX_VALUE,
    /**
     * Shard manifest the load is part of. Required when
     * [layerStart]/[layerEnd] restrict the load; optional otherwise.
     * Carries KV cache size + tokenizer refs so the engine knows
     * how much RAM to reserve and what vocab to load.
     */
    val manifest: com.meshlit.core.inference.net.ShardManifest? = null,
)

/** Per-backend hints gathered from the device profile. The engine
 *  uses these to pick which llama.cpp backend(s) to enable. */
data class BackendHints(
    /** Number of CPU threads to allocate. 0 means "auto". */
    val cpuThreads: Int,
    /** Number of transformer layers to offload to GPU. */
    val gpuLayers: Int,
    /** Preferred GPU backend family. */
    val gpuBackend: GpuBackend,
    /** eGPU connection, if any. */
    val egpu: com.meshlit.core.common.EGpuConnection? = null,
    /** Host OS — affects SIMD selection. */
    val hostOS: com.meshlit.core.common.HostOS = com.meshlit.core.common.HostOS.ANDROID,
) {
    companion object {
        val CpuOnly = BackendHints(
            cpuThreads = 0,
            gpuLayers = 0,
            gpuBackend = GpuBackend.NONE,
        )
    }
}

/** llama.cpp GPU backend family. Maps 1:1 to the llama.cpp build
 *  flags (`-DGGML_CUDA=ON`, `-DGGML_VULKAN=ON`, etc.). The engine
 *  picks the first one with a working driver on the host. */
enum class GpuBackend(val tag: String, val displayName: String) {
    NONE("none", "CPU only"),
    VULKAN("vulkan", "Vulkan"),
    OPENCL("opencl", "OpenCL"),
    CUDA("cuda", "CUDA (NVIDIA)"),
    ROCM("rocm", "ROCm (AMD)"),
    METAL("metal", "Metal (Apple)"),
}

/** Description of a loaded model. Returned by [InferenceEngine.loadModel]
 *  and shown in the UI / device card. */
data class ModelInfo(
    val modelPath: String,
    val modelName: String,
    val contextSize: Int,
    val parameterCount: Long,
    val quantization: String,
    val embeddingDim: Int,
    val sizeBytes: Long,
    val loadedAtMs: Long,
    /** Inclusive layer start this device is hosting. `0` for a
     *  whole-model load. Used by the `/v1/model` endpoint and the
     *  planner to make sharding decisions. */
    val layerStart: Int = 0,
    /** Exclusive layer end. `Int.MAX_VALUE` for whole-model loads. */
    val layerEnd: Int = Int.MAX_VALUE,
)

/** One inference call. The [onToken] callback fires for each generated
 *  token; [onComplete] fires once when generation finishes (success
 *  or failure). */
data class InferenceRequest(
    val prompt: String,
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val stopSequences: List<String> = emptyList(),
    val seed: Long = -1L,
    val onToken: suspend (String) -> Unit,
    val onComplete: suspend (InferenceResult) -> Unit = {},
)

/** Inference outcome. */
data class InferenceResult(
    val promptTokens: Int,
    val generatedTokens: Int,
    val totalDurationMs: Long,
    val tokensPerSecond: Float,
    val finishReason: FinishReason,
    val finalText: String,
)

enum class FinishReason(val tag: String) {
    NATURAL_STOP("natural"),        // EOS token
    MAX_TOKENS("max_tokens"),        // hit maxTokens limit
    STOP_SEQUENCE("stop_sequence"),  // matched one of stopSequences
    CANCELLED("cancelled"),          // caller cancelled
    ERROR("error"),                  // engine error
}