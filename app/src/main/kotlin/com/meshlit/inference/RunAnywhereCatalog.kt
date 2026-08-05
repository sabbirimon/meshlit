package com.meshlit.inference

import com.meshlit.core.inference.RunAnywhereCatalogEngine

/**
 * Curated list of GGUF model ids the RunAnywhere SDK 0.20.12
 * knows how to download and serve.
 *
 * The upstream SDK does not currently expose a `listAvailable()`
 * or `catalog()` API — only `downloadModelStream(RAModelInfo(id))`
 * with a fixed set of known ids. So we maintain this list by hand,
 * mirroring the upstream README's recommended starters plus a
 * couple of Hugging Face's most-downloaded GGUFs. When the SDK
 * ships an enumeration API this whole object becomes a one-line
 * wrapper:
 *
 *     val all: List<Entry> = RunAnywhere.listAvailable().map { it.toEntry() }
 *
 * Each [Entry] mirrors the field shape of
 * [com.meshlit.core.inference.RunAnywhereCatalogEngine.Entry] so
 * the Catalog screen's Compose row can render both the live SDK
 * fetch and the offline fallback without branching on type.
 *
 * Quantization policy: Q4_K_M and Q8_0 only. These are the only
 * quants `libllama.so` (shipped by `runanywhere-llamacpp:0.20.12`)
 * can decode without an explicit `Q4_0` / `Q5_1` re-link. We pick
 * the lighter quant per family (Q8_0 for the 360M SmolLM2,
 * Q4_K_M for the 1-2B tier) so the user's first download lands
 * quickly even on cellular.
 *
 * Size budget: dense models cap at Phi-3-mini (2.3 GB) for now.
 * Bigger models belong on the cluster / sharding path that lives
 * on the Devices screen — a separate plan. MoE entries (Qwen3-A3B,
 * Granite-Tiny-MoE, Mixtral) are intentionally oversize because
 * they're tagged and the user picks them knowingly.
 *
 * Why a small list and not a long scraped one: the SDK's enrollment
 * is currently a manual id → URL mapping on the upstream side, and
 * renaming an id silently breaks every consumer. A short, audited
 * list is safer than a long scraped one.
 */
object RunAnywhereCatalog {

    /**
     * Mirrors the field shape of
     * [com.meshlit.core.inference.RunAnywhereCatalogEngine.Entry]
     * intentionally — the Catalog screen renders the same row for
     * both the live SDK fetch and this offline fallback.
     *
     * @property id SDK canonical id used by
     *   `RunAnywhere.downloadModelStream(RAModelInfo(id = …))`.
     *   Treat this as an opaque string from the host's perspective;
     *   the SDK owns it.
     * @property displayName shown in the Models screen row.
     * @property origin country flag + label, e.g. "USA", "China".
     * @property license short license tag — Apache 2.0, MIT, etc.
     * @property family model family name (Qwen 2.5, Llama 3.2, …).
     * @property approxSizeMb approximate download size for UI hint;
     *   the SDK's actual progress reports bytes.
     * @property language coverage flag, e.g. "English-first",
     *   "EN/ZH", "EN/ES/FR/DE/IT/PT/…".
     * @property strengths short list shown in the row subtitle,
     *   e.g. `listOf("multilingual", "general")`.
     * @property architecture DENSE vs MOE — drives the small
     *   architecture badge in the row.
     * @property quant quant tag — drives the Q-tag chip.
     * @property sizeClass size bucket — drives the S/M/L/HUGE chip
     *   and tone (success/info/warn/error).
     * @property bundled `true` when the model ships inside the APK
     *   (`assets/models/`); the row renders a green "bundled" chip
     *   and the importer skips the network download for it.
     */
    data class Entry(
        val id: String,
        val displayName: String,
        val origin: String,
        val license: String,
        val family: String,
        val approxSizeMb: Long,
        val language: String,
        val strengths: List<String>,
        val architecture: RunAnywhereCatalogEngine.Architecture =
            RunAnywhereCatalogEngine.Architecture.DENSE,
        val quant: RunAnywhereCatalogEngine.Quant =
            RunAnywhereCatalogEngine.Quant.UNKNOWN,
        val sizeClass: RunAnywhereCatalogEngine.SizeClass =
            RunAnywhereCatalogEngine.SizeClass.MEDIUM,
        val bundled: Boolean = false,
    )

