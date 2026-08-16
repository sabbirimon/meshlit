package com.meshlit.core.inference

import android.content.Context
import java.io.File

/**
 * Pure predicates the Models picker needs to render each row.
 *
 * Mirrors the extension predicates on the SDK's `RAModelInfo`
 * upstream (`com.runanywhere.runanywhereai.ui.screens.models`):
 *
 *  - `isBuiltIn` — is the model bundled with the APK?
 *  - `isDownloadedOnDisk` — is a file present at the canonical
 *    on-device path?
 *  - `isVisibleForNativeNpuCatalog` — should the row show in the
 *    active framework filter?
 *
 * We don't have direct access to the SDK's `RAModelInfo` extensions
 * from `core-inference` (the vendored SDK module is a separate
 * `:runanywhere-kotlin` Gradle module and the proto fields are
 * read-only). So we reimplement the same logic against our own
 * `BundledModelInstaller` + on-disk file presence checks.
 */
object ModelPredicates {

    /**
     * Bundled ids — mirrors the `BUNDLED_IDS` set in
     * [RunAnywhereCatalogEngine]. Kept in sync by hand; if you add
     * a new bundled asset, update both.
     */
    val BUNDLED_IDS: Set<String> = setOf(
        "smollm2-360m-instruct-q8_0",
    )

    /** True when the given id matches a known bundled asset. */
    fun isBuiltIn(id: String): Boolean = id in BUNDLED_IDS

    /**
     * True when an .gguf file exists on disk for the given id.
     * Looks under `filesDir/imported-models/<id>.gguf` — the path
     * the alternative-models installer uses. Bundled models extract
     * to the same directory on first run, so the same predicate
     * covers both.
     */
    fun isDownloadedOnDisk(context: Context, id: String): Boolean {
        val file = importedModelFile(context, id)
        return file.exists() && file.length() > 0L
    }

    /** Canonical on-disk file for an imported or bundled model. */
    fun importedModelFile(context: Context, id: String): File =
        File(File(context.filesDir, "imported-models"), "$id.gguf")

    /**
     * True when the model should be visible in the active framework
     * filter. Today only `LLAMA_CPP` is supported; the predicate is
     * a placeholder so the filter row can be added without a real
     * NPU integration yet.
     *
     * When the framework filter is `NPU`, models tagged with the
     * NPU classification (`metadata.npu = true`) are visible;
     * otherwise all models are visible.
     */
    fun isVisibleForNativeNpuCatalog(
        infoNpuTagged: Boolean,
        activeFramework: ActiveFramework,
    ): Boolean = when (activeFramework) {
        ActiveFramework.ALL -> true
        ActiveFramework.LLAMA_CPP -> true
        ActiveFramework.NPU -> infoNpuTagged
    }

    /** Active framework filter — mirrors `ModelSelectionViewModel`'s
     *  backend filter. Today only `LLAMA_CPP` and `NPU` are real;
     *  `ALL` is the default. */
    enum class ActiveFramework { ALL, LLAMA_CPP, NPU }
}