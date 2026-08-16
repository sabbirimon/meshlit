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
 * Phase 4.x — Always-fallback SD engine. The router returns this
 * when the persisted runtime is `"stub"` or when the persisted
 * value is invalid (anything outside `SdRuntime.entries`).
 *
 * Every operation returns a typed `sd.stub` failure so the bridge
 * can route to the procedural fallback when this engine is
 * selected. `isReady` is always false.
 */
class StubSdEngine : SdEngine {

    override val engineTag: String = "sd-stub"

    override val isReady: Boolean = false

    override val loadedModel: SdModelInfo? = null

    override suspend fun loadModel(req: SdLoadRequest): MeshlitResult<SdModelInfo> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "sd.stub",
                IllegalStateException(
                    "Stable Diffusion runtime is set to 'stub'. Pick a runtime in Settings → Image Gen → Local SD Models.",
                ),
            ),
        )

    override suspend fun unloadModel() = Unit

    override suspend fun txt2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.stub"))

    override suspend fun img2img(c: SdConstraints): MeshlitResult<SdGeneratedImage> =
        MeshlitResult.Failure(MeshlitError.Native("sd.stub"))

    override suspend fun interrupt() = Unit

    override fun progress(): Flow<SdProgressEvent> = emptyFlow()
}