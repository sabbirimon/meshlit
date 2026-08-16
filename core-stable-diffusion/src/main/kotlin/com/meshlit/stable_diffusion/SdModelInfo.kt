package com.meshlit.stable_diffusion

/**
 * Phase 4.x — Description of a model currently loaded into a
 * [SdEngine]. Mirrors the shape `LlamaCppInferenceEngine.ModelInfo`
 * uses for LLM inference so the orchestration layer can pattern-
 * match on the same data class family.
 *
 * `loadedAtEpochSec` is wall-clock seconds since epoch; the engine
 * stamps it at load completion so the UI can render "Loaded 12s ago"
 * without re-fetching the file mtime.
 *
 * `approxSizeMb` is the sum of all three file sizes (unet +
 * textEncoder + vae) at load time — useful for the model card
 * footer ("DreamShaper 8 Q4_0 · 1.7 GB loaded").
 *
 * `taesdPath` is tracked separately from [vaePath] because the two
 * are mutually exclusive — sd.cpp prefers TAESD (faster, ~5 MB)
 * when present and falls back to a full VAE (~330 MB) otherwise.
 */
data class SdModelInfo(
    val runtime: SdRuntime,
    val modelId: String,
    val unetPath: String,
    val textEncoderPath: String?,
    val vaePath: String?,
    val taesdPath: String?,
    val approxSizeMb: Long,
    val loadedAtEpochSec: Long,
)