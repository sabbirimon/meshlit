package com.meshlit.stable_diffusion

/**
 * Phase 4.x — Real on-device Stable Diffusion MVP.
 *
 * The five runtime options the user can pick from in the
 * `LocalSdModelCard`. The string values are persisted via
 * `SettingsRepository.imageGenSdRuntimeFlow` and survive a cold
 * restart, so the user doesn't need to re-pick every session.
 *
 * Lifecycle:
 *  - [Stub] is the default and the no-op fallback. The bridge
 *    routes through the procedural engine when this is selected.
 *  - [StableDiffusionCpp] is the production target — Phase 2 drops
 *    real sd.cpp + ggml into the libmeshlit_sd.so body and the
 *    same runtime id keeps working.
 *  - [OnnxRuntime] / [DiffusersPython] / [ExecuTorch] are stub
 *    classes for MVP1; their engine implementations return typed
 *    "not yet implemented" failures so the picker UI shows all five
 *    slots but the dispatch wires are hot.
 *
 * Adding a new runtime = add a new entry here + add a case in
 * [SdEngineRouter.pick] + add an engine implementation file. The
 * rest of the system reads only the [engineTag] string and the
 * SettingsRepository key, so it's an additive change.
 */
enum class SdRuntime(val label: String, val engineTag: String, val key: String) {
    Stub("Disabled (stub)", "sd-stub", "stub"),
    StableDiffusionCpp(
        "stable-diffusion.cpp (GGUF)",
        "sd.cpp-gguf",
        "sd.cpp",
    ),
    OnnxRuntime(
        "ONNX Runtime Mobile",
        "onnx-ort",
        "onnx",
    ),
    DiffusersPython(
        "Chaquopy diffusers (Phase 2)",
        "diffusers-py",
        "diffusers",
    ),
    ExecuTorch(
        "ExecuTorch (Phase 2)",
        "executorch-pte",
        "executorch",
    ),
    ;

    companion object {
        val default: SdRuntime = Stub

        /** Lookup by persisted key with safe fallback to [default]. */
        fun fromKey(key: String?): SdRuntime =
            entries.firstOrNull { it.key == key } ?: default

        /** Non-throwing variant — returns null on unknown key so
         *  callers can distinguish "unknown runtime" from "user
         *  picked Stub". */
        fun fromKeyOrNull(key: String?): SdRuntime? =
            entries.firstOrNull { it.key == key }
    }
}