package com.meshlit.stable_diffusion

/**
 * Phase 4.x — Step-by-step progress emitted by a [SdEngine] during
 * a txt2img / img2img call. The UI subscribes via [SdEngine.progress]
 * and renders each `Step` as a partially-decoded preview PNG so the
 * user sees mid-generation progress even on slow devices.
 *
 * Mirrors `StableDiffusionBridge.ProgressEvent` 1:1 — the bridge
 * mapper in the `:app` module translates these into the screen-
 * facing sealed class without forking the UI surface.
 */
sealed class SdProgressEvent {
    /** Model load in progress (0..100). Emitted only during
     *  loadModel() — once isReady is true, txt2img emits Steps. */
    data class Loading(val percent: Int) : SdProgressEvent()

    /** A diffusion step just completed. [current] is 1-indexed,
     *  [total] matches the user's `steps` setting, [previewB64] is
     *  the partially-decoded PNG (may be null for stub engines). */
    data class Step(val current: Int, val total: Int, val previewB64: String?) : SdProgressEvent()

    /** VAE decode step. Decoupled from [Step] so the UI can show a
     *  separate "decoding…" indicator for the few hundred ms
     *  between the last step and the final image. */
    data class Decoding(val previewB64: String?) : SdProgressEvent()

    /** txt2img / img2img completed successfully. The final image
     *  arrives on the suspend call's MeshlitResult.Success. */
    object Completed : SdProgressEvent()

    /** Generation aborted with [reason] (typed MeshlitError tag). */
    data class Failed(val reason: String) : SdProgressEvent()
}