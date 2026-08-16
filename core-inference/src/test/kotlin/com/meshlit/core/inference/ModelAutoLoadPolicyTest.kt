package com.meshlit.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Validates [ModelAutoLoadPolicy.preferredCandidateId] against the
 * upstream RunAnywhere sample's `ModelAutoLoadPolicy.kt` rules:
 *
 *  1. Exact match wins over a `<pref>_v<N>` suffix variant.
 *  2. Suffix variant wins over first-ready-id fallback.
 *  3. Empty `readyIds` returns null (no auto-load).
 *  4. Bundle starter (`smollm2-360m-instruct-q8_0`) is preferred
 *     above the upstream-pref list when present.
 *  5. A fuzzy substring (`qwen3` matches both `qwen3_0_6b` and
 *     `qwen3_5_0_8b`) does NOT cause the policy to collide — only
 *     exact and `_<suffix>` matches count.
 */
class ModelAutoLoadPolicyTest {

    @Test fun empty_returns_null() {
        assertNull(ModelAutoLoadPolicy.preferredCandidateId(emptyList()))
    }

    @Test fun single_id_returns_that_id() {
        // When only one model is on disk and it isn't in the
        // preference list, we still load it (better than loading
        // nothing).
        val ready = listOf("smollm2-360m-instruct-q8_0")
        assertEquals(
            "smollm2-360m-instruct-q8_0",
            ModelAutoLoadPolicy.preferredCandidateId(ready),
        )
    }

    @Test fun bundled_starter_wins_over_upstream_pref() {
        // The bundled SmolLM2 is at the head of PREFERENCE so it
        // wins over every NPU chat model even when those are also
        // on disk.
        val ready = listOf(
            "qwen3_5_0_8b",
            "lfm2_5_350m",
            "smollm2-360m-instruct-q8_0",
        )
        assertEquals(
            "smollm2-360m-instruct-q8_0",
            ModelAutoLoadPolicy.preferredCandidateId(ready),
        )
    }

    @Test fun exact_pref_match_beats_suffix_variant() {
        // `qwen3_5_0_8b` is preferred over `qwen3_5_0_8b_v79` —
        // the upstream rule is exact first, then `_v<N>` suffix.
        val ready = listOf("qwen3_5_0_8b_v79", "qwen3_5_0_8b")
        assertEquals(
            "qwen3_5_0_8b",
            ModelAutoLoadPolicy.preferredCandidateId(ready),
        )
    }

    @Test fun suffix_variant_beats_first_ready_fallback() {
        // When the exact id isn't on disk but the architecture-
        // suffixed variant is, the variant wins over arbitrary
        // first-ready.
        val ready = listOf("some_other_model", "qwen3_5_0_8b_v79")
        assertEquals(
            "qwen3_5_0_8b_v79",
            ModelAutoLoadPolicy.preferredCandidateId(ready),
        )
    }

    @Test fun falls_back_to_first_ready_when_no_pref_matches() {
        // The cooperative host (e.g. an internal test) might have a
        // model on disk that's not in our preference list — we
        // still auto-load it as the first-ready fallback.
        val ready = listOf("not_in_pref_list_anything")
        assertEquals(
            "not_in_pref_list_anything",
            ModelAutoLoadPolicy.preferredCandidateId(ready),
        )
    }

    @Test fun fuzzy_qwen3_does_not_collide_with_qwen3_5_0_8b() {
        // The policy must not match `qwen3_5_0_8b` via a fuzzy
        // `qwen3` substring. Only `qwen3` itself or `qwen3_*` should
        // match.
        val ready = listOf("qwen3_5_0_8b")
        // qwen3_5_0_8b is at index 1 in PREFERENCE, after
        // smollm2-360m-instruct-q8_0. Both should match (exact).
        assertEquals(
            "smollm2-360m-instruct-q8_0",
            ModelAutoLoadPolicy.preferredCandidateId(ready + "smollm2-360m-instruct-q8_0"),
        )
        // Without the bundle, qwen3_5_0_8b itself wins over a
        // generic `qwen3` id that happens to be present.
        val ready2 = listOf("qwen3", "qwen3_5_0_8b")
        assertEquals(
            "qwen3_5_0_8b",
            ModelAutoLoadPolicy.preferredCandidateId(ready2),
        )
    }

    @Test fun custom_preference_overrides_default() {
        // The `preference` parameter lets the caller override the
        // policy. We test that by passing a single-element list.
        val ready = listOf("smollm2-360m-instruct-q8_0", "lfm2_5_350m")
        assertEquals(
            "lfm2_5_350m",
            ModelAutoLoadPolicy.preferredCandidateId(ready, listOf("lfm2_5_350m")),
        )
    }
}