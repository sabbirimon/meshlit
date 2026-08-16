package com.meshlit.stable_diffusion.engines

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.stable_diffusion.SdConstraints
import com.meshlit.stable_diffusion.SdEngine
import com.meshlit.stable_diffusion.SdGeneratedImage
import com.meshlit.stable_diffusion.SdLoadRequest
import com.meshlit.stable_diffusion.SdModelInfo
import com.meshlit.stable_diffusion.SdProgressEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Phase 4.x — ONNX Runtime Mobile SD engine (MVP1 stub).
 *
 * Phase 2 will replace this with a real implementation that:
 *  1. Loads three `OrtSession`s — UNet, CLIP text encoder, VAE decoder.
 *  2. Tokenizes the prompt via the openai/clip-vit-base-patch32
 *     BPE vocab (bundled as a resource).
 *  3. Runs the text encoder → embeddings.
 *  4. Loops the scheduler (euler_a / dpmpp_2m) for `steps` ticks.
 *  5. Calls VAE.decode(latents) → RGB float32 → PNG bytes.
 *  6. Wraps in `SdGeneratedImage` + emits step previews via
 *     `progress()`.
 *
 * MVP1 returns typed `sd.onnx_unimplemented` failures so the
 * runtime picker shows the slot and the bridge dispatch wires are
 * hot. The user gets a clear "Install onnxruntime-android AAR and
 * implement the pipeline" hint when they pick ONNX.
 */
class OnnxSdEngine : SdEngine {

    override val engineTag: String = "onnx-ort"

    override val isReady: Boolean = false

    override val loadedModel: SdModelInfo? = null

    override suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.onnx_unimplemented",
                IllegalStateException(
                    "ONNX Runtime Mobile SD pipeline not yet implemented. " +
                        "Phase 2: add com.microsoft.onnxruntime:onnxruntime-android AAR and implement " +
                        "CLIP tokenize → text encoder → scheduler loop → VAE decode.",
                ),
            ),
        )

    override suspend fun unloadModel() = Unit

    override suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.onnx_unimplemented",
                IllegalStateException("ONNX Runtime Mobile SD pipeline not yet implemented."),
            ),
        )

    override suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.img2img_unsupported"))

    override suspend fun interrupt() = Unit

    override fun progress(): Flow<SdProgressEvent> = emptyFlow()
}