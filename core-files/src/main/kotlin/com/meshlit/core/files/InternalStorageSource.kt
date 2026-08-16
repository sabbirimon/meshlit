package com.meshlit.core.files

import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [FileBrowserSource] backed by the local Android filesystem. Honors a
 * `.nomedia` sentinel in any directory (skips it), skips hidden files
 * (`name.startsWith('.')`), and caps traversal at 4 096 entries per
 * directory to keep the UI snappy on phones with deeply nested
 * download caches.
 *
 * Path validation: paths are rejected unless they're inside one of
 * the supplied [allowedRoots] (typically the app's `filesDir` and
 * `cacheDir`). This prevents callers from browsing outside the app
 * sandbox via the in-app browser.
 */
class InternalStorageSource(
    private val allowedRoots: List<File>,
    private val maxEntriesPerDir: Int = 4096,
) : FileBrowserSource {

    private val log = logger("InternalStorageSource")

    override suspend fun list(dir: String): List<FileBrowserEntry> = withContext(Dispatchers.IO) {
        val target = File(dir)
        if (!isInsideAllowedRoots(target)) {
            log.warn("browser.deny", "refused path outside roots", mapOf("path" to dir))
            return@withContext emptyList()
        }
        if (!target.isDirectory) return@withContext emptyList()
        val children = target.listFiles()?.take(maxEntriesPerDir).orEmpty()
        children.asSequence()
            .filter { !it.name.startsWith(".") }
            .filter { it.name != NOMEDIA_PARENT }
            .map { entry ->
                FileBrowserEntry(
                    path = entry.absolutePath,
                    name = entry.name,
                    sizeBytes = if (entry.isFile) entry.length() else 0L,
                    isDirectory = entry.isDirectory,
                    mimeGuess = if (entry.isFile) guessMime(entry.name) else "inode/directory",
                )
            }
            .sortedWith(compareByDescending<FileBrowserEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    private fun isInsideAllowedRoots(target: File): Boolean {
        val canonical = runCatching { target.canonicalPath }.getOrNull() ?: return false
        return allowedRoots.any { root ->
            val rootCanonical = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical == rootCanonical || canonical.startsWith("$rootCanonical/")
        }
    }

    /** Public variant of [isInsideAllowedRoots] — exposed so the
     *  controller's write operations can validate paths without
     *  duplicating the canonical-path normalization. */
    override fun isAllowed(path: String): Boolean =
        isInsideAllowedRoots(File(path))

    companion object {
        private const val NOMEDIA_PARENT = ".nomedia"
    }
}
