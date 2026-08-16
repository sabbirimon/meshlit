package com.meshlit.core.inference

import java.net.URI

/**
 * Resolves public GitHub file links to their raw download URL.
 *
 * Supported forms:
 *  - https://github.com/owner/repo/blob/main/path/model.gguf
 *  - https://github.com/owner/repo/raw/main/path/model.gguf
 *  - https://raw.githubusercontent.com/owner/repo/main/path/model.gguf
 *
 * This is deliberately a URL resolver, not a Git client: it does not
 * clone repositories, execute git, or handle private/authenticated
 * repositories. That keeps model import safe and works on every Android
 * ABI without shipping a native Git implementation.
 */
object GitHubResolver {

    data class ResolvedFile(
        val rawUrl: String,
        val owner: String,
        val repository: String,
        val reference: String,
        val path: String,
    )

    /**
     * Return a raw GitHub URL for a public model file, or null when the
     * input is not a supported HTTPS GitHub file URL.
     */
    fun resolve(url: String): ResolvedFile? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme.lowercase() != "https") return null

        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

        return when {
            host == "raw.githubusercontent.com" && segments.size >= 4 -> {
                val owner = segments[0]
                val repository = segments[1]
                val reference = segments[2]
                val path = segments.drop(3).joinToString("/")
                if (!isSafePart(owner) || !isSafePart(repository) || path.isBlank()) null
                else ResolvedFile(
                    rawUrl = "https://raw.githubusercontent.com/$owner/$repository/$reference/$path",
                    owner = owner,
                    repository = repository,
                    reference = reference,
                    path = path,
                )
            }
            host == "github.com" && segments.size >= 5 &&
                (segments[2] == "blob" || segments[2] == "raw") -> {
                val owner = segments[0]
                val repository = segments[1]
                val reference = segments[3]
                val path = segments.drop(4).joinToString("/")
                if (!isSafePart(owner) || !isSafePart(repository) || path.isBlank()) null
                else ResolvedFile(
                    rawUrl = "https://raw.githubusercontent.com/$owner/$repository/$reference/$path",
                    owner = owner,
                    repository = repository,
                    reference = reference,
                    path = path,
                )
            }
            else -> null
        }
    }

    private fun isSafePart(value: String): Boolean =
        value.isNotBlank() && value.none { it == '?' || it == '#' || it == '\\' }
}
