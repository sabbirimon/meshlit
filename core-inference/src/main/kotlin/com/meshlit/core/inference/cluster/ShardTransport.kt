package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Plain HTTP/1.1 client for shard transfer.
 *
 * Endpoints (mounted by [ShardServer]):
 *  - `POST /v1/shards/{modelId}/{shardId}`  — body is the raw shard
 *  - `GET  /v1/shards/{modelId}/{shardId}`  — raw bytes (range-aware)
 *  - `GET  /v1/manifest/{modelId}`          — JSON `ShardManifest`
 *  - `GET  /v1/capabilities`                — JSON `PeerCapabilities`
 *
 * Concurrency:
 *  - One `OkHttpClient` shared across calls; per-call timeouts only.
 *  - `fetchShard` writes to disk as the body streams in, so we
 *    never hold the whole shard in memory. Phone-grade RAM headroom.
 *
 * Range support:
 *  - `fetchShardRange(..., offset, length)` emits a `Range: bytes=…`
 *    header. Server should reply with `206 Partial Content` when
 *    implemented; today the server falls back to 200 + full body,
 *    which the client truncates after `length` bytes (a known gap
 *    that we will close in a follow-up).
 *
 * Why OkHttp and not NanoHTTPD's outbound: NanoHTTPD is a server
 * stack — using its internals for outbound calls means pulling in
 * a chunk of the server-only routing layer into the client path.
 * OkHttp is what the rest of `:core-inference` already uses for
 * the SDK's downloads, so reusing it here keeps the dependency
 * graph simple.
 */
class ShardTransport(
    private val client: OkHttpClient = defaultClient(),
) {

    private val log = logger("ShardTransport")
    private val shardClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /** Upload a shard to the peer's HTTP server. Overwrites if it
     *  already exists (the server treats `POST` as idempotent). */
    suspend fun pushShard(
        peerBaseUrl: String,
        modelId: String,
        shardId: String,
        source: File,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = "$peerBaseUrl/v1/shards/$modelId/$shardId"
        val request = Request.Builder()
            .url(url)
            .post(source.asShardRequestBody())
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} pushing $shardId to $peerBaseUrl")
                }
            }
        }.onFailure { t ->
            log.warn(
                "shard.push.fail",
                "push failed",
                mapOf("shard" to shardId, "peer" to peerBaseUrl, "err" to (t.message ?: "?")),
            )
        }
    }

    /** Fetch an entire shard and return the raw bytes. Use this for
     *  small metadata shards; for model shards prefer [fetchShardToFile]. */
    suspend fun fetchShardBytes(
        peerBaseUrl: String,
        modelId: String,
        shardId: String,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val url = "$peerBaseUrl/v1/shards/$modelId/$shardId"
        val request = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.bytes() ?: throw IOException("empty body")
            }
        }.onFailure { t ->
            log.warn("shard.fetch.fail", "fetch failed", mapOf("shard" to shardId, "err" to (t.message ?: "?")))
        }
    }

    /** Stream exactly the declared immutable shard; never follow a redirect
     * away from the caller-approved source. Caller verifies the digest. */
    suspend fun fetchShardToFile(
        peerBaseUrl: String,
        modelId: String,
        shardId: String,
        dest: File,
        offset: Long,
        length: Long,
        onProgress: (Long) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        require(length > 0L && offset >= 0 && offset <= Long.MAX_VALUE - length) { "Invalid shard bounds" }
        val url = "$peerBaseUrl/v1/shards/$modelId/$shardId"
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${length - 1}")
            .get()
            .build()
        val call = shardClient.newCall(request)
        try {
            coroutineScope {
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try { call.execute().use { handleShardResponse(it, dest, offset, length, onProgress) } }
                finally { cancellation.cancel() }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(error)
        }
    }

    /** Fetch the JSON `PeerCapabilities` document. */
    suspend fun fetchCapabilities(peerBaseUrl: String): Result<PeerCapabilities> =
        withContext(Dispatchers.IO) {
            val url = "$peerBaseUrl/v1/capabilities"
            val request = Request.Builder().url(url).get().build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    PeerCapabilitiesJson.decode(body)
                }
            }
        }

    private suspend fun handleShardResponse(
        resp: Response,
        dest: File,
        offset: Long,
        length: Long,
        onProgress: (Long) -> Unit,
    ) {
        if (resp.code != 200 && resp.code != 206) throw IOException("HTTP ${resp.code}")
        if (resp.code == 206 && resp.header("Content-Range") != "bytes 0-${length - 1}/$length") {
            throw IOException("Shard Content-Range differs from manifest")
        }
        val body = resp.body ?: throw IOException("empty body")
        if (body.contentLength() >= 0 && body.contentLength() != length) throw IOException("Shard length differs from manifest")
        dest.parentFile?.mkdirs()
        RandomAccessFile(dest, "rw").use { raf ->
            raf.seek(offset)
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (copied < length) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buf, 0, minOf(buf.size.toLong(), length - copied).toInt())
                    if (read < 0) throw IOException("Truncated shard")
                    raf.write(buf, 0, read)
                    copied += read
                    if (copied % (256 * 1024) == 0L) onProgress(copied)
                }
                if (input.read() != -1) throw IOException("Shard exceeds manifest length")
                currentCoroutineContext().ensureActive()
            }
        }
        onProgress(length)
    }

    private fun File.asShardRequestBody(): okhttp3.RequestBody =
        asRequestBody("application/octet-stream".toMediaType())

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
