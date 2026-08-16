package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Reassembles a model from locally hosted and remote shard files.
 *
 * The output is written under `<filesDir>/reassembled/<modelId>.gguf`.
 * The method is transactional: it writes to `.part`, verifies the
 * final SHA-256, then renames atomically. A failed verification
 * deletes the partial file and leaves any previous good output intact.
 *
 * Remote shards are fetched with [ShardTransport.fetchShardToFile]
 * directly into the correct byte offset; no `ByteArray(modelSize)`
 * is ever allocated. This is important on phones where the model can
 * be several times larger than available RAM.
 */
class ShardAssembler(
    private val filesDir: File,
    private val transport: ShardTransport = ShardTransport(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val log = logger("ShardAssembler")

    /**
     * Reassemble [assignments] into one contiguous model file.
     *
     * @param peerBaseUrl maps peer id → `http://ip:port`. Required
     *   only for assignments whose `peerId != "self"`.
     * @param expectedSha256 hex SHA-256 of the full final model.
     */
    suspend fun reassemble(
        modelId: String,
        assignments: List<ClusterShardPlanner.ShardAssignment>,
        expectedSha256: String,
        peerBaseUrl: (String) -> String?,
        onProgress: (Long) -> Unit = {},
    ): File = withContext(dispatcher) {
        require(assignments.isNotEmpty()) { "assignments must not be empty" }
        val dir = File(filesDir, "reassembled").apply { mkdirs() }
        val dest = File(dir, "$modelId.gguf")
        val tmp = File(dir, "$modelId.gguf.part")
        runCatching { tmp.delete() }
        tmp.createNewFile()

        try {
            val totalBytes = assignments.maxOf { it.byteOffset + it.byteLength }
            RandomAccessFile(tmp, "rw").use { it.setLength(totalBytes) }
            var copied = 0L
            assignments.sortedBy { it.byteOffset }.forEach { assignment ->
                if (assignment.peerId == SELF_PEER_ID) {
                    val shard = localShardFile(modelId, assignment.shardId)
                    require(shard.exists()) {
                        "local shard ${assignment.shardId} not found at ${shard.absolutePath}"
                    }
                    copyLocalRange(
                        source = shard,
                        dest = tmp,
                        offset = assignment.byteOffset,
                        length = assignment.byteLength,
                    )
                } else {
                    val base = peerBaseUrl(assignment.peerId)
                        ?: error("no base URL for peer ${assignment.peerId}")
                    transport.fetchShardToFile(
                        peerBaseUrl = base,
                        modelId = modelId,
                        shardId = assignment.shardId,
                        dest = tmp,
                        offset = assignment.byteOffset,
                        length = assignment.byteLength,
                    ).getOrThrow()
                }
                copied += assignment.byteLength
                onProgress(copied * 100L / totalBytes.coerceAtLeast(1L))
            }

            val actual = sha256(tmp)
            // Manifest SHA-256 is optional — when it's blank (e.g. an
            // SDK-CDN model with no documented digest) we accept the
            // reassembled file as-is. ClusterStorageInstaller.kt
            // documents the same convention; keep them in sync.
            if (expectedSha256.isNotBlank()) {
                require(actual.equals(expectedSha256, ignoreCase = true)) {
                    "model SHA-256 mismatch: expected=$expectedSha256 actual=$actual"
                }
            } else {
                log.warn(
                    "shard.assemble.no_sha",
                    "skipping SHA verify — no manifest SHA",
                    mapOf("modelId" to modelId, "actual" to actual),
                )
            }
            if (dest.exists()) dest.delete()
            check(tmp.renameTo(dest)) { "could not rename reassembled model" }
            log.info(
                "shard.assemble.done",
                "reassembled model",
                mapOf("modelId" to modelId, "bytes" to dest.length(), "shards" to assignments.size),
            )
            dest
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            log.warn(
                "shard.assemble.fail",
                "reassembly failed",
                mapOf("modelId" to modelId, "err" to (t.message ?: t.javaClass.simpleName)),
            )
            throw t
        }
    }

    /** Split a local whole model into shard files that match a plan.
     *  Used by the seed/owner device before `pushShard`. */
    suspend fun splitLocalModel(
        modelId: String,
        source: File,
        assignments: List<ClusterShardPlanner.ShardAssignment>,
    ): List<File> = withContext(dispatcher) {
        require(source.exists()) { "source model missing: ${source.absolutePath}" }
        val dir = File(filesDir, "shards/$modelId").apply { mkdirs() }
        RandomAccessFile(source, "r").use { input ->
            assignments.sortedBy { it.byteOffset }.map { assignment ->
                val dest = File(dir, "${assignment.shardId}.shard")
                val tmp = File(dir, "${assignment.shardId}.part")
                input.seek(assignment.byteOffset)
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var remaining = assignment.byteLength
                    while (remaining > 0L) {
                        val want = minOf(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, want)
                        if (read < 0) error("unexpected EOF splitting ${assignment.shardId}")
                        output.write(buf, 0, read)
                        remaining -= read
                    }
                }
                if (dest.exists()) dest.delete()
                check(tmp.renameTo(dest)) { "rename failed for ${assignment.shardId}" }
                dest
            }
        }
    }

    private fun copyLocalRange(source: File, dest: File, offset: Long, length: Long) {
        RandomAccessFile(dest, "rw").use { out ->
            out.seek(offset)
            FileInputStream(source).use { input ->
                val buf = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0L) {
                    val want = minOf(buf.size.toLong(), remaining).toInt()
                    val read = input.read(buf, 0, want)
                    if (read < 0) error("unexpected EOF reading ${source.name}")
                    out.write(buf, 0, read)
                    remaining -= read
                }
            }
        }
    }

    private fun localShardFile(modelId: String, shardId: String): File =
        File(filesDir, "shards/$modelId/$shardId.shard")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SELF_PEER_ID: String = "self"
    }
}
