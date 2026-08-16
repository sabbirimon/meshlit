package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File

/**
 * NanoHTTPD route handlers for shard transfer. Mounted by the
 * `MeshlitApplication` peer-probe loop onto the same `InferenceHttpServer`
 * port — NanoHTTPD routes are pluggable.
 *
 * Endpoints:
 *  - `POST /v1/shards/{modelId}/{shardId}` — body is the raw shard
 *  - `GET  /v1/shards/{modelId}/{shardId}` — raw bytes
 *  - `GET  /v1/capabilities`                — JSON `PeerCapabilities`
 *
 * Storage layout:
 *  - `<filesDir>/shards/<modelId>/<shardId>.shard`
 *  - Uploads land as `<shardId>.part` first, then renamed on
 *    successful write — protects against half-received bytes
 *    being served later.
 *
 * Why a separate class instead of editing `InferenceHttpServer`:
 * the latter carries an `InferenceCoordinator` dependency for the
 * inference endpoints. Mounting shard routes on the same server
 * would force every shard read/write to take that coordinator's
 * lock. Keeping the shard handler standalone means we can route
 * shard traffic independently when the inference path is busy.
 */
class ShardServer(
    private val filesDir: File,
    private val selfCapabilities: () -> PeerCapabilities,
) {

    private val log = logger("ShardServer")

    fun route(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        val uri = session.uri.removePrefix("/").trimEnd('/')
        return when {
            uri.startsWith("v1/manifest/") -> handleManifest(session, uri)
            uri.startsWith("v1/shards/") -> handleShard(session, uri)
            uri == "v1/capabilities" -> handleCapabilities(session)
            else -> null
        }
    }

    /**
     * `GET /v1/manifest/{modelId}` — JSON document describing every
     * shard the local device already hosts for `modelId`. The
     * planner consults this before sending `MultiShard` requests so
     * a peer doesn't re-fetch a shard it could pull locally. When
     * the device hosts no shards for the model, returns 404 (which
     * is the same signal the planner uses to fall back to a whole-
     * model download).
     */
    private fun handleManifest(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "text/plain",
                "use GET",
            )
        }
        val modelId = sanitize(uri.removePrefix("v1/manifest/"))
        if (modelId.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "missing modelId",
            )
        }
        val dir = shardDir(modelId)
        if (!dir.isDirectory) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain",
                "no shards hosted for $modelId",
            )
        }
        val shardFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".shard") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (shardFiles.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain",
                "no shards hosted for $modelId",
            )
        }
        var offset = 0L
        val specs = shardFiles.map { f ->
            val length = f.length()
            val spec = ClusterShardManifest.ShardSpec(
                shardId = f.name.removeSuffix(".shard"),
                byteOffset = offset,
                byteLength = length,
            )
            offset += length
            spec
        }
        val manifest = ClusterShardManifest(
            modelId = modelId,
            totalBytes = offset,
            sha256 = "",
            shardSpecs = specs,
        )
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            ClusterShardManifestJson.encode(manifest),
        )
    }

    private fun handleShard(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response {
        val parts = uri.removePrefix("v1/shards/").split('/')
        if (parts.size != 2) return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.BAD_REQUEST,
            "text/plain",
            "expected /v1/shards/{modelId}/{shardId}",
        )
        val modelId = sanitize(parts[0])
        val shardId = sanitize(parts[1])
        if (modelId.isEmpty() || shardId.isEmpty()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "text/plain",
                "modelId/shardId contain illegal characters",
            )
        }
        val shardFile = shardFile(modelId, shardId)
        return when (session.method) {
            NanoHTTPD.Method.GET -> serveShard(shardFile, session)
            NanoHTTPD.Method.POST -> ingestShard(modelId, shardId, shardFile, session)
            else -> NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                "text/plain",
                "use GET or POST",
            )
        }
    }

    private fun serveShard(file: File, session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (!file.exists()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "text/plain",
                "shard not hosted on this peer",
            )
        }
        val rangeHeader = session.headers["range"]
        return if (rangeHeader != null) {
            val (start, end) = parseRange(rangeHeader, file.length())
                ?: return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE,
                    "text/plain",
                    "bad Range",
                )
            val length = end - start + 1
            val slice = file.inputStream().use { input ->
                val skipped = input.skip(start)
                require(skipped == start) { "could not skip to range start" }
                val buf = ByteArray(length.toInt())
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read < buf.size) buf.copyOf(read) else buf
            }
            val resp = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.PARTIAL_CONTENT,
                "application/octet-stream",
                ByteArrayInputStream(slice),
                slice.size.toLong(),
            )
            resp.addHeader("Content-Range", "bytes $start-$end/${file.length()}")
            resp
        } else {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/octet-stream",
                file.inputStream(),
                file.length(),
            )
        }
    }

    private fun ingestShard(
        modelId: String,
        shardId: String,
        dest: File,
        session: NanoHTTPD.IHTTPSession,
    ): NanoHTTPD.Response {
        val dir = shardDir(modelId)
        if (!dir.exists() && !dir.mkdirs()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/plain",
                "could not create $dir",
            )
        }
        val tmp = File(dir, "$shardId.part")
        try {
            session.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "rename failed",
                )
            }
            log.info(
                "shard.server.ingest",
                "shard stored",
                mapOf("modelId" to modelId, "shardId" to shardId, "bytes" to dest.length()),
            )
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "text/plain",
                "ok",
            )
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            log.warn(
                "shard.server.ingest.fail",
                "ingest failed",
                mapOf("err" to (t.message ?: "?")),
            )
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "text/plain",
                "ingest failed: ${t.message}",
            )
        }
    }

    private fun handleCapabilities(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val caps = selfCapabilities()
        val payload = PeerCapabilitiesJson.encode(caps)
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json",
            payload,
        )
    }

    private fun shardDir(modelId: String): File = File(filesDir, "shards/$modelId")

    private fun shardFile(modelId: String, shardId: String): File =
        File(shardDir(modelId), "$shardId.shard")

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Parse `bytes=START-END` (single range). Returns null on bad input. */
    private fun parseRange(header: String, totalLength: Long): Pair<Long, Long>? {
        val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim())
            ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: 0L
        val end = match.groupValues[2].toLongOrNull() ?: (totalLength - 1)
        if (start < 0 || end < start || start >= totalLength) return null
        return start to minOf(end, totalLength - 1)
    }
}
