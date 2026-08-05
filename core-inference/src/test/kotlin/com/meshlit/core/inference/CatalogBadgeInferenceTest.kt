package com.meshlit.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates that the [RunAnywhereCatalogEngine.BUNDLED_IDS] set is
 * stable and that the canonical entry identifiers stay consistent
 * with the `:app`-side curated catalog.
 *
 * The actual `adaptModelInfo()` helper is private inside the engine
 * because it depends on the SDK's `ModelInfo` proto. This test
 * exercises the public surface — what consumers can read — and
 * the badge inference rules that the engine's helpers encode.
 */
class CatalogBadgeInferenceTest {

    @Test fun bundled_ids_contains_smollm2_starter() {
        // The starter swapped from Qwen 2.5 1.5B Q4_K_M (~940 MB)
        // to SmolLM2-360M-Instruct Q8_0 (~368 MB) per the user's
        // "use a small model" guidance. The asset basename matches
        // the SDK's `DEFAULT_MODEL_ID` so the FGS auto-loads it
        // without a rename step.
        assertTrue(
            "smollm2-360m-instruct-q8_0" in RunAnywhereCatalogEngine.BUNDLED_IDS,
        )
    }

    @Test fun bundled_ids_does_not_contain_moe_models() {
        // MoE models are user-installable only — they should never be
        // bundled, because the APK would balloon to >20 GB.
        val moeIds = setOf(
            "qwen3-30b-a3b-instruct-q4_k_m",
            "granite-4.0-tiny-moe-q4_k_m",
            "mixtral-8x7b-instruct-q4_k_m",
        )
        moeIds.forEach { id ->
            assertTrue(
                "MoE model $id should NOT be bundled",
                id !in RunAnywhereCatalogEngine.BUNDLED_IDS,
            )
        }
    }

    @Test fun entry_badge_computes_architecture_chip() {
        val moe = RunAnywhereCatalogEngine.Entry(
            id = "qwen3-30b-a3b-instruct-q4_k_m",
            displayName = "Qwen3-30B-A3B-Instruct · Q4_K_M",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 3",
            approxSizeMb = 18_000L,
            language = "EN/ZH/ES/FR/DE/…",
            strengths = listOf("moe", "reasoning"),
            architecture = RunAnywhereCatalogEngine.Architecture.MOE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.HUGE,
            bundled = false,
        )
        val labels = moe.badges().map { it.label }
        assertTrue("MOE chip", "MOE" in labels)
        assertTrue("Q4-K-M chip", "Q4-K-M" in labels)
        assertTrue("HUGE chip", "HUGE" in labels)
        assertTrue("multi chip", "multi" in labels)
    }

    @Test fun entry_badge_dense_quant_size_only() {
        val dense = RunAnywhereCatalogEngine.Entry(
            id = "smollm2-360m-instruct-q8_0",
            displayName = "SmolLM2-360M-Instruct · Q8_0",
            origin = "USA",
            license = "Apache 2.0",
            family = "SmolLM2",
            approxSizeMb = 368L,
            language = "English-first",
            strengths = listOf("starter", "fast"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.Q8_0,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.SMALL,
            bundled = true,
        )
        val labels = dense.badges().map { it.label }
        assertTrue("DENSE chip", "DENSE" in labels)
        assertTrue("Q8-0 chip", "Q8-0" in labels)
        assertTrue("SMALL chip", "SMALL" in labels)
        assertTrue("bundled chip", "bundled" in labels)
    }

    @Test fun entry_badge_unknown_quant_omitted() {
        val unknown = RunAnywhereCatalogEngine.Entry(
            id = "mystery-1",
            displayName = "Mystery 1",
            origin = "Unknown",
            license = "Unknown",
            family = "Unknown",
            approxSizeMb = 100L,
            language = "EN",
            strengths = listOf("general"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.UNKNOWN,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.SMALL,
        )
        val labels = unknown.badges().map { it.label }
        assertEquals("DENSE", labels.first())
        // No Q-chip because quant = UNKNOWN.
        assertTrue(labels.none { it.startsWith("Q") })
        assertTrue("SMALL chip present", "SMALL" in labels)
        // No multi chip because language = "EN".
        assertTrue("multi chip absent", "multi" !in labels)
    }
}
