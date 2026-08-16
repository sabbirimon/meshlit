package com.meshlit.core.inference

/**
 * Which downloaded LLM to auto-load into an empty chat slot,
 * best-first. Kept pure (no ViewModel / lifecycle deps) so the
 * default-load ordering is unit-testable.
 *
 * Mirrors `com.runanywhere.runanywhereai.ui.screens.models
 * .ModelAutoLoadPolicy` (examples/android/RunAnywhereAI — upstream
 * RunAnywhere sample). Local adaptation: our bundled starter
 * (`smollm2-360m-instruct-q8_0`) is inserted at the front of the
 * preference list because it's the smallest viable conversational
 * model and ships inside the APK. The rest of the upstream
 * preference list is preserved so the upgrade path is identical
 * to the upstream picker's "Top pick" recommendation.
 *
 * Order stays synchronized with the catalog's recommended-section
 * highlighting (the Top-pick pill in the picker) so the auto-load
 * default and the UI's recommendation agree.
 */
object ModelAutoLoadPolicy {

    /**
     * Best-first preference list. The bundled starter wins; if it's
     * not on disk, the smallest NPU-friendly NPU chat model wins.
     * Keep in sync with the recommended-row highlight logic in
     * `ModelsScreen.kt` so the auto-load default and the visible
     * "Top pick" chip agree.
     */
    val PREFERENCE: List<String> = listOf(
        // Bundled starter — ships inside the APK so this is always
        // available after first launch. Auto-loaded by the FGS on
        // cold start; no network required.
        "smollm2-360m-instruct-q8_0",
        // Upstream order preserved from here on. We intentionally
        // do not include the curated catalog ids (`qwen2.5`,
        // `llama-3.2`, etc.) because their SDK ids don't match the
        // upstream preference naming and the user has explicitly
        // asked for upstream parity.
        "qwen3_5_0_8b",
        "lfm2_5_350m",
        "qwen3_0_6b",
        "lfm2_5_230m",
    )

    /**
     * The highest-preference ready model id — matched exactly or as
     * an architecture-suffixed `"<id>_v<N>"` variant (e.g.
     * `qwen3_5_0_8b_v79`), never as an arbitrary substring.
     *
     * Falls back to the first ready id when no preference matches,
     * and `null` if `readyIds` is empty. The exact-match + suffix
     * rule avoids fuzzy collisions: a `qwen3` prefix would otherwise
     * match both `qwen3_0_6b` and `qwen3_5_0_8b`.
     */
    fun preferredCandidateId(
        readyIds: List<String>,
        preference: List<String> = PREFERENCE,
    ): String? {
        if (readyIds.isEmpty()) return null
        for (pref in preference) {
            val exact = readyIds.firstOrNull { it == pref }
            if (exact != null) return exact
            val suffixed = readyIds.firstOrNull { it.startsWith("${pref}_") }
            if (suffixed != null) return suffixed
        }
        return readyIds.first()
    }
}
