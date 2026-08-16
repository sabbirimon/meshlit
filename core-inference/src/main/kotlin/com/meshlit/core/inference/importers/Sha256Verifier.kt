package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Streams a URL to a temp file while computing SHA-256 over the byte
 * stream, then verifies against [expected] (if non-blank).
 *
 * The download is buffered in 64 KiB chunks so multi-GB models do
 * not OOM. Verification is constant-memory (we hash as we go).
 *
 * On success returns the file size in bytes. On SHA mismatch the
 * partial file is deleted and a [ShaMismatchException] is returned.
 *
 * Used by [com.meshlit.core.inference.importers.HuggingFaceResolver]
 * callers who want a verified download — the importer resolves the
 * URL + SHA, the download manager hands both to this class.
 */
class Sha256Verifier(
    private val chunkBytes: Int = 64 * 1024,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) {

    /**
     * @param url the file URL to download.
     * @param target where to write the bytes. Caller chooses the path;
     *               typically `cacheDir/imports/<hash>.part`.
     * @param expected optional SHA-256 hex string (64 lowercase hex
     *                 chars). When null the verifier returns the byte
     *                 count without raising on mismatch.
     */
    suspend fun verifyAndDownload(
        url: String,
        target: File,
        expected: String?,
    ): MeshlitResult<Long> = withContext(Dispatchers.IO) {
        runCatching {
            target.parentFile?.mkdirs()
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MeshlitImporter/1.0")
            }
            val md = MessageDigest.getInstance("SHA-256")
            val totalBytes = conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(chunkBytes)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        md.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                target.length()
            }
            conn.disconnect()

            if (expected != null) {
                val digest = md.digest().toHex()
                if (!digest.equals(expected.trim(), ignoreCase = true)) {
                    target.delete()
                    throw ShaMismatchException(expected = expected, actual = digest, sizeBytes = totalBytes)
                }
            }
            totalBytes
        }.fold(
            onSuccess = { MeshlitResult.Success(it) },
            onFailure = { t ->
                val err = if (t is ShaMismatchException) {
                    MeshlitError.Invalid("sha.mismatch:${t.expected.take(8)}")
                } else {
                    MeshlitError.Network("sha.download", t)
                }
                MeshlitResult.Failure(err)
            },
        )
    }

    /**
     * Pure-JVM hash of a local file. Used by tests and by callers
     * that already have a file on disk (e.g. re-importing).
     */
    fun hashOf(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(chunkBytes)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            md.update(buffer, 0, read)
        }
        md.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append(((b.toInt() ushr 4) and 0xF).toString(16)).append((b.toInt() and 0xF).toString(16))
        return sb.toString()
    }
}

class ShaMismatchException(
    val expected: String,
    val actual: String,
    val sizeBytes: Long,
) : RuntimeException("SHA-256 mismatch: expected $expected, got $actual after $sizeBytes bytes")
