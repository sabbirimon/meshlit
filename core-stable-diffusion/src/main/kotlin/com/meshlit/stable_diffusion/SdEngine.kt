package com.meshlit.stable_diffusion

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.flow.Flow

/**
 * Phase 4.x — Pluggable on-device Stable Diffusion engine. Mirrors
 * `InferenceEngine` 1:1 so the orchestration layer can pattern-match
 * on the same shape across LLMs and SD.
 *
 * Implementations:
 *  - [SdCppEngine] — wraps `libmeshlit_sd.so` via JNI. Phase 2
 *    replaces the JNI stub body with a real stable-diffusion.cpp
 *    call; MVP1 returns typed `sd.native_stub` failures.
 *  - [OnnxSdEngine] — wraps ONNX Runtime Mobile for `.onnx`
 *    SD exports. Stub for MVP1 (returns `sd.onnx_unimplemented`).
 *  - [DiffusersEngine] — wraps Chaquopy + Python diffusers.
 *    Stub for MVP1 (returns `sd.diffusers_not_bundled`).
 *  - [ExecuTorchEngine] — wraps ExecuTorch `.pte` files. Stub for
 *    MVP1 (returns `sd.executorch_unimplemented`).
 *  - [StubSdEngine] — always-fallback. Used when the persisted
 *    runtime is "stub" or the persisted value is invalid.
 *
 * Lifecycle:
 *  - [loadModel] / [unloadModel] are called by the `ImageGenScreen`
 *    when the user picks a runtime + taps Load.
 *  - [txt2img] / [img2img] are the streaming calls. The final
 *    image arrives via `MeshlitResult.Success`; step + preview
 *    events stream via [progress].
 *  - [interrupt] cancels any in-flight generation.
 */
interface SdEngine {

    /** Stable identifier. Surfaced in the UI status chip so the
     *  user knows which engine actually ran. */
    val engineTag: String

    /** Whether a model is currently loaded and inference can
     *  proceed. False after construction; flips true at the end
     *  of [loadModel]. */
    val isReady: Boolean

    /** Currently loaded model metadata; null when [isReady] is false. */
    val loadedModel: SdModelInfo?

    /** Load a model triple from disk. Returns when the model is in
     *  memory and inference can proceed. Cancelling the coroutine
     *  mid-load leaves the engine in [isReady]=false. */
    suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo>

    /** Release the loaded model. Safe to call when nothing is loaded. */
    suspend fun unloadModel()

    /** Generate one image from text. The [SdConstraints] shape
     *  comes from the screen so the engine can stay decoupled from
     *  UI. */
    suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage>

    /** Generate from a base64-encoded source image + prompt.
     *  Stub engines return `Failure("sd.img2img_unsupported")`. */
    suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage>

    /** Cancel any in-flight generation. Safe to call when idle. */
    suspend fun interrupt()

    /** Stream step + preview events for an in-flight generation.
     *  Collect this in parallel with [txt2img] / [img2img] so the
     *  UI can render the partially-decoded preview. */
    fun progress(): Flow<SdProgressEvent>
}