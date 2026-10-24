package com.meshlit.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Imports individual trusted artifacts; never executes or unpacks an archive. */
class ArtifactStore(private val directory: File) {
    private val imports = Mutex()
    suspend fun verify(file: File, sha256: String): Boolean = withContext(Dispatchers.IO) {
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Expected a SHA-256 digest" }
        if (!file.isFile) return@withContext false
        digest(file).equals(sha256, ignoreCase = true)
    }

    suspend fun install(source: File, name: String, sha256: String, replace: Boolean = false): File {
        require(source.isFile) { "Missing artifact" }
        require(source.canonicalFile != File(directory, name).canonicalFile) { "Source and destination must differ" }
        return installStream({ source.inputStream() }, name, sha256, source.length(), replace)
    }

    /** Copies opaque bytes; never executes or extracts downloaded content. */
    suspend fun installStream(
        open: () -> InputStream, name: String, sha256: String, expectedBytes: Long,
        replace: Boolean = false, progress: suspend (Long) -> Unit = {},
    ): File = imports.withLock { withTimeout(30L * 60 * 1000) { withContext(Dispatchers.IO) {
        require(name.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")) && name != "." && name != "..")
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(expectedBytes in 1..(8L * 1024 * 1024 * 1024)) { "Invalid artifact size" }
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create artifact directory" }
        val target = File(directory, name)
        require(!target.exists() || replace) { "Artifact already exists" }
        require(directory.usableSpace > expectedBytes + 64L * 1024 * 1024) { "Insufficient free storage" }
        val staging = File.createTempFile("import-", ".part", directory)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            open().use { input -> staging.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count > 0) { "Artifact stream made no progress" }
                    total += count
                    require(total <= expectedBytes) { "Artifact exceeds declared size" }
                    hash.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    progress(total)
                }
                output.fd.sync()
            } }
            require(total == expectedBytes) { "Incomplete artifact" }
            val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            require(actual.equals(sha256, ignoreCase = true)) { "Artifact checksum mismatch" }
            coroutineContext.ensureActive()
            require(staging.renameTo(target)) { "Atomic artifact replacement failed" }
            target
        } finally { staging.delete() }
    } } }

    /** Human-supplied exact HTTPS source, no credentials, redirects or execution. */
    suspend fun download(
        url: String, name: String, sha256: String, expectedBytes: Long,
        progress: suspend (Long) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(url.length <= 4096)
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.rawQuery == null && uri.fragment == null) { "Use an exact HTTPS URL without credentials/query/fragment" }
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        try {
            installStream({
                require(connection.responseCode == 200) { "Artifact request failed or redirected" }
                val size = connection.contentLengthLong
                require(size < 0 || size == expectedBytes) { "Remote artifact size differs" }
                connection.inputStream
            }, name, sha256, expectedBytes, progress = progress)
        } finally { connection.disconnect() }
    }

    private suspend fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) {
                coroutineContext.ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
