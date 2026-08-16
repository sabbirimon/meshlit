package com.meshlit.core.training.plan

import kotlinx.serialization.Serializable

/**
 * Minimal model metadata consumed by [ShardingPlanner]. Carries
 * enough information to compute layer / tensor / pipeline partitions
 * without dragging the full HuggingFace config into `core-training`.
 *
 * In v0 this is hand-built by the trainer's launch path. A real
 * autograd path will populate it from the loaded `InferenceEngine`
 * once that lands.
 */
@Serializable
data class ModelSpec(
    /** Display name — also used in MeshlitEvent telemetry. */
    val name: String,
    /** Total parameter count, in millions. Used for sizing the
     *  per-peer optimizer-state budget. */
    val paramCountM: Long,
    /** Number of decoder layers. 0 if the model is not a
     *  layer-stack architecture (then `mode = REPLICATED` is forced). */
    val totalLayers: Int,
    /** Hidden dimension — used for tensor-partition sizing. */
    val hiddenDim: Int,
    /** Bytes per parameter — 4 for INT8, 2 for INT4/NF4, 1 for INT2. */
    val bytesPerParam: Int = 2,
) {
    init {
        require(paramCountM > 0) { "paramCountM must be > 0; got $paramCountM" }
        require(totalLayers >= 0) { "totalLayers must be >= 0; got $totalLayers" }
        require(hiddenDim > 0) { "hiddenDim must be > 0; got $hiddenDim" }
        require(bytesPerParam in 1..8) { "bytesPerParam must be in [1, 8]" }
    }

    /** Total resident size, in MB. */
    fun residentMb(): Long =
        (paramCountM * 1_000_000L * bytesPerParam) / (1024L * 1024L)

    /** Per-layer optimizer state size, in MB (AdamW: 2 floats per
     *  parameter). */
    fun perLayerOptimizerMb(): Long {
        if (totalLayers == 0) return 0L
        val perLayerParams = (paramCountM * 1_000_000L) / totalLayers
        return (perLayerParams * 8L) / (1024L * 1024L)
    }
}
