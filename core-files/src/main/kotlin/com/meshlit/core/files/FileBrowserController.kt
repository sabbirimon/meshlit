package com.meshlit.core.files

import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure UI-state controller for the in-app file browser. Holds the
 * current directory stack and the most recent listing so a Compose
 * screen can render without juggling state locally. Designed for
 * testability: the only collaborator is [FileBrowserSource], and
 * the controller never touches Android APIs directly.
 *
 * Operations:
 *  - [navigateTo] / [navigateUp] / [refresh] — the read / nav path
 *  - [copy] / [move] / [delete] / [mkdir] — the write path; each
 *    returns a `Result` so the screen can show the failure inline
 *    without surfacing the exception to the user.
 *
 * All write operations enforce the source's `allowedRoots` policy —
 * paths outside `filesDir` / `cacheDir` (for [InternalStorageSource])
 * are silently rejected with a typed error in the `Result.failure`.
 */
class FileBrowserController(
    private val source: FileBrowserSource,
    initialDir: String,
) {
    private val log = logger("FileBrowserController")

    private val _state = MutableStateFlow(FileBrowserState.empty(initialDir))
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    /** Push [dir] onto the stack and refresh. */
    suspend fun navigateTo(dir: String) {
        val entries = source.list(dir)
        val currentStack = _state.value.stack
        val nextStack = if (currentStack.lastOrNull() == dir) currentStack else currentStack + dir
        _state.value = _state.value.copy(
            currentDir = dir,
            entries = entries,
            stack = nextStack,
            lastError = null,
        )
        log.info("browser.nav", "navigated", mapOf("dir" to dir, "entries" to entries.size))
    }

    /** Pop the top of the stack. No-op at the root. */
    suspend fun navigateUp() {
        val stack = _state.value.stack
        if (stack.size <= 1) return
        val popped = stack.dropLast(1)
        val newTop = popped.last()
        val entries = source.list(newTop)
        _state.value = _state.value.copy(
            currentDir = newTop,
            entries = entries,
            stack = popped,
            lastError = null,
        )
    }

    /** Refresh the current directory without changing the stack. */
    suspend fun refresh() {
        val dir = _state.value.currentDir
        val entries = source.list(dir)
        _state.value = _state.value.copy(entries = entries, lastError = null)
    }

    /**
     * Copy a file or directory inside the same source. The
     * destination lives under [destDir]; the file name is preserved.
     * Returns the resolved destination path on success, or a
     * human-readable error string on failure.
     *
     * The implementation delegates to `File.copyRecursively` /
     * `File.copyTo` and enforces the source's `allowedRoots`
     * policy on both endpoints so the controller can't be used
     * as a sandbox-escape primitive.
     */
    suspend fun copy(srcPath: String, destDir: String): Result<String> {
        return runCatching {
            val src = java.io.File(srcPath)
            require(src.exists()) { "source not found: $srcPath" }
            require(source.isAllowed(src.absolutePath)) { "source outside allowed roots: $srcPath" }
            val dest = java.io.File(destDir, src.name)
            require(!dest.exists()) { "destination already exists: ${dest.absolutePath}" }
            require(source.isAllowed(dest.absolutePath)) { "destination outside allowed roots: ${dest.absolutePath}" }
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
            } else {
                src.copyTo(dest, overwrite = false)
            }
            log.info("browser.copy", "ok", mapOf("src" to srcPath, "dest" to dest.absolutePath))
            dest.absolutePath
        }.onFailure { t ->
            log.warn("browser.copy", "failed", mapOf("src" to srcPath, "error" to (t.message ?: "unknown")))
        }
    }

    /**
     * Move a file or directory. Implemented as `copy` + `delete`
     * so we get a single failure mode if the copy can't complete —
     * moving via `renameTo` would silently fail across mount points
     * and Storage-Volume roots.
     */
    suspend fun move(srcPath: String, destDir: String): Result<String> {
        val copied = copy(srcPath, destDir)
        if (copied.isFailure) return copied
        return runCatching {
            val src = java.io.File(srcPath)
            require(src.deleteRecursively()) { "delete after copy failed: $srcPath" }
            log.info("browser.move", "ok", mapOf("src" to srcPath, "dest" to destDir))
            copied.getOrThrow()
        }.onFailure { t ->
            log.warn("browser.move", "failed", mapOf("src" to srcPath, "error" to (t.message ?: "unknown")))
        }
    }

    /**
     * Delete a file or directory. Returns `Unit` on success; the
     * caller should call [refresh] afterwards so the row disappears
     * from the listing.
     */
    suspend fun delete(path: String): Result<Unit> {
        return runCatching {
            require(source.isAllowed(path)) { "outside allowed roots: $path" }
            val file = java.io.File(path)
            require(file.exists()) { "not found: $path" }
            require(file.deleteRecursively()) { "deleteRecursively returned false" }
            log.info("browser.delete", "ok", mapOf("path" to path))
        }.onFailure { t ->
            log.warn("browser.delete", "failed", mapOf("path" to path, "error" to (t.message ?: "unknown")))
        }
    }

    /**
     * Create a new empty directory under [parent] and refresh.
     * Returns the new path on success.
     */
    suspend fun mkdir(parent: String, name: String): Result<String> {
        return runCatching {
            require(name.isNotBlank()) { "empty name" }
            require(!name.contains('/') && !name.contains('\u0000')) { "invalid name" }
            val target = java.io.File(parent, name)
            require(source.isAllowed(target.absolutePath)) { "outside allowed roots: ${target.absolutePath}" }
            require(target.mkdir()) { "mkdir failed (already exists?)" }
            log.info("browser.mkdir", "ok", mapOf("path" to target.absolutePath))
            target.absolutePath
        }.onFailure { t ->
            log.warn("browser.mkdir", "failed", mapOf("parent" to parent, "error" to (t.message ?: "unknown")))
        }
    }
}

data class FileBrowserState(
    val currentDir: String,
    val entries: List<FileBrowserEntry>,
    val stack: List<String>,
    val lastError: String? = null,
) {
    val isAtRoot: Boolean get() = stack.size <= 1

    companion object {
        fun empty(initialDir: String): FileBrowserState =
            FileBrowserState(
                currentDir = initialDir,
                entries = emptyList(),
                stack = listOf(initialDir),
            )
    }
}