package com.meshlit.inference

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
 * Each [Entry] mirrors the field names of
 * [com.meshlit.models.ModelCatalog.Entry] (id, displayName,
 * origin, license, family, approxSizeMb, language, strengths)
 * so the Compose row in [com.meshlit.ui.screens.settings.ModelsScreen]
 * could later be shared between the two catalog cards if we
 * collapse them. Until then the two cards co-exist: the OkHttp
 * card is the fallback when the SDK fails to initialize.
 *
 * Quantization policy: Q4_K_M and Q8_0 only. These are the only
 * quants `libllama.so` (shipped by `runanywhere-llamacpp:0.20.12`)
 * can decode without an explicit `Q4_0` / `Q5_1` re-link. We pick
 * the lighter quant per family (Q8_0 for the 360M SmolLM2,
 * Q4_K_M for the 1-2B tier) so the user's first download lands
 * quickly even on cellular.
 *
 * Size budget: nothing larger than Phi-3-mini (2.3 GB) for now.
 * Bigger models belong on the cluster / sharding path that lives
 * on the Devices screen — a separate plan.
 *
 * Why four rows and not twenty: the SDK's enrollment is currently
 * a manual id → URL mapping on the upstream side, and renaming an
 * id silently breaks every consumer. A short, audited list is
 * safer than a long scraped one.
 */
object RunAnywhereCatalog {

    /**
     * Mirrors the field shape of
     * [com.meshlit.models.ModelCatalog.Entry] intentionally —
     * the Compose row could later be reused across both cards.
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
    )

    /**
     * Curated catalog. Order matters — the Models screen renders
     * the list top-to-bottom, and we want the smallest model (which
     * lands in ~10 s on Wi-Fi and lets first-run users see real
     * tokens fastest) at the top.
     */
    val all: List<Entry> = listOf(
        // 360M / Q8_0 — already the SDK's default starter. Anchors
        // the list because it's the only one that completes on
        // cellular in under a minute on a typical phone, so a
        // first-time user on a coffee-shop Wi-Fi gets real tokens
        // before they lose patience.
        Entry(
            id = "smollm2-360m-instruct-q8_0",
            displayName = "SmolLM2-360M-Instruct · Q8_0",
            origin = "USA",
            license = "Apache 2.0",
            family = "SmolLM2",
            approxSizeMb = 250L,
            language = "English-first",
            strengths = listOf("starter", "fast"),
        ),
        // 1.5B / Q4_K_M — same family as the bundled GGUF, so the
        // user can compare RunAnywhere-delivered weights against
        // the bundled asset without leaving the app.
        Entry(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen2.5-1.5B-Instruct · Q4_K_M",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 2.5",
            approxSizeMb = 1100L,
            language = "EN/ZH/ES/FR/DE/…",
            strengths = listOf("multilingual", "general"),
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
        ),
    )

    /** Lookup by SDK id. Returns `null` if the id isn't in the
     *  curated list (e.g. a future SDK release that adds a fifth
     *  catalog row). */
    fun find(id: String): Entry? = all.firstOrNull { it.id == id }
}
