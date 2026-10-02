package com.meshlit.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the multi-source refactor of
 * [RunAnywhereCatalogEngine.Entry]:
 *
 *  - `Entry` carries a `sources: List<DownloadSource>` so a single
 *    model row can expose several HF orgs / quant variants.
 *  - `Entry.primarySource` returns the lowest-priority source.
 *  - `Entry.availableTiers` / `availableQuants` collapse the list
 *    for filter chip rendering.
 *  - `Entry.minSizeBytes` / `maxSizeBytes` give the size range
 *    chip.
 *  - `Entry.modelType` is independent from `Entry.architecture`.
 *  - `DownloadSource.humanSize()` formats bytes / MB / GB cleanly.
 *
 * Backward compat: the new fields default safely (`sources = []`,
 * `modelType = CHAT`) so existing consumers compile unchanged.
 */
class CatalogMultiSourceTest {

    private fun source(
        org: String,
        quant: RunAnywhereCatalogEngine.Quant,
        sizeBytes: Long,
        url: String,
        tier: RunAnywhereCatalogEngine.SourceTier,
        priority: Int,
    ) = RunAnywhereCatalogEngine.DownloadSource(
        id = "$org:${quant.name}@$priority",
        org = org,
        quant = quant,
        approxSizeBytes = sizeBytes,
        url = url,
        tier = tier,
        priority = priority,
    )

