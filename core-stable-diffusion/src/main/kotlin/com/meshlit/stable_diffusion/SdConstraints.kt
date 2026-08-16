package com.meshlit.stable_diffusion

/**
 * Phase 4.x — Per-image generation parameters for SD. Mirrors the
 * shape `StableDiffusionBridge.Constraints` already exposes to the
 * UI so the bridge can pass user-typed values straight through to
 * the engine.
 *
 * Lives in `:core-stable-diffusion` (not `:app`) so the engine
 * surface doesn't depend on the bridge module — engines stay
 * loadable from `:core-advanced-engines` if Phase 2 lifts them
 * out of the image-gen package.
 *
 * Field semantics:
 *  - [prompt]: the user prompt. Must be non-empty.
 *  - [negativePrompt]: passed through to engines that support a
 *    separate neg-prompt slot (sd.cpp, ONNX). Engines without a
 *    slot prepend it to [prompt] themselves.
 *  - [steps]: diffusion step count. 20 = typical, 30+ for
 *    higher-quality renders. The UI clamps to 1..20 today; the
 *    engine accepts up to 50.
 *  - [cfgScale]: classifier-free guidance scale. 7.0 = typical,
 *    1.5 for turbo/lcm models.
 *  - [sampler]: euler_a / dpmpp_2m / heun / etc. sd.cpp has its
 *    own name table — the engine maps aliases.
 *  - [seed]: -1 = random, else deterministic.
 *  - [baseImage]: base64-encoded source PNG for img2img.
 *  - [denoisingStrength]: 0.0..1.0, fraction of noise added before
 *    diffusion. 0.7 = typical.
 */
data class SdConstraints(
    val prompt: String,
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val sampler: String = "euler_a",
    val seed: Long = -1L,
    val batchSize: Int = 1,
    val clipSkip: Int = 1,
    val baseImage: String? = null,
    val denoisingStrength: Float = 0.7f,
)

/** Wire shape returned by txt2img / img2img. Mirrors the
 *  sd.cpp + sd-server JSON `{ "images": ["base64png..."], "info":
 *  {...} }`. */
data class SdGeneratedImage(
    val base64Png: String,
    val seed: Long,
    val durationSec: Float,
    val prompt: String,
)