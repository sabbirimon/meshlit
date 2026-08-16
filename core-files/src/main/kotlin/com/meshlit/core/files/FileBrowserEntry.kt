package com.meshlit.core.files

import kotlinx.serialization.Serializable

/**
 * One row of the in-app file browser. Pure data — neither the UI nor
 * the persistence layer need anything beyond [path] / [name] /
 * [sizeBytes] / [isDirectory] / [mimeGuess].
 *
 * [mimeGuess] is derived from the file extension via [guessMime] —
 * callers should treat it as advisory (no magic-byte inspection in
 * Phase 3).
 */
@Serializable
data class FileBrowserEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val mimeGuess: String,
)

/**
 * Pluggable source of [FileBrowserEntry] rows. The default in-app
 * implementation walks `filesDir` and `cacheDir`; a future SAF-backed
 * source could expose `content://` URIs without changing the UI.
 */
interface FileBrowserSource {
    suspend fun list(dir: String): List<FileBrowserEntry>

    /**
     * Authoritative gate — `true` only when [path] is inside the
     * source's allowed roots. The controller's write operations
     * (copy / move / delete / mkdir) call this before touching
     * the filesystem so a malicious screen can't escape the
     * sandbox via the file controller.
     *
     * Default impl returns `true` to keep the interface additive —
     * sandboxed sources (the only ones that should ever drive the
     * controller) override this.
     */
    fun isAllowed(path: String): Boolean = true
}

/** Best-effort MIME guess from a filename. Returns `application/octet-stream` when unknown. */
fun guessMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "gguf" -> "application/x-gguf"
        "onnx" -> "application/x-onnx"
        "safetensors" -> "application/x-safetensors"
        "json" -> "application/json"
        "txt", "md" -> "text/plain"
        "log" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "shard" -> "application/x-meshlit-shard"
        else -> "application/octet-stream"
    }
}
