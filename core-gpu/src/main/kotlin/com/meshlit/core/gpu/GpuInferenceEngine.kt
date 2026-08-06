package com.meshlit.core.gpu

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.BackendHints
import com.meshlit.core.inference.InferenceEngine
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.inference.InferenceResult
import com.meshlit.core.inference.ModelInfo
import com.meshlit.core.inference.ModelLoadRequest

/**
 * Thin [InferenceEngine] wrapper that rewrites [ModelLoadRequest.backendHints]
 * based on the latest [GpuProbe].
 *
 *  - If the probe is `VULKAN`, the wrapper upgrades `backendHints.gpuBackend`
 *    to `VULKAN` and recomputes `gpuLayers` for the model size.
 *  - If the probe is `NONE`, the wrapper falls back to CPU by passing
 *    `BackendHints.CpuOnly` straight through.
 *
 * The wrapped engine is responsible for the actual JNI plumbing — this
 * wrapper only handles policy so the wrapper is testable on the JVM.
 */
class GpuInferenceEngine(
    private val inner: InferenceEngine,
    private val probeProvider: () -> GpuProbe,
) : InferenceEngine {
    override val engineTag: String = inner.engineTag + "+gpu"

    override fun isReady(): Boolean = inner.isReady()

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        val probe = probeProvider()
        val adjusted = request.copy(
            backendHints = BackendHintsPicker.pick(
                probe = probe,
                cpuThreads = request.backendHints.cpuThreads,
                modelParameterCount = null,
            ),
        )
        return inner.loadModel(adjusted)
    }

    override suspend fun unloadModel() = inner.unloadModel()

    override fun loadedModel(): ModelInfo? = inner.loadedModel()

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        inner.infer(request)
}