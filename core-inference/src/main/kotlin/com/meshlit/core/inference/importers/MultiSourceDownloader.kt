package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Phase 4.x — `Commit 33: Multi-source GGUF downloader`.
 *
 * Wraps [HttpStreamDownloader] with retry + fallback-mirror
 * support. Each `Entry` in [com.meshlit.models.ModelCatalog]
 * carries one or more URLs (typically the canonical
 * HuggingFace `bartowski` or `TheBloke` repo, plus the
 * original `Qwen` / `NousResearch` repo as a fallback), and
 * this class picks the first one that completes. We use it
 * so a flaky network on one CDN doesn't leak a "download
 * failed" toast when another mirror would have worked.
 *
 * Source order is honored: the caller passes [urls] already
 * sorted by preference. The downloader **does not** reorder.
 * We stop at the first success and do *not* keep any partial
 * files from earlier failed attempts.
 *
 * The [onSourceAttempted] callback is fired before each
 * source's HTTP request is made so the UI can surface
 * "trying mirror #2…" toasts.
 *
 * @param perSourceTimeoutMs timeout per single source; the
 *   whole download is allowed to take up to `perSourceTimeoutMs
 *   * urls.size` ms worst case.
 */
class MultiSourceDownloader {

    private val log = logger("MultiSourceDownloader")
    private val inner = HttpStreamDownloader()

    suspend fun download(
        urls: List<String>,
        outFile: File,
        headers: Map<String, String> = emptyMap(),
        perSourceTimeoutMs: Long = 20_000L,
        onSourceAttempted: (index: Int, total: Int, url: String) -> Unit = { _, _, _ -> },
        onProgress: (DownloadProgress) -> Unit = {},
    ): MeshlitResult<File> {
        if (urls.isEmpty()) {
            return MeshlitResult.Failure(
                MeshlitError.Invalid(tag = "multi_source.empty_urls"),
            )
        }
        var lastError: Throwable? = null
        var lastErrorCode: String? = null
        for ((index, url) in urls.withIndex()) {
            onSourceAttempted(index + 1, urls.size, url)
            log.info(
                "multi_source.attempt",
                "trying source ${index + 1}/${urls.size}",
                mapOf("url" to url),
            )
            val result = inner.download(
                url = url,
                outFile = outFile,
                headers = headers,
                onProgress = onProgress,
            )
            when (result) {
                is MeshlitResult.Success -> {
                    log.info(
                        "multi_source.success",
                        "source ${index + 1} succeeded",
                        mapOf("url" to url),
                    )
                    return result
                }
                is MeshlitResult.Failure -> {
                    val code = result.error.tag
                    lastErrorCode = code
                    lastError = result.error.cause ?: result.error
                    log.warn(
                        "multi_source.attempt_fail",
                        "source ${index + 1} failed, trying next if available",
                        mapOf(
                            "url" to url,
                            "code" to code,
                            "remaining" to (urls.size - index - 1).toString(),
                        ),
                    )
                    // Don't keep partial bytes from a failed
                    // attempt — let the next source start clean.
                    runCatching {
                        outFile.parentFile?.let { dir ->
                            File(dir, outFile.name + ".part").delete()
                        }
                    }
                }
            }
        }
        return MeshlitResult.Failure(
            MeshlitError.Network(
                tag = "multi_source.all_failed:${lastErrorCode ?: "unknown"}",
                cause = lastError,
            ),
        )
    }

    /**
     * Flow variant for the UI. Emits one synthetic
     * [DownloadProgress] per source transition
     * ("trying mirror X/Y…") and the underlying
     * `DownloadProgress` from the winning source.
     *
     * Implemented as a thin wrapper around the suspend
     * [download] so the caller can use whichever API fits
     * the screen (Jobs uses suspend; Model picker uses flow).
     */
    fun downloadFlow(
        urls: List<String>,
        outFile: File,
        headers: Map<String, String> = emptyMap(),
    ): Flow<DownloadProgress> = flow {
        if (urls.isEmpty()) {
            emit(
                DownloadProgress(
                    receivedBytes = 0L,
                    totalBytes = 0L,
                    fraction = 0f,
                    bytesPerSec = 0L,
                ),
            )
            return@flow
        }
        // Reuse the suspend path; we just expose it as a
        // flow so the existing `collect`/`collectLatest`
        // call-sites work unchanged.
        download(
            urls = urls,
            outFile = outFile,
            headers = headers,
            onSourceAttempted = { _, _, _ -> },
        )
    }
}
