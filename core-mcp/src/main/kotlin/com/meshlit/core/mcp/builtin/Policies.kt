package com.meshlit.core.mcp.builtin

import java.io.File

/**
 * Path-based read policy used by [FilesListTool] and [FilesReadTool].
 * Decisions are derived from `allowedRoots` — every requested path
 * must canonical-resolve into one of the roots. Symlinks that escape
 * the roots are denied.
 *
 * The policy is intentionally simple: an empty `allowedRoots` set
 * denies everything, which makes tests trivially safe (a
 * `FileSystemPolicy()` denies all reads).
 */
class FileSystemPolicy(
    private val allowedRoots: List<File> = emptyList(),
) {
    sealed class Decision {
        object Allow : Decision()
        data class Deny(val reason: String) : Decision()
    }

    fun checkRead(target: File): Decision {
        if (allowedRoots.isEmpty()) return Decision.Deny("no allowed roots configured")
        val canonical = runCatching { target.canonicalPath }.getOrNull()
            ?: return Decision.Deny("could not canonicalize $target")
        val matches = allowedRoots.any { root ->
            val rootCanonical = runCatching { root.canonicalPath }.getOrNull()
                ?: return@any false
            canonical == rootCanonical || canonical.startsWith("$rootCanonical/")
        }
        return if (matches) Decision.Allow
        else Decision.Deny("path is outside allowed roots")
    }
}

/**
 * Command-based policy used by [ShellExecTool]. The default policy
 * is a static allowlist of read-only binaries — no `rm`, no
 * `dd`, no `chmod`. The arg-level checks are intentionally
 * minimal: the allowlist is the gatekeeper, the args are just
 * passed through. Tighten per-deployment as needed.
 */
class ShellPolicy(
    private val allowlist: Set<String> = DEFAULT_ALLOWLIST,
) {
    sealed class Decision {
        object Allow : Decision()
        data class Deny(val reason: String) : Decision()
    }

    fun check(command: String, args: List<String>): Decision {
        val base = File(command).name
        if (base !in allowlist) {
            return Decision.Deny("command '$base' is not in the allowlist")
        }
        // Reject shell metacharacters in args. The allowlist should
        // never hit a real shell, but a future tool that wraps
        // `bash -c "$arg"` would silently inherit this protection.
        for (a in args) {
            if (a.any { it in SHELL_METACHARACTERS }) {
                return Decision.Deny("argument contains shell metacharacters")
            }
        }
        return Decision.Allow
    }

    companion object {
        val DEFAULT_ALLOWLIST: Set<String> = setOf(
            "echo", "cat", "ls", "wc", "head", "tail",
            "grep", "find", "stat", "pwd", "whoami",
            "uname", "date", "env",
        )

        private val SHELL_METACHARACTERS: Set<Char> = setOf(
            '|', '&', ';', '$', '`', '>', '<', '\n', '\r', '*', '?', '~',
        )
    }
}
