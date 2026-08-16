package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.logger
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.listModels
import com.runanywhere.sdk.public.extensions.refreshModelRegistry
import com.runanywhere.sdk.public.extensions.downloadedModels
import ai.runanywhere.proto.v1.ModelInfo
import ai.runanywhere.proto.v1.ModelListRequest
import ai.runanywhere.proto.v1.ModelQuery
import ai.runanywhere.proto.v1.ModelCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 2.x — dynamic model catalog powered by the RunAnywhere SDK's
 * model registry.
 *
 * Replaces the hand-curated [com.meshlit.inference.RunAnywhereCatalog]
 * with a live view of the SDK's `RunAnywhere.listModels()` and
 * `RunAnywhere.refreshModelRegistry()` calls. Used by the new
 * `CatalogScreen` route on the bottom nav.
 *
 * Why both a `StateFlow` and a `refresh()`:
 *
 *  - The StateFlow is the single source of truth for the UI. It
 *    starts populated with [offlineFallback] (the existing curated
 *    list) so the screen renders *something* immediately on first
 *    launch.
 *  - `refresh()` re-runs `refreshModelRegistry(forceRefresh = true)`
 *    and `listModels()` on [dispatcher], then replaces the StateFlow.
 *    If the registry call throws (no network, registry down) the
 *    StateFlow keeps the existing list — never goes empty.
 *
 * Concurrency:
 *
 *  - Multiple `refresh()` calls are serialized through [refreshLock].
 *    Without this, two parallel tabs pulling the screen could both
 *    fire network calls and produce racy StateFlow updates.
 *  - Reads (`observe()`) are lock-free — they just return the
 *    StateFlow.
 *
 * Failure modes:
 *
 *  - SDK not initialized  → leaves StateFlow at offline fallback,
 *                            returns `MeshlitResult.Failure(Invalid)`.
 *  - Network unreachable  → same as above; logs a warning.
 *  - Empty list returned  → keeps the offline fallback rather than
 *                            showing a blank screen.
 *
 * Why we mirror the existing `RunAnywhereCatalog.Entry` field shape:
 * the Catalog screen's row could later be shared with the existing
 * Models screen `RunAnywhereCatalogCard`. Until then the two coexist
 * and the curated card remains the safe default.
 */
class RunAnywhereCatalogEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Fallback list served when the SDK's registry is unreachable.
     * Defaults to the curated list from
     * [com.meshlit.inference.RunAnywhereCatalog] (which already lives
     * in the `:app` module). The caller is expected to inject it
     * rather than hard-coding here so the engine doesn't depend on
     * `:app`.
     */
    private val offlineFallback: () -> List<Entry>,
) {

    /**
     * Catalog row in the shape the UI expects. Mirrors the field
     * names of `com.meshlit.models.ModelCatalog.Entry` and
     * `com.meshlit.inference.RunAnywhereCatalog.Entry` so the
     * Compose row could be reused across screens.
     *
     * @property id SDK canonical id used by
     *   `RunAnywhere.downloadModelStream(RAModelInfo(id = …))`.
     * @property displayName shown in the Catalog row.
     * @property origin best-guess country flag label — derived from
     *   the SDK's `ModelSource` enum when available.
     * @property license short license tag.
     * @property family model family name.
     * @property approxSizeMb approximate download size in MB.
     * @property language coverage hint.
     * @property strengths short list of tags.
     */
    /**
     * Architecture marker. The row UI surfaces this as a small
     * "MoE" or "Dense" badge next to the model name.
     *
     *  - DENSE — single FFN per layer, every parameter runs every
     *    token (Qwen 2.5 1.5B, Llama 3.2 1B, Phi-3-mini, …)
     *  - MOE   — multiple experts per layer, only the active ones
     *    run per token (Qwen3-A3B, Granite-4-Tiny-MoE, Mixtral, …)
     */
    enum class Architecture { DENSE, MOE }

    /**
     * Quantization tier. The row UI shows this as a Q-tag.
     *  - Q8_0  — high quality, ~2x model size
     *  - Q4_K_M — conventional "small + decent" sweet spot
     *  - Q2_K  — aggressive; only on >12 GB-RAM devices
     *  - F16 / F32 — full precision, only on dev / test runs
     *  - UNKNOWN — SDK returned no quant hint
     */
    enum class Quant { Q8_0, Q4_K_M, Q2_K, F16, F32, UNKNOWN }

    /**
     * Coarse size class. The row UI shows a "S / M / L" tag so the
     * user can spot at a glance whether the model will fit their
     * phone. Boundaries are intentionally generous.
     *  - SMALL   — ≤ 500 MB (SmolLM2 360M, Granite-Tiny-MoE)
     *  - MEDIUM  — 500 MB – 2.5 GB (Qwen 2.5 1.5B, Llama 3.2 1B, Phi-3-mini)
     *  - LARGE   — 2.5 GB – 8 GB (Qwen 2.5 3B, …)
     *  - HUGE    — > 8 GB (Qwen3-A3B, Mixtral-8x7B) — needs sharding
     */
    enum class SizeClass { SMALL, MEDIUM, LARGE, HUGE }

    /** Lightweight badge descriptor returned by [Entry.badges]. Each
     *  UI badge has a short text label and a Material color hint so
     *  the row can render it consistently without re-decoding the
     *  raw entry fields. */
    data class Badge(val label: String, val tone: Tone) {
        enum class Tone { INFO, SUCCESS, WARN, ERROR, ACCENT }
    }

    data class Entry(
        val id: String,
        val displayName: String,
        val origin: String,
        val license: String,
        val family: String,
        val approxSizeMb: Long,
        val language: String,
        val strengths: List<String>,
        val architecture: Architecture = Architecture.DENSE,
        val quant: Quant = Quant.UNKNOWN,
        val sizeClass: SizeClass = SizeClass.MEDIUM,
        val bundled: Boolean = false,
    ) {
        /** Computed list of badges for this row. UI calls this once
         *  per compose and renders the returned list as a small
         *  `Row` of colored chips. Order is stable so the UI doesn't
         *  shimmer on recomposition. */
        fun badges(): List<Badge> = buildList {
            // Architecture is the first thing a user looks at.
            add(Badge(label = architecture.name, tone = if (architecture == Architecture.MOE) Badge.Tone.ACCENT else Badge.Tone.INFO))
            // Quantization tag.
            if (quant != Quant.UNKNOWN) add(Badge(label = quant.name.replace('_', '-'), tone = Badge.Tone.INFO))
            // Size class.
            add(
                Badge(
                    label = sizeClass.name,
                    tone = when (sizeClass) {
                        SizeClass.SMALL -> Badge.Tone.SUCCESS
                        SizeClass.MEDIUM -> Badge.Tone.INFO
                        SizeClass.LARGE -> Badge.Tone.WARN
                        SizeClass.HUGE -> Badge.Tone.ERROR
                    },
                ),
            )
            // Multilingual coverage.
            if (language.startsWith("EN/") || language.contains("multilingual", ignoreCase = true)) {
                if ("multilingual" in language.lowercase() || "/" in language) {
                    add(Badge(label = "multi", tone = Badge.Tone.INFO))
                }
            }
            // Bundled-with-APK tag — shown only when true.
            if (bundled) add(Badge(label = "bundled", tone = Badge.Tone.SUCCESS))
            // Strengths that look like a badge already (e.g. "fast",
            // "reasoning", "edge") — skip if already implied.
            strengths.forEach { tag ->
                when (tag) {
                    "fast", "starter", "edge", "reasoning" -> add(Badge(label = tag, tone = if (tag == "reasoning") Badge.Tone.ACCENT else Badge.Tone.INFO))
                }
            }
        }
    }

    private val log = logger("RunAnywhereCatalogEngine")

    private val _entries = MutableStateFlow<List<Entry>>(offlineFallback())
    /** Observable list of catalog entries, served live by the SDK or
     *  the offline fallback when the SDK is unreachable. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Whether the current entries list came from the SDK registry
     *  (true) or the offline fallback (false). The Catalog screen
     *  surfaces this as a thin yellow banner when false. */
    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val refreshLock = Mutex()

    /**
     * Refresh the catalog from the SDK. Safe to call repeatedly —
     * concurrent calls are serialized. Returns the count of entries
     * the SDK returned, or a [MeshlitResult.Failure] if the SDK is
     * unreachable (the StateFlow is left unchanged in that case).
     */
    suspend fun refresh(): MeshlitResult<Int> = refreshLock.withLock {
        withContext(dispatcher) {
            try {
                // Pull the registry. `rescanLocal = true` forces
                // a fresh local-file scan (the cheap half); the
                // `includeRemoteCatalog` flag hits the RunAnywhere
                // CDN for the live model list. Both default to
                // false in the SDK, but the user tapped Refresh
                // explicitly so we want both.
                RunAnywhere.refreshModelRegistry(
                    rescanLocal = true,
                    includeRemoteCatalog = true,
                    pruneOrphans = false,
                )
                // Then list what's available — defaults to all
                // categories, no filter.
                val response = RunAnywhere.listModels(
                    request = ModelListRequest(
                        query = ModelQuery(
                            category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                        ),
                        include_counts = true,
                    ),
                )
                if (!response.success) {
                    log.warn(
                        "runanywhere.catalog.list_failed",
                        "SDK listModels returned failure",
                        mapOf("error" to (response.error_message ?: "unknown")),
                    )
                    return@withContext MeshlitResult.Failure(
                        MeshlitError.Native(
                            "runanywhere.catalog.list_failed:${response.error_message ?: "unknown"}",
                        ),
                    )
                }
                val mapped = response.models?.models.orEmpty().mapNotNull { info ->
                    adaptModelInfo(info)
                }
                if (mapped.isEmpty()) {
                    log.warn(
                        "runanywhere.catalog.empty",
                        "SDK returned an empty catalog",
                        emptyMap(),
                    )
                    // Don't replace the StateFlow — keep the fallback
                    // so the UI never goes blank.
                    return@withContext MeshlitResult.Success(0)
                }
                _entries.value = mapped
                _live.value = true
                log.info(
                    "runanywhere.catalog.refreshed",
                    "Catalog refreshed from SDK",
                    mapOf("count" to mapped.size),
                )
                MeshlitResult.Success(mapped.size)
            } catch (t: Throwable) {
                log.warn(
                    "runanywhere.catalog.refresh_failed",
                    "Catalog refresh failed; keeping fallback list",
                    mapOf("error" to (t.message ?: t.javaClass.simpleName)),
                )
                MeshlitResult.Failure(
                    MeshlitError.Native(
                        "runanywhere.catalog.refresh_failed:${t.message ?: t.javaClass.simpleName}",
                        t,
                    ),
                )
            }
        }
    }

    /** Lookup by SDK id. Returns `null` if not in the current list. */
    fun find(id: String): Entry? = _entries.value.firstOrNull { it.id == id }

    /**
     * Adapt an SDK [ModelInfo] proto into our UI-typed [Entry]. The
     * SDK doesn't carry every field we want (license, language,
     * strengths are empty on most registry rows), so we infer what
     * we can from `id`/`name` and leave the rest as sensible
     * defaults.
     */
    private fun adaptModelInfo(info: ModelInfo): Entry? {
        val id = info.id.takeIf { it.isNotBlank() } ?: return null
        val name = info.name.takeIf { it.isNotBlank() } ?: id
        val approxMb = info.download_size_bytes.takeIf { it > 0 }?.div(1_048_576L) ?: 0L
        val family = inferFamily(name)
        return Entry(
            id = id,
            displayName = name,
            origin = inferOrigin(id, name),
            license = info.metadata?.license.orEmpty().ifBlank { "Unknown" },
            family = family,
            approxSizeMb = approxMb,
            language = inferLanguage(family),
            strengths = inferStrengths(family, approxMb),
            architecture = inferArchitecture(name, family),
            quant = inferQuant(name),
            sizeClass = inferSizeClass(approxMb),
            bundled = id in BUNDLED_IDS,
        )
    }

    /** Architecture inference — most families default to DENSE; only
     *  the well-known MoE names ("MoE", "Mixtral", "Granite-*-MoE",
     *  Qwen3-A3B) flip the bit. Conservative on purpose — getting
     *  this wrong is more confusing than showing a wrong badge. */
    private fun inferArchitecture(name: String, family: String): Architecture {
        val lower = name.lowercase()
        return when {
            "moe" in lower -> Architecture.MOE
            "mixtral" in lower -> Architecture.MOE
            family == "Granite" && "moe" in lower -> Architecture.MOE
            "a3b" in lower && "qwen" in lower -> Architecture.MOE
            else -> Architecture.DENSE
        }
    }

    /** Quant inference from the displayName. Looks for the canonical
     *  GGUF quant tags (`Q8_0`, `Q4_K_M`, `Q2_K`) and the floating-
     *  point formats (`F16`, `F32`). Falls back to UNKNOWN when the
     *  SDK doesn't tell us — the row renders without a quant chip. */
    private fun inferQuant(name: String): Quant = when {
        "Q8_0" in name -> Quant.Q8_0
        "Q4_K_M" in name -> Quant.Q4_K_M
        "Q2_K" in name -> Quant.Q2_K
        "F16" in name -> Quant.F16
        "F32" in name -> Quant.F32
        else -> Quant.UNKNOWN
    }

    /** Size class — boundaries intentionally generous:
     *  - SMALL ≤ 500 MB (SmolLM2 360M, Granite-Tiny-MoE)
     *  - MEDIUM 500 MB – 2.5 GB (Qwen 2.5 1.5B, Llama 3.2 1B)
     *  - LARGE 2.5 GB – 8 GB (Qwen 2.5 3B, Phi-3-mini)
     *  - HUGE > 8 GB (Qwen3-30B-A3B, Mixtral-8x7B) — needs sharding
     *
     *  0 MB (unknown size) maps to MEDIUM so the row doesn't render
     *  a misleading "SMALL" chip. */
    private fun inferSizeClass(approxMb: Long): SizeClass = when {
        approxMb <= 0L -> SizeClass.MEDIUM
        approxMb <= 500L -> SizeClass.SMALL
        approxMb <= 2_500L -> SizeClass.MEDIUM
        approxMb <= 8_000L -> SizeClass.LARGE
        else -> SizeClass.HUGE
    }

    /** Best-guess family from the model name. Falls back to
     *  "Unknown" when the name doesn't match a known pattern. */
    private fun inferFamily(name: String): String {
        val lower = name.lowercase()
        return when {
            "qwen" in lower -> "Qwen"
            "llama" in lower -> "Llama"
            "smol" in lower -> "SmolLM"
            "phi" in lower -> "Phi"
            "gemma" in lower -> "Gemma"
            "mistral" in lower -> "Mistral"
            "deepseek" in lower -> "DeepSeek"
            else -> "Unknown"
        }
    }

    /** Origin hint derived from the model family. Not authoritative —
     *  the SDK doesn't expose a country code in `ModelInfo`. The
     *  curated list mirrors what Hugging Face tags use, so the
     *  labels stay consistent across both catalog cards. */
    private fun inferOrigin(id: String, name: String): String {
        val family = inferFamily(name)
        return when (family) {
            "Qwen", "DeepSeek" -> "China"
            "Llama" -> "USA"
            "SmolLM", "Phi" -> "USA"
            "Gemma" -> "USA"
            "Mistral" -> "France"
            else -> "Unknown"
        }
    }

    /** Coverage hint derived from family. Curated families have
     *  documented multilingual coverage; fall back to "EN" for
     *  unknown families. */
    private fun inferLanguage(family: String): String = when (family) {
        "Qwen", "Llama", "Mistral", "Gemma" -> "EN/ZH/ES/FR/DE/…"
        "SmolLM", "Phi", "DeepSeek" -> "EN-first"
        else -> "EN"
    }

    /** Strengths derived from family + size. Small SmolLM is a
     *  starter; bigger models get reasoning/general. */
    private fun inferStrengths(family: String, approxMb: Long): List<String> {
        val tags = mutableListOf<String>()
        if (family == "SmolLM" || approxMb in 0..500) tags += "fast"
        if (approxMb >= 1500) tags += "reasoning"
        if (tags.isEmpty()) tags += "general"
        return tags
    }

    companion object {
        /** Set of SDK canonical ids that ship inside the APK as
         *  bundled assets. The engine flips `bundled = true` for
         *  these so the Catalog row can show a green "bundled"
         *  badge and downstream installers can skip the network
         *  download for them. Add new entries here when a model
         *  is added to `assets/models/`. */
        val BUNDLED_IDS: Set<String> = setOf(
            // The single starter model — SmolLM2-360M-Instruct
            // Q8_0. The APK ships this asset (≈ 368 MB) so first-
            // launch has a working FGS within seconds of cold
            // start. The asset basename matches the SDK's
            // `DEFAULT_MODEL_ID` so the FGS auto-loads it
            // without a rename step.
            "smollm2-360m-instruct-q8_0",
        )

        /** Singleton holder used by [com.meshlit.MeshlitApplication] —
         *  one engine instance per process, mirroring
         *  `inferenceCoordinator.runAnywhereEngine()`. */
        private val INSTANCE = AtomicReference<RunAnywhereCatalogEngine?>(null)

        /**
         * Install a process-wide engine. Must be called from
         * `MeshlitApplication.onCreate` before any UI thread reads
         * [entries]. Subsequent calls are no-ops.
         */
        fun install(offlineFallback: () -> List<Entry>) {
            INSTANCE.compareAndSet(null, RunAnywhereCatalogEngine(offlineFallback = offlineFallback))
        }

        /** Get the process-wide engine. Throws if [install] hasn't
         *  been called yet — surfaces a startup-order bug at the
         *  call site rather than silently returning an empty list. */
        fun get(): RunAnywhereCatalogEngine =
            INSTANCE.get() ?: error(
                "RunAnywhereCatalogEngine not installed — call install() from MeshlitApplication.onCreate",
            )
    }
}
