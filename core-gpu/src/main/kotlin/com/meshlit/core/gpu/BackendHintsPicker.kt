package com.meshlit.core.gpu

import com.meshlit.core.inference.BackendHints
import com.meshlit.core.inference.GpuBackend as InferenceGpuBackend

/**
 * Pure-Kotlin helper that turns a [GpuProbe] + the model's parameter
 * count into a [BackendHints] value. The picker is intentionally
 * stateless so callers can rebuild it cheaply whenever the probe
 * snapshot changes (eGPU hot-plug).
 *
 * Routing rules:
 *  - If the probe is `NONE`, return `BackendHints.CpuOnly`.
 *  - Else, route `gpuLayers = parameterCount / 1e9 * 20` clamped to
 *    `[0, totalLayers]`. The heuristic: one GPU layer per ~50 M
 *    params at Q4. The math is approximate and intentionally
 *    conservative; the JNI side overrides it once it knows the
 *    exact layer count of the loaded GGUF.
 *  - `gpuBackend` is set to `VULKAN` only.
 */
object BackendHintsPicker {
    fun pick(
        probe: GpuProbe,
        cpuThreads: Int = 0,
        modelParameterCount: Long? = null,
    ): BackendHints {
        if (probe.backend == GpuBackend.NONE) {
            return BackendHints.CpuOnly
        }
        val layers = (modelParameterCount ?: 0L).let { count ->
            if (count <= 0L) 0 else ((count / 50_000_000L).toInt()).coerceIn(0, 200)
        }
        return BackendHints(
            cpuThreads = cpuThreads,
            gpuLayers = layers,
            gpuBackend = InferenceGpuBackend.VULKAN,
        )
    }
}