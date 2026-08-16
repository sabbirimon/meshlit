package com.meshlit.stable_diffusion

/**
 * Phase 4.x — What to load into a [SdEngine]. One request per
 * loadModel call; engines cache the result internally until
 * `unloadModel` is called or a different [SdLoadRequest] supersedes
 * it.
 *
 * All paths are absolute filesystem paths under `filesDir/imported-models/`
 * — the [SdImportController] downloads them and writes them to
 * canonical locations before the engine sees them.
 *
 * [threads] is the CPU thread budget. sd.cpp runs the diffusion
 * loop on `threads` cores; the default of 4 is the right tradeoff
 * for a mid-range phone (8 cores total, leave 4 for the UI).
 * [gpuLayers] is the number of transformer layers to offload to
 * GPU / NPU; 0 means CPU-only. The stub engine ignores both
 * fields.
 *
 * [vaeTiling] enables VAE tile-by-tile decode for large images.
 * Cuts peak memory by ~4x at a small throughput cost. Recommended
 * for SDXL on devices with <6 GB RAM.
 */
data class SdLoadRequest(
    val runtime: SdRuntime,
    val unetPath: String,
    val textEncoderPath: String? = null,
    val vaePath: String? = null,
    val taesdPath: String? = null,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val vaeTiling: Boolean = false,
)