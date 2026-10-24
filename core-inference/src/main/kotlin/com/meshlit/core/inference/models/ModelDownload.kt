package com.meshlit.core.inference.models

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Resumable, bounded artifact downloads. A failed transfer never replaces a
 * valid installed model. Metadata is bound to the original URL and strong ETag. */
class ModelDownload(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val permitTestHttp: Boolean = false,
) {
    data class Progress(val bytes: Long, val total: Long, val bytesPerSecond: Long, val phase: String) {
        val percent: Int? get() = if (total > 0) ((bytes.toDouble() / total) * 100).toInt().coerceIn(0, 99) else null
    }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val slots = Semaphore(2)
    private val reserved = ConcurrentHashMap<String, Long>()
    private val reservationLock = Any()
    private fun reserve(dest: File, remaining: Long) = synchronized(reservationLock) {
        val key=dest.canonicalPath
        val used=reserved.filterKeys { it!=key }.values.sum()
        require(dest.parentFile!!.usableSpace >= used + remaining + RESERVE) { "Insufficient unreserved storage for this model" }
        reserved[key]=remaining
    }
    private fun release(dest: File) { synchronized(reservationLock) { reserved.remove(dest.canonicalPath) } }


    suspend fun download(
        url: String, destination: File, token: String = "", expectedSha256: String? = null,
        onProgress: (Progress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        locks.getOrPut(destination.canonicalPath) { Mutex() }.withLock {
            slots.withPermit {
                try { withTimeout(60 * 60 * 1000L) { transfer(url, destination, token, expectedSha256, onProgress) } }
                finally { release(destination) }
            }
        }
    }

    private fun checkedUrl(value: String): HttpUrl {
        val parsed = value.toHttpUrl()
        require((parsed.isHttps || permitTestHttp && parsed.host in setOf("localhost", "127.0.0.1")) &&
            parsed.username.isEmpty() && parsed.password.isEmpty()) { "Use an HTTPS model URL without embedded credentials" }
        // A repository blob page is not a weight file.
        return if (parsed.host == "huggingface.co" && "/blob/" in parsed.encodedPath)
            parsed.newBuilder().encodedPath(parsed.encodedPath.replaceFirst("/blob/", "/resolve/")).build()
        else parsed
    }

    private suspend fun transfer(url: String, dest: File, token: String, expected: String?, update: (Progress) -> Unit): File {
        require(expected == null || expected.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256" }
        val origin = checkedUrl(url)
        val dir = requireNotNull(dest.parentFile).apply { check(mkdirs() || isDirectory) }
        val part = File(dir, dest.name + ".part")
        val metaFile = File(dir, dest.name + ".resume")
        val meta = Properties().apply { if (metaFile.isFile) metaFile.inputStream().use { load(it) } }
        val etag = meta.getProperty("etag", "")
        var offset = if (part.isFile && meta.getProperty("source") == origin.toString() &&
            etag.isNotBlank() && !etag.startsWith("W/")) part.length() else 0L
        if (offset == 0L) part.delete()
        var endpoint = origin
        var restarted = false
        var redirects = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val builder = Request.Builder().url(endpoint).header("User-Agent", "Meshlit/0.2.3 (Android)")
                .header("Accept-Encoding", "identity").header("Accept", "application/octet-stream")
            if (endpoint.host == "huggingface.co" && token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            if (offset > 0) builder.header("Range", "bytes=$offset-").header("If-Range", etag)
            val call = client.newCall(builder.build())
            var follow: HttpUrl? = null
            var retry = false
            coroutineScope {
                // Parent cancellation cancels a blocking network read promptly.
                val watcher = launch(Dispatchers.IO) { try { awaitCancellation() } finally { call.cancel() } }
                try {
                    call.execute().use { response ->
                        if (response.code in setOf(301, 302, 303, 307, 308)) {
                            require(++redirects <= 8) { "Too many source redirects" }
                            follow = checkedUrl(requireNotNull(endpoint.resolve(response.header("Location") ?: "")) {
                                "Invalid redirect"
                            }.toString())
                            return@use
                        }
                        if (response.code == 416 && offset > 0 && !restarted) {
                            part.delete(); metaFile.delete(); offset = 0; restarted = true; retry = true
                            return@use
                        }
                        if (!response.isSuccessful) throw IOException(when (response.code) {
                            401, 403 -> "Source requires authorization or license acceptance (HTTP ${response.code})"
                            404 -> "Model file not found; check repository, revision and filename"
                            429 -> "Source rate limited the download; retry later"
                            else -> "Download failed (HTTP ${response.code})"
                        })
                        require(response.code in setOf(200, 206)) { "Unexpected download response" }
                        val body = response.body ?: throw IOException("Source returned no model data")
                        require(!body.contentType().toString().contains("text/html", true)) { "Source returned a web page, not model weights" }
                        val responseTag = response.header("ETag", "")!!
                        val length = body.contentLength()
                        val total: Long
                        if (response.code == 206) {
                            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range", "")!!)
                                ?: throw IOException("Invalid resumed download range")
                            val start = match.groupValues[1].toLong()
                            val end = match.groupValues[2].toLong()
                            total = match.groupValues[3].toLong()
                            require(start == offset && end >= start && end < total && (length < 0 || length == end - start + 1)) {
                                "Source returned inconsistent resume data"
                            }
                            if(offset > 0L && responseTag != etag) {
                                part.delete(); metaFile.delete();offset=0;require(!restarted){"Model validator keeps changing"}
                                restarted=true;retry=true;return@use
                            }
                        } else {
                            // Range ignored or validator changed: overwrite, never append a 200 body.
                            offset = 0L
                            total = length
                        }
                        require(total < 0 || total <= MAX_BYTES) { "Model exceeds supported download size" }
                        reserve(dest, if(total>=0) total-offset else 0)
                        Properties().apply { setProperty("source", origin.toString()); setProperty("etag", responseTag) }
                            .let { props -> metaFile.outputStream().use { props.store(it, null) } }
                        var received = offset
                        val started = System.nanoTime()
                        var lastUpdate = 0L
                        update(Progress(received, total, 0, "Downloading"))
                        body.byteStream().use { input ->
                            FileOutputStream(part, offset > 0).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    received += n
                                    require(received <= MAX_BYTES && (total < 0 || received <= total)) { "Model response exceeds expected size" }
                                    output.write(buffer, 0, n)
                                    val now = System.nanoTime()
                                    if (now - lastUpdate >= 200_000_000L) {
                                        reserve(dest, if(total>=0) (total-received).coerceAtLeast(0) else 0)
                                        update(Progress(received, total, ((received - offset) * 1_000_000_000.0 /
                                            (now - started).coerceAtLeast(1)).toLong(), "Downloading"))
                                        lastUpdate = now
                                    }
                                }
                                output.fd.sync()
                            }
                        }
                        require(total < 0 || received == total) { "Incomplete model download; retry to resume" }
                        update(Progress(received, total, 0, "Validating"))
                        if (dest.extension.equals("gguf", true)) ModelFiles.validateGguf(part)
                        if (expected != null && !ModelFiles.sha256(part).equals(expected, true)) {
                            part.delete(); metaFile.delete(); throw IOException("Model checksum mismatch; download again")
                        }
                        currentCoroutineContext().ensureActive()
                        if (!part.renameTo(dest)) throw IOException("Could not atomically install model")
                        metaFile.delete()
                        update(Progress(received, received, 0, "Installed"))
                    }
                } finally { watcher.cancelAndJoin() }
            }
            if (follow != null) { endpoint = follow!!; continue }
            if (retry) { endpoint = origin; continue }
            return dest
        }
    }
    companion object { const val RESERVE = 64L * 1024 * 1024; const val MAX_BYTES = 128L * 1024 * 1024 * 1024 }
}
