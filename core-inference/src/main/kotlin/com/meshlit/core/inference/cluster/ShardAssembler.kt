package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Reassembles a model from locally hosted and remote shard files.
 *
 * The output is written under `<filesDir>/reassembled/<modelId>.gguf`.
 * The method is transactional: it writes to `.part`, verifies the
 * final SHA-256, then renames atomically. Previous committed output survives
 * failure. Partial ranges are retained only with trusted per-shard digests,
 * and rehashed before reuse; otherwise a failed partial is removed.
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
        expectedShardSha256: Map<String, String> = emptyMap(),
        expectedSizeBytes: Long? = null,
    ): File = withContext(dispatcher) { diskLock.withLock {
        requireIdentifier(modelId)
        requireDigest(expectedSha256)
        val ordered = assignments.sortedBy { it.byteOffset }
        val totalBytes = validateAssignments(ordered)
        require(expectedSizeBytes == null || expectedSizeBytes == totalBytes) { "Manifest size differs from model source" }
        require(expectedShardSha256.isEmpty() || expectedShardSha256.keys == ordered.map { it.shardId }.toSet()) {
            "Resume requires an independently supplied digest for every shard"
        }
        expectedShardSha256.values.forEach(::requireDigest)
        val dir = File(filesDir, "reassembled").apply { check(mkdirs() || isDirectory) }
        val dest = File(dir, "$modelId.gguf")
        if (verifiedFile(dest, totalBytes, expectedSha256)) return@withLock dest
        // Resolve once: recovery never searches for another source or follows
        // redirects. The caller remains responsible for peer approval.
        val sources = ordered.filter { it.peerId != SELF_PEER_ID }.associate { a ->
            a.peerId to requireNotNull(peerBaseUrl(a.peerId)) { "No approved source for ${a.peerId}" }
        }
        val tmp = File(dir, "$modelId.gguf.part")
        val marker = File(dir, "$modelId.gguf.part.manifest")
        val resumable = expectedShardSha256.isNotEmpty()
        val identity = digestText(buildString {
            append(expectedSha256.lowercase()).append(':').append(totalBytes).append('\n')
            ordered.forEach { a -> append("${a.shardId}:${a.layerStart}:${a.layerEnd}:${a.byteOffset}:${a.byteLength}:")
                .append(expectedShardSha256[a.shardId]?.lowercase().orEmpty()).append('\n') }
        })
        var verifyRetainedRanges = resumable && tmp.isFile && tmp.length() == totalBytes &&
            marker.isFile && marker.length() == 64L && marker.readText() == identity
        if (!verifyRetainedRanges) {
            check(!tmp.exists() || tmp.delete()) { "Cannot remove incompatible partial model" }
            check(!marker.exists() || marker.delete()) { "Cannot remove incompatible partial manifest" }
            RandomAccessFile(tmp, "rw").use { it.setLength(totalBytes) }
            if (resumable) marker.writeText(identity)
        }
        var preservePartial = resumable
        try {
            for (attempt in 0..1) {
                try {
                    var copied = 0L
                    for (a in ordered) {
                        currentCoroutineContext().ensureActive()
                        val shardHash = expectedShardSha256[a.shardId]
                        // Never trust a completion bitmap or a prior file size.
                        // Rehash each retained range against the trusted manifest.
                        if (shardHash == null || !verifyRetainedRanges || !sha256(tmp, a.byteOffset, a.byteLength).equals(shardHash, true)) {
                            if (a.peerId == SELF_PEER_ID) {
                                val shard = localShardFile(modelId, a.shardId)
                                require(shard.isFile && shard.length() == a.byteLength) { "Local shard size differs from manifest" }
                                copyLocalRange(shard, tmp, a.byteOffset, a.byteLength)
                            } else {
                                transport.fetchShardToFile(sources.getValue(a.peerId), modelId, a.shardId,
                                    tmp, a.byteOffset, a.byteLength).getOrThrow()
                            }
                            require(shardHash == null || sha256(tmp, a.byteOffset, a.byteLength).equals(shardHash, true)) {
                                "Shard SHA-256 mismatch: ${a.shardId}"
                            }
                        }
                        copied += a.byteLength
                        onProgress(copied * 100 / totalBytes)
                    }
                    if (!sha256(tmp).equals(expectedSha256, true)) {
                        // If every trusted chunk passed but the model did not,
                        // the manifests disagree; no bytes may be reused.
                        if (resumable) preservePartial = false
                        error("Model SHA-256 mismatch")
                    }
                    currentCoroutineContext().ensureActive()
                    RandomAccessFile(tmp, "rw").use { it.fd.sync() }
                    // Same-directory rename replaces atomically on Android. Do
                    // not delete the last good model if publication fails.
                    check(tmp.renameTo(dest)) { "Could not commit verified model" }
                    marker.delete()
                    return@withLock dest
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (attempt == 1 || sources.isEmpty() || !preservePartial && resumable) throw error
                    verifyRetainedRanges = resumable
                    // At most one refetch pass. With chunk digests, good ranges
                    // survive; with only the model digest, refetch every range.
                }
            }
            error("Reassembly did not complete")
        } finally {
            if (!preservePartial) { tmp.delete(); marker.delete() }
        }
    } }

    /** Revalidate a committed cache on every acquisition, including same-size corruption. */
    suspend fun verifiedCachedModel(modelId: String, sizeBytes: Long, expectedSha256: String): File? =
        withContext(dispatcher) { diskLock.withLock {
            requireIdentifier(modelId); requireDigest(expectedSha256)
            val file = File(filesDir, "reassembled/$modelId.gguf")
            file.takeIf { verifiedFile(it, sizeBytes, expectedSha256) }
        } }

    private suspend fun verifiedFile(file: File, sizeBytes: Long, expectedSha256: String): Boolean =
        file.isFile && sizeBytes > 0 && file.length() == sizeBytes && sha256(file).equals(expectedSha256, true)

    suspend fun requireVerifiedModel(file: File, sizeBytes: Long, expectedSha256: String): File = withContext(dispatcher) {
        requireDigest(expectedSha256)
        require(verifiedFile(file, sizeBytes, expectedSha256)) { "Downloaded model size or SHA-256 differs from manifest" }
        file
    }

    private fun validateAssignments(assignments: List<ClusterShardPlanner.ShardAssignment>): Long {
        require(assignments.size in 1..1024 && assignments.map { it.shardId }.distinct().size == assignments.size)
        var end = 0L
        var layerEnd = 0
        assignments.forEach { a ->
            requireIdentifier(a.shardId)
            require(a.byteOffset == end && a.byteLength > 0 && a.byteLength <= MAX_MODEL_BYTES - end &&
                a.layerStart == layerEnd && a.layerEnd > a.layerStart) { "Invalid, overlapping or incomplete shard manifest" }
            end += a.byteLength; layerEnd = a.layerEnd
        }
        return end
    }

    /** Split a local whole model into shard files that match a plan.
     *  Used by the seed/owner device before `pushShard`. */
    suspend fun splitLocalModel(
        modelId: String,
        source: File,
        assignments: List<ClusterShardPlanner.ShardAssignment>,
    ): List<File> = withContext(dispatcher) { diskLock.withLock {
        requireIdentifier(modelId)
        require(source.isFile) { "source model missing: ${source.absolutePath}" }
        require(validateAssignments(assignments.sortedBy { it.byteOffset }) == source.length()) { "Source size differs from manifest" }
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
                        currentCoroutineContext().ensureActive()
                        val want = minOf(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, want)
                        if (read < 0) error("unexpected EOF splitting ${assignment.shardId}")
                        output.write(buf, 0, read)
                        remaining -= read
                    }
                }
                currentCoroutineContext().ensureActive()
                check(tmp.renameTo(dest)) { "rename failed for ${assignment.shardId}" }
                dest
            }
        }
    } }

    private suspend fun copyLocalRange(source: File, dest: File, offset: Long, length: Long) {
        RandomAccessFile(dest, "rw").use { out ->
            out.seek(offset)
            FileInputStream(source).use { input ->
                val buf = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0L) {
                    currentCoroutineContext().ensureActive()
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

    private suspend fun sha256(file: File, offset: Long = 0, length: Long = file.length()): String {
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val buf = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                check(read > 0) { "Truncated model range" }
                digest.update(buf, 0, read); remaining -= read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun digestText(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun requireIdentifier(value: String) {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}"))) { "Unsafe model or shard identifier" }
    }
    private fun requireDigest(value: String) {
        require(value.matches(Regex("[a-fA-F0-9]{64}"))) { "Cluster acquisition requires a trusted SHA-256 manifest" }
    }
    companion object {
        const val SELF_PEER_ID: String = "self"
        private const val MAX_MODEL_BYTES = 1L shl 40
        private val diskLock = Mutex()
    }
}
