package com.meshlit.core.inference.importers

/**
 * A model file resolved from an external registry. Pure data — it
 * does not start a download.
 *
 * Lives alongside the cluster's [com.meshlit.core.inference.cluster.ClusterStorageIncubator.ModelSource]
 * which is a *bundled* / *catalog* model ready to load. An
 * [ImportedModelSource] is what the importer hands back to the UI
 * after the user resolves a HuggingFace / Ollama / GitHub URL; the
 * UI then converts it into a catalog entry that the cluster layer
 * ingests.
 */
data class ImportedModelSource(
    val displayName: String,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long?,
    val format: ImportedModelFormat,
)

enum class ImportedModelFormat {
    GGUF,
    GGML,
    ONNX,
    UNKNOWN;

    companion object {
        /** Best-effort detection from a filename. */
        fun fromFileName(name: String): ImportedModelFormat {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".gguf") -> GGUF
                lower.endsWith(".ggml") -> GGML
                lower.endsWith(".onnx") -> ONNX
                else -> UNKNOWN
            }
        }
    }
}
