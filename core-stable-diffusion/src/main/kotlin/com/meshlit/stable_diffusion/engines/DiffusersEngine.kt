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
 * Phase 4.x — Chaquopy diffusers engine (MVP1 stub).
 *
 * Diffusers is the reference Python implementation that
 * PrivateLM ships inside their APK (~80 MB embedded Python +
 * NumPy + diffusers + torch). Chaquopy adds the JPype interop
 * to call Python from Kotlin.
 *
 * MVP1 returns typed `sd.diffusers_not_bundled` failures. Phase 2
 * will add:
 *  - `com.chaquo.python:chaquopy` plugin
 *  - `python/` source tree with a `txt2img.py` entry
 *  - JPype bridge for prompt → diffusers.StableDiffusionPipeline
 *  - thread-affinity handling around the GIL
 *
 * APK cost when shipped: ~80 MB. Gate via
 * `meshlit.chaquopy.enabled=true` Gradle property.
 */
class DiffusersEngine : SdEngine {

    override val engineTag: String = "diffusers-py"

    override val isReady: Boolean = false

    override val loadedModel: SdModelInfo? = null

    override suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.diffusers_not_bundled",
                IllegalStateException(
                    "Chaquopy diffusers is not bundled in this build. " +
                        "Rebuild with `-Pmeshlit.chaquopy.enabled=true` to enable the Python " +
                        "diffusers runtime (~80 MB APK cost).",
                ),
            ),
        )

    override suspend fun unloadModel() = Unit

    override suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.diffusers_not_bundled"))

    override suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.img2img_unsupported"))

    override suspend fun interrupt() = Unit

    override fun progress(): Flow<SdProgressEvent> = emptyFlow()
}