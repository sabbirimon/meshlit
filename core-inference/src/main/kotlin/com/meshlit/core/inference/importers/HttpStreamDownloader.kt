package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Streams a single HTTP GET to a local file, emitting
 * [DownloadProgress] snapshots at ~250 ms intervals.
 *
 * We use OkHttp directly (no Retrofit/Moshi) because:
 *  - The downloader must work on every supported minSdk (24+),
 *    and OkHttp is pure Java (Ktor 3 client needs DEX 040).
 *  - We want explicit control over the `.part` file rename and
 *    the failure cleanup — no high-level library does exactly
 *    what [MultiSourceDownloader] needs.
 *
 * Wire-level behavior:
 *  - Atomic write: bytes go to `<outFile>.part`, then renamed
 *    on success. A failure (cancelled, network drop, non-2xx
 *    response) leaves the partial file on disk so the next
 *    source can resume from the same path.
 *  - Optional [headers] are passed through verbatim (the GGUF
 *    mirror endpoints we use send a `User-Agent` requirement).
 *  - 4xx/5xx is reported as [MeshlitError.Network] with the
 *    HTTP status in the `tag` so the UI can decide whether to
 *    retry (5xx → yes, 4xx → no).
 *
 * @see MultiSourceDownloader for the retry/multi-mirror wrapper.
 */
class HttpStreamDownloader {

    private val log = logger("HttpStreamDownloader")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Streams `url` to [outFile]. Returns [MeshlitResult.Success]
     * with the final [File] on 2xx, [MeshlitResult.Failure] on
     * any other outcome.
     */
    suspend fun download(
        url: String,
        outFile: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (DownloadProgress) -> Unit = {},
    ): MeshlitResult<File> = withContext(Dispatchers.IO) {
        val partFile = File(outFile.parentFile, outFile.name + ".part")
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext MeshlitResult.Failure(
                        MeshlitError.Network(
                            tag = "http.status.${response.code}",
                            cause = IOException("HTTP ${response.code} for $url"),
                        ),
                    )
                }
                val body = response.body
                    ?: return@withContext MeshlitResult.Failure(
                        MeshlitError.Network(
                            tag = "http.empty_body",
                            cause = IOException("empty response body for $url"),
                        ),
                    )

                val totalBytes = body.contentLength().takeIf { it >= 0 } ?: -1L
                var receivedBytes = 0L
                var lastEmitMs = 0L
                val startedAt = System.currentTimeMillis()

                body.byteStream().use { input ->
                    partFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            receivedBytes += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 250) {
                                val fraction = if (totalBytes > 0) {
                                    (receivedBytes.toDouble() / totalBytes.toDouble())
                                        .coerceIn(0.0, 1.0)
                                        .toFloat()
                                } else {
                                    0f
                                }
                                val bytesPerSec =
                                    (receivedBytes * 1000L) /
                                        (now - startedAt).coerceAtLeast(1L)
                                onProgress(
                                    DownloadProgress(
                                        receivedBytes = receivedBytes,
                                        totalBytes = totalBytes,
                                        fraction = fraction,
                                        bytesPerSec = bytesPerSec,
                                    ),
                                )
                                lastEmitMs = now
                            }
                        }
                    }
                }

                // Atomic rename so the caller's [outFile] only ever
                // points at a complete file.
                if (!partFile.renameTo(outFile)) {
                    // Some filesystems refuse rename-overwrite;
                    // delete the target and try again.
                    outFile.delete()
                    if (!partFile.renameTo(outFile)) {
                        return@withContext MeshlitResult.Failure(
                            MeshlitError.Native(
                                tag = "http.rename_failed",
                                cause = IOException("rename ${partFile.path} → ${outFile.path}"),
                            ),
                        )
                    }
                }

                log.info(
                    "http.download.success",
                    "streamed ${receivedBytes}B to ${outFile.name}",
                    mapOf("url" to url),
                )

                MeshlitResult.Success(outFile)
            }
        } catch (e: IOException) {
            log.warn(
                "http.download.fail",
                "I/O failure: ${e.message}",
                mapOf("url" to url),
            )
            MeshlitResult.Failure(
                MeshlitError.Network(
                    tag = "http.io:${e.javaClass.simpleName}",
                    cause = e,
                ),
            )
        } catch (e: Exception) {
            MeshlitResult.Failure(
                MeshlitError.Network(
                    tag = "http.exception:${e.javaClass.simpleName}",
                    cause = e,
                ),
            )
        }
    }

    /**
     * Flow variant. Emits at least one terminal
     * [DownloadProgress] (the final 100% snapshot) before
     * completing so callers using `collect` always see a
     * stable end-state.
     */
    fun downloadFlow(
        url: String,
        outFile: File,
        headers: Map<String, String> = emptyMap(),
    ): Flow<DownloadProgress> = callbackFlow {
        val result = download(url, outFile, headers) { progress ->
            trySend(progress).isSuccess
        }
        when (result) {
            is MeshlitResult.Success -> {
                trySend(
                    DownloadProgress(
                        receivedBytes = result.value.length(),
                        totalBytes = result.value.length(),
                        fraction = 1f,
                        bytesPerSec = 0L,
                    ),
                )
            }
            is MeshlitResult.Failure -> {
                // Surface as a 0% snapshot so callers can
                // differentiate "failed" from "completed" via
                // their own try/catch around collect.
                trySend(
                    DownloadProgress(
                        receivedBytes = 0L,
                        totalBytes = 0L,
                        fraction = 0f,
                        bytesPerSec = 0L,
                    ),
                )
            }
        }
        close()
    }
}