    /**
     * Curated catalog. Order matters — the Models screen renders
     * the list top-to-bottom, and we want the smallest model (which
     * lands in ~10 s on Wi-Fi and lets first-run users see real
     * tokens fastest) at the top.
     */
    val all: List<Entry> = listOf(
        // The bundled starter — SmolLM2-360M-Instruct Q8_0. The
        // APK ships this file in `assets/models/`; the row renders
        // a green "bundled" chip + SMALL size class. The FGS
        // auto-loads it on first bind so the user sees real tokens
        // within seconds of cold start. The asset basename matches
        // the SDK's `DEFAULT_MODEL_ID`, so no rename step is
        // required between extraction and load.
        Entry(
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
        ),
        // Qwen 2.5 1.5B Q4_K_M — larger Chinese-built dense model.
        // Available as a download from the Catalog; the APK no
        // longer bundles it because the previous ~940 MB asset
        // exceeded the user's "use a smaller model" guidance. The
        // row stays in the curated list so users who want a bigger
        // general-purpose model can pull it via the SDK.
        Entry(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen2.5-1.5B-Instruct · Q4_K_M",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 2.5",
            approxSizeMb = 1100L,
            language = "EN/ZH/ES/FR/DE/…",
            strengths = listOf("multilingual", "general"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.MEDIUM,
        ),
        // 1B / Q4_K_M — Meta's small open-weight model. Useful for
        // users who want Meta-family outputs for comparison.
        Entry(
            id = "llama-3.2-1b-instruct-q4_k_m",
            displayName = "Llama-3.2-1B-Instruct · Q4_K_M",
            origin = "USA",
            license = "Llama community",
            family = "Llama 3.2",
            approxSizeMb = 900L,
            language = "EN/ES/FR/DE/IT/PT/…",
            strengths = listOf("multilingual", "fast"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.MEDIUM,
        ),
        // Phi-3-mini / Q4_K_M — current ceiling for the catalog.
        // Above this size we assume the user wants cluster-shard
        // inference, which is a different screen and a different
        // plan. Phi-3-mini is the largest that comfortably fits
        // a 6 GB-RAM phone.
        Entry(
            id = "phi-3-mini-4k-instruct-q4_k_m",
            displayName = "Phi-3-mini-4k-instruct · Q4_K_M",
            origin = "USA",
            license = "MIT",
            family = "Phi 3",
            approxSizeMb = 2300L,
            language = "EN",
            strengths = listOf("reasoning", "general"),
            architecture = RunAnywhereCatalogEngine.Architecture.DENSE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.MEDIUM,
        ),

        // ----------------------------------------------------------------
        // MoE (Mixture-of-Experts) entries.
        //
        // The user picks + downloads these from the Catalog. They are
        // NOT bundled — the APK ships with only the dense starter
        // above so first-launch stays under 1.5 GB installed. MoE
        // rows are tagged with `architecture = MOE` so the row UI
        // can show the MoE badge in `ACCENT` tone.
        //
        // Note on memory: MoE still has to load *all* experts into
        // RAM (only the active ones run per token), so the sizes
        // below are *total* weights, not active. Phones with <6 GB
        // RAM will OOM on Qwen3-30B-A3B.
        // ----------------------------------------------------------------

        // Qwen3-30B-A3B — flagship MoE, 30B total / 3B active.
        // Q4_K_M ≈ 18 GB. Only viable on 12 GB+ devices. Tagged
        // HUGE because the total weight size requires sharding
        // for phones.
        Entry(
            id = "qwen3-30b-a3b-instruct-q4_k_m",
            displayName = "Qwen3-30B-A3B-Instruct · Q4_K_M",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 3",
            approxSizeMb = 18_000L,
            language = "EN/ZH/ES/FR/DE/…",
            strengths = listOf("moe", "reasoning", "multilingual"),
            architecture = RunAnywhereCatalogEngine.Architecture.MOE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.HUGE,
        ),
        // IBM Granite-4.0-Tiny-MoE — small IBM MoE, ~1B total / 0.5B
        // active. Designed for edge / phone inference. Q4 ≈ 700 MB.
        Entry(
            id = "granite-4.0-tiny-moe-q4_k_m",
            displayName = "Granite-4.0-Tiny-MoE · Q4_K_M",
            origin = "USA",
            license = "Apache 2.0",
            family = "Granite 4",
            approxSizeMb = 700L,
            language = "EN-first",
            strengths = listOf("moe", "fast", "edge"),
            architecture = RunAnywhereCatalogEngine.Architecture.MOE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.SMALL,
        ),
        // Mixtral-8x7B-Instruct — classic MoE reference. 47B total
        // / 13B active. Q4 ≈ 26 GB. Powerful, but only on laptops /
        // sharded phones.
        Entry(
            id = "mixtral-8x7b-instruct-q4_k_m",
            displayName = "Mixtral-8x7B-Instruct · Q4_K_M",
            origin = "France",
            license = "Apache 2.0",
            family = "Mixtral",
            approxSizeMb = 26_000L,
            language = "EN/FR/DE/ES/IT/…",
            strengths = listOf("moe", "reasoning", "multilingual"),
            architecture = RunAnywhereCatalogEngine.Architecture.MOE,
            quant = RunAnywhereCatalogEngine.Quant.Q4_K_M,
            sizeClass = RunAnywhereCatalogEngine.SizeClass.HUGE,
        ),
    )

    /** Lookup by SDK id. Returns `null` if the id isn't in the
     *  curated list (e.g. a future SDK release that adds a fifth
     *  catalog row). */
    fun find(id: String): Entry? = all.firstOrNull { it.id == id }
}