    @Test fun primary_source_is_lowest_priority() {
        val entry = RunAnywhereCatalogEngine.Entry(
            id = "phi-3-mini-4k-instruct-q4_k_m",
            displayName = "Phi-3-mini-4k-instruct",
            origin = "USA",
            license = "MIT",
            family = "Phi 3",
            approxSizeMb = 2300L,
            language = "EN",
            strengths = listOf("reasoning"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.LARGE,
            sources = listOf(
                source(
                    "unsloth", RunAnywhereCatalogEngine.Quant.Q4_K_M,
                    1_950_000_000L,
                    "https://huggingface.co/unsloth/Phi-3-mini-4k-instruct-GGUF/resolve/main/Q4_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT,
                    priority = 20,
                ),
                source(
                    "bartowski", RunAnywhereCatalogEngine.Quant.Q4_K_M,
                    2_300_000_000L,
                    "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Q4_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT,
                    priority = 10,
                ),
                source(
                    "bartowski", RunAnywhereCatalogEngine.Quant.Q4_K_M,
                    2_650_000_000L,
                    "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Q5_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT,
                    priority = 30,
                ),
            ),
        )
        val primary = entry.primarySource
        assertNotNull("primary source present", primary)
        assertEquals("bartowski", primary!!.org)
        assertEquals(10, primary.priority)
        assertEquals(
            "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Q4_K_M.gguf",
            primary.url,
        )
    }

    @Test fun primary_source_null_when_no_sources() {
        val entry = RunAnywhereCatalogEngine.Entry(
            id = "bundled-only",
            displayName = "Bundled Only",
            origin = "USA",
            license = "Apache 2.0",
            family = "Test",
            approxSizeMb = 100L,
            language = "EN",
            strengths = listOf("starter"),
            sources = emptyList(),
        )
        assertNull("primary source is null", entry.primarySource)
        // primaryUrl returns the bundled sentinel so callers
        // don't have to null-check.
        assertEquals("asset://bundled", entry.primaryUrl)
    }

    @Test fun available_tiers_collapses_to_distinct_set() {
        val entry = RunAnywhereCatalogEngine.Entry(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen2.5-1.5B-Instruct",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 2.5",
            approxSizeMb = 1100L,
            language = "EN/ZH",
            strengths = listOf("multilingual"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            sources = listOf(
                source(
                    "Qwen", RunAnywhereCatalogEngine.Quant.Q4_K_M, 1_100_000_000L,
                    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/q4_k_m.gguf",
                    RunAnywhereCatalogEngine.SourceTier.OFFICIAL, priority = 10,
                ),
                source(
                    "Qwen", RunAnywhereCatalogEngine.Quant.Q4_K_M, 1_250_000_000L,
                    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/q5_k_m.gguf",
                    RunAnywhereCatalogEngine.SourceTier.OFFICIAL, priority = 20,
                ),
                source(
                    "bartowski", RunAnywhereCatalogEngine.Quant.Q4_K_M, 1_050_000_000L,
                    "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Q4_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT, priority = 30,
                ),
            ),
        )
        val tiers = entry.availableTiers
        assertEquals(2, tiers.size)
        assertTrue(RunAnywhereCatalogEngine.SourceTier.OFFICIAL in tiers)
        assertTrue(RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT in tiers)
    }

    @Test fun size_range_is_smallest_to_largest() {
        val entry = RunAnywhereCatalogEngine.Entry(
            id = "llama-3.2-1b-instruct-q4_k_m",
            displayName = "Llama-3.2-1B-Instruct",
            origin = "USA",
            license = "Llama community",
            family = "Llama 3.2",
            approxSizeMb = 900L,
            language = "EN/ES/FR/DE",
            strengths = listOf("multilingual"),
            sources = listOf(
                source(
                    "bartowski", RunAnywhereCatalogEngine.Quant.Q4_K_M, 900_000_000L,
                    "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Q4_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT, priority = 10,
                ),
                source(
                    "bartowski", RunAnywhereCatalogEngine.Quant.Q8_0, 1_350_000_000L,
                    "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Q8_0.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT, priority = 30,
                ),
            ),
        )
        assertEquals(900_000_000L, entry.minSizeBytes)
        assertEquals(1_350_000_000L, entry.maxSizeBytes)
    }

    @Test fun model_type_is_orthogonal_to_architecture() {
        // A MoE chat model — should classify as CHAT, not just MOE.
        val chat = RunAnywhereCatalogEngine.Entry(
            id = "qwen3-30b-a3b-instruct-q4_k_m",
            displayName = "Qwen3-30B-A3B-Instruct",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 3",
            approxSizeMb = 18_000L,
            language = "EN/ZH",
            strengths = listOf("moe"),
            architecture = RunAnywhereCatalogEngine.Architecture.MOE,
            modelType = RunAnywhereCatalogEngine.ModelType.CHAT,
            sources = listOf(
                source(
                    "unsloth", RunAnywhereCatalogEngine.Quant.Q4_K_M, 18_000_000_000L,
                    "https://huggingface.co/unsloth/Qwen3-30B-A3B-Instruct-2507-GGUF/resolve/main/Q4_K_M.gguf",
                    RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT, priority = 10,
                ),
            ),
        )
        assertEquals(
            RunAnywhereCatalogEngine.ModelType.CHAT,
            chat.modelType,
        )
        assertEquals(
            RunAnywhereCatalogEngine.Architecture.MOE,
            chat.architecture,
        )
    }

    @Test fun human_size_formats_bytes_mb_gb() {
        val bytes = RunAnywhereCatalogEngine.DownloadSource(
            id = "x",
            org = "x",
            quant = RunAnywhereCatalogEngine.Quant.UNKNOWN,
            approxSizeBytes = 500L,
            url = "",
            tier = RunAnywhereCatalogEngine.SourceTier.UNKNOWN,
            priority = 10,
        )
        val mb = bytes.copy(approxSizeBytes = 254_000_000L)
        val gb = bytes.copy(approxSizeBytes = 18_000_000_000L)
        assertEquals("500 B", bytes.humanSize())
        assertEquals("254 MB", mb.humanSize())
        assertEquals("18 GB", gb.humanSize())
        // Sub-GB shows two decimals (1.07 GB pattern).
        val oneGb = bytes.copy(approxSizeBytes = 1_070_000_000L)
        assertEquals("1.07 GB", oneGb.humanSize())
    }

    @Test fun backward_compat_default_model_type_is_chat() {
        // A pre-multi-source caller can construct an Entry without
        // specifying `modelType` — the default is CHAT so existing
        // renderers don't break.
        val legacy = RunAnywhereCatalogEngine.Entry(
            id = "legacy",
            displayName = "Legacy",
            origin = "USA",
            license = "Apache 2.0",
            family = "Legacy",
            approxSizeMb = 100L,
            language = "EN",
            strengths = listOf("general"),
        )
        assertEquals(
            RunAnywhereCatalogEngine.ModelType.CHAT,
            legacy.modelType,
        )
        assertEquals(emptyList<RunAnywhereCatalogEngine.DownloadSource>(), legacy.sources)
    }

    @Test fun model_type_enum_has_required_categories() {
        // The filter chip row iterates this list — make sure the
        // catalog exposes CHAT / CODE / VISION / MULTIMODAL /
        // EMBEDDING. UNKNOWN is intentionally not in the chip
        // row.
        val expected = setOf(
            "CHAT", "CODE", "VISION", "MULTIMODAL", "EMBEDDING", "UNKNOWN",
        )
        val actual = RunAnywhereCatalogEngine.ModelType.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test fun source_tier_enum_has_required_tiers() {
        val expected = setOf(
            "OFFICIAL", "COMMUNITY_REQUANT", "MIRROR", "SDK_BUNDLED", "UNKNOWN",
        )
        val actual = RunAnywhereCatalogEngine.SourceTier.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}