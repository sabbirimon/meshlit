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
 * Phase 4.x — ExecuTorch SD engine (MVP1 stub).
 *
 * ExecuTorch is Meta's PyTorch mobile runtime (`.pte` files). It's
 * the smallest and fastest of the three runtimes when fully wired —
 * the AAR is ~5 MB and the `.pte` files load via mmap in <100 ms.
 *
 * Why it's stubbed for MVP1:
 *  - No stable PyTorch → ExecuTorch export pipeline for SD 1.5
 *    checkpoints exists yet (as of mid-2026, SDXL-Turbo has one
 *    but quality is mixed on mobile).
 *  - Adding `org.pytorch:executorch-android` AAR is one Gradle
 *    line; the body is the real work.
 *
 * MVP1 returns typed `sd.executorch_unimplemented` failures so the
 * runtime picker shows the slot. Phase 2 wires the AAR + .pte
 * loader.
 */
class ExecuTorchEngine : SdEngine {

    override val engineTag: String = "executorch-pte"

    override val isReady: Boolean = false

    override val loadedModel: SdModelInfo? = null

    override suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.executorch_unimplemented",
                IllegalStateException(
                    "ExecuTorch SD pipeline not yet implemented. " +
                        "Phase 2: add org.pytorch:executorch-android AAR + .pte loader " +
                        "for the SDXL-Turbo / SD 1.5 ExecuTorch exports.",
                ),
            ),
        )

    override suspend fun unloadModel() = Unit

    override suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.executorch_unimplemented"))

    override suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.img2img_unsupported"))

    override suspend fun interrupt() = Unit

    override fun progress(): Flow<SdProgressEvent> = emptyFlow()
}