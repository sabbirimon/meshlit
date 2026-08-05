package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult

/**
 * Fallback inference engine used when no real runtime is available
 * for the requested file format. Surfaces a typed
 * [MeshlitError.Native] failure on every operation so a load never
 * silently produces a placeholder reply.
 *
 * This is **not** a stub. A stub synthesizes plausible output and
 * pretends inference happened; an engine that ships a fake reply is
 * dangerous because every UI surface above it thinks it received a
 * real model response. The only acceptable behaviour in a "no engine"
 * state is to refuse the request loudly.
 *
 * Use cases:
 *  - The host called [InferenceCoordinator.loadModel] before any of
 *    [LlamaCppInferenceEngine], [OnnxOrtInferenceEngine], or
 *    [RunAnywhereInferenceEngine] came up native-ready.
 *  - The user picked a file format no runtime can decode (e.g.
 *    SafeTensors / TFLite / MLX / CoreML on Android).
 *
 * Engine tag is `"none"` so the UI's "stub banner" branch in
 * `JobsScreen` lights up with an honest message: "No engine
 * available — open Settings → Runtimes".
 */
class NoOpInferenceEngine : InferenceEngine {

    override val engineTag: String = "none"

    override fun isReady(): Boolean = false

    override fun loadedModel(): ModelInfo? = null

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> =
        MeshlitResult.Failure(
            MeshlitError.Native(
                "no_engine_for_format:${request.modelPath.ifBlank { "<empty>" }}",
            ),
        )

    override suspend fun unloadModel() {
        // Nothing to unload — we never loaded anything.
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        MeshlitResult.Failure(
            MeshlitError.Native("no_engine_for_infer:${request.prompt.length}-chars"),
        )
}
