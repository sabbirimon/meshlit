package com.meshlit.core.inference.importers

/**
 * One snapshot of a streaming HTTP download's progress.
 *
 * Emitted by [HttpStreamDownloader] (and forwarded by
 * [MultiSourceDownloader]) so the UI can render a progress
 * bar with both bytes-received and bytes-per-second.
 *
 * `totalBytes` is `-1L` when the upstream didn't send a
 * `Content-Length` header (chunked transfer, mirror
 * redirects, etc.). In that case the UI is expected to fall
 * back to an indeterminate shimmer bar.
 */
data class DownloadProgress(
    val receivedBytes: Long,
    val totalBytes: Long,
    val fraction: Float,
    val bytesPerSec: Long,
)
