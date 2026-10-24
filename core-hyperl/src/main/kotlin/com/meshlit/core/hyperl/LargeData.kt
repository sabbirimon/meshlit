// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import androidx.annotation.RequiresApi
import java.io.InputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable private data class DataRow(val index: Int, val bytes: Int, val sha256: String, val type: String = "chunk")
@Serializable private data class DataFooter(val totalBytes: Long, val chunks: Int, val sha256: String, val type: String = "footer")
data class DatasetSummary(val bytes: Long, val chunks: Int, val sha256: String)

/** Explicit local encrypted storage. HLM2 writes; authenticated HLM1 reads remain supported. */
@RequiresApi(26)
object LargeData {
    const val CHUNK_BYTES = 4 * 1024 * 1024
    const val MAX_BYTES = 4L * 1024 * 1024 * 1024 * 1024
    const val MAX_CHUNKS = 1048576
    private val random = SecureRandom()
    private val digestPattern = Regex("[a-f0-9]{64}")
    private val manifestJson = Json(HyperLCodec.json) { encodeDefaults = true; prettyPrint = false }

    private fun privateAttributes(parent: Path) =
        if (Files.getFileStore(parent).supportsFileAttributeView("posix"))
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        else emptyArray()

    fun keygen(file: Path) {
        Files.createFile(file, *privateAttributes(file.toAbsolutePath().parent))
        try {
            Files.write(file, ByteArray(32).also(random::nextBytes), StandardOpenOption.WRITE)
        } catch (error: Exception) {
            Files.deleteIfExists(file)
            throw error
        }
    }

    private fun key(file: Path): SecretKeySpec {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == 32L) {
            "Expected an existing 32-byte AES key file"
        }
        return SecretKeySpec(Files.readAllBytes(file), "AES")
    }

    private fun keyId(key: SecretKeySpec) = hash(key.encoded)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun cipher(mode: Int, key: SecretKeySpec, iv: ByteArray, aad: String) =
        Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(mode, key, GCMParameterSpec(128, iv))
            it.updateAAD(aad.toByteArray(Charsets.UTF_8))
        }

    private fun privateDirectory(parent: Path) = Files.createTempDirectory(parent, ".hyperl-data-").also {
        if (Files.getFileStore(it).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwx------"))
        }
    }

    private fun cleanup(dir: Path) {
        Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun id(dir: Path): String {
        val file = dir.resolve("id")
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == 36L)
        return Files.readAllBytes(file).toString(Charsets.UTF_8).also { require(UUID.fromString(it).toString() == it) }
    }

    private fun chunk(dir: Path, index: Int, version: Int): Path {
        if (version == 1) return dir.resolve("chunk_$index.aesgcm")
        val root = dir.resolve("chunks")
        val group = root.resolve((index / 1024).toString(16).padStart(4, '0'))
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) &&
            Files.isDirectory(group, LinkOption.NOFOLLOW_LINKS)) { "Invalid chunk directory" }
        return group.resolve("chunk_$index.aesgcm")
    }

    private class ManifestReader(val input: DataInputStream, val key: SecretKeySpec,
                                 val id: String, val version: Int, val keyId: String?) : AutoCloseable {
        private var index = 0
        fun aad(index: Int, bytes: Int) = if (version == 1) "hyperl-dataset/1:$id:$index:$bytes"
            else "hyperl-dataset/2:$id:$keyId:$index:$bytes"
        fun next(): String? {
            val first = input.read()
            if (first < 0) return null
            val second = input.read()
            require(second >= 0 && index <= MAX_CHUNKS) { "Truncated/oversized manifest" }
            val length = (first shl 8) or second
            require(length in 28..540)
            val encrypted = ByteArray(length).also(input::readFully)
            require(encrypted.size == length) { "Truncated manifest record" }
            val aad = if (version == 1) "hyperl-dataset/1:$id:manifest:${index++}"
                else "hyperl-dataset/2:$id:$keyId:manifest:${index++}"
            return cipher(Cipher.DECRYPT_MODE, key, encrypted.copyOfRange(0, 12), aad)
                .doFinal(encrypted, 12, encrypted.size - 12).toString(Charsets.UTF_8)
        }
        override fun close() = input.close()
    }

    private fun manifest(dir: Path, key: SecretKeySpec): ManifestReader {
        val file = dir.resolve("manifest.aesgcm")
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) in 32..600000000)
        val input = DataInputStream(Files.newInputStream(file).buffered())
        try {
            val magic = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
            val version = when (magic) { "HLM1" -> 1; "HLM2" -> 2; else -> error("Unknown manifest format") }
            val keyId = if (version == 2) {
                val keyFile = dir.resolve("key-id")
                require(Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS) && Files.size(keyFile) == 64L)
                Files.readAllBytes(keyFile).toString(Charsets.UTF_8).also {
                    require(it.matches(digestPattern) && it == keyId(key)) { "Dataset key ID mismatch" }
                }
            } else null
            return ManifestReader(input, key, id(dir), version, keyId)
        } catch (error: Exception) {
            input.close()
            throw error
        }
    }

    private fun record(text: String, version: Int): Pair<DataRow?, DataFooter?> {
        val fields = HyperLCodec.json.parseToJsonElement(text).jsonObject
        val rowKeys = setOf("index", "bytes", "sha256")
        val footerKeys = setOf("totalBytes", "chunks", "sha256")
        val kind = if (version == 1) when (fields.keys) {
            rowKeys -> "chunk"
            footerKeys -> "footer"
            else -> error("Unknown legacy manifest record")
        } else {
            val type = fields["type"]?.jsonPrimitive
            require(type != null && type.isString)
            type.content
        }
        val required = when (kind) { "chunk" -> rowKeys; "footer" -> footerKeys; else -> error("Unknown manifest record type") }
        require(fields.keys == if (version == 1) required else required + "type") { "Unknown manifest record field" }
        require(fields.getValue("sha256").jsonPrimitive.let { it.isString && it.content.matches(digestPattern) })
        for (name in required - "sha256") {
            val number = fields.getValue(name).jsonPrimitive
            require(!number.isString && number.longOrNull != null) { "Invalid manifest integer" }
        }
        return if (kind == "chunk") HyperLCodec.json.decodeFromJsonElement<DataRow>(fields) to null
            else null to HyperLCodec.json.decodeFromJsonElement<DataFooter>(fields)
    }

    private suspend fun verified(directory: Path, key: SecretKeySpec, maxBytes: Long,
                                 consume: (ByteArray, Int) -> Unit): DatasetSummary {
        var bytes = 0L
        var chunks = 0
        var footer: DataFooter? = null
        val digest = MessageDigest.getInstance("SHA-256")
        manifest(directory, key).use { reader ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val text = reader.next() ?: break
                require(footer == null) { "Trailing manifest rows" }
                val (row, end) = record(text, reader.version)
                if (end != null) { footer = end; continue }
                val part = checkNotNull(row)
                require(part.index == chunks && chunks < MAX_CHUNKS && part.bytes in 1..CHUNK_BYTES)
                bytes += part.bytes
                require(bytes <= maxBytes) { "Dataset limit exceeded" }
                val file = chunk(directory, chunks, reader.version)
                require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == part.bytes.toLong() + 28)
                val encrypted = Files.readAllBytes(file)
                val data = cipher(Cipher.DECRYPT_MODE, key, encrypted.copyOfRange(0, 12), reader.aad(chunks, part.bytes))
                    .doFinal(encrypted, 12, encrypted.size - 12)
                try {
                    require(hash(data) == part.sha256) { "Chunk integrity mismatch" }
                    digest.update(data)
                    consume(data, chunks)
                } finally { data.fill(0) }
                chunks++
            }
        }
        val expected = checkNotNull(footer) { "Missing authenticated footer" }
        val sha = digest.digest().toHex()
        require(expected.totalBytes == bytes && expected.chunks == chunks && expected.sha256 == sha) { "Dataset integrity mismatch" }
        return DatasetSummary(bytes, chunks, sha)
    }

    private class DatasetWriter(val stage: Path, val key: SecretKeySpec) : AutoCloseable {
        val id = UUID.randomUUID().toString()
        val keyId = keyId(key)
        val rows: DataOutputStream
        init {
            Files.write(stage.resolve("id"), id.toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE_NEW)
            Files.write(stage.resolve("key-id"), keyId.toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE_NEW)
            Files.createDirectory(stage.resolve("chunks"))
            rows = DataOutputStream(Files.newOutputStream(stage.resolve("manifest.aesgcm"), StandardOpenOption.CREATE_NEW).buffered())
            rows.write("HLM2".toByteArray(Charsets.US_ASCII))
        }
        fun frame(index: Int, text: String) {
            val data = text.toByteArray(Charsets.UTF_8)
            require(data.size <= 512)
            val iv = ByteArray(12).also(random::nextBytes)
            val encrypted = cipher(Cipher.ENCRYPT_MODE, key, iv, "hyperl-dataset/2:$id:$keyId:manifest:$index").doFinal(data)
            rows.writeShort(iv.size + encrypted.size)
            rows.write(iv)
            rows.write(encrypted)
        }
        fun append(data: ByteArray, index: Int) {
            val group = stage.resolve("chunks").resolve((index / 1024).toString(16).padStart(4, '0'))
            if (!Files.exists(group, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(group)
            val iv = ByteArray(12).also(random::nextBytes)
            Files.newOutputStream(chunk(stage, index, 2), StandardOpenOption.CREATE_NEW).use { output ->
                output.write(iv)
                output.write(cipher(Cipher.ENCRYPT_MODE, key, iv, "hyperl-dataset/2:$id:$keyId:$index:${data.size}").doFinal(data))
            }
            frame(index, manifestJson.encodeToString(DataRow(index, data.size, hash(data))))
        }
        fun finish(summary: DatasetSummary) = frame(summary.chunks,
            manifestJson.encodeToString(DataFooter(summary.bytes, summary.chunks, summary.sha256)))
        override fun close() = rows.close()
    }

    /** Reserve without overwrite, publish the authenticated manifest last. */
    private suspend fun publishDataset(stage: Path, destination: Path) {
        currentCoroutineContext().ensureActive()
        val attributes = if (Files.getFileStore(destination.toAbsolutePath().parent).supportsFileAttributeView("posix"))
            arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        else emptyArray()
        Files.createDirectory(destination, *attributes)
        var published = false
        try {
            for (name in listOf("id", "key-id", "chunks", "manifest.aesgcm")) {
                currentCoroutineContext().ensureActive()
                Files.move(stage.resolve(name), destination.resolve(name), StandardCopyOption.ATOMIC_MOVE)
            }
            published = true
        } finally { if (!published) cleanup(destination) }
    }

    suspend fun import(source: Path, destination: Path, keyFile: Path, maxBytes: Long): DatasetSummary {
        require(maxBytes in 1..MAX_BYTES && Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        return Files.newInputStream(source).use { import(it, destination, keyFile, maxBytes) }
    }

    suspend fun import(source: InputStream, destination: Path, keyFile: Path, maxBytes: Long): DatasetSummary {
        require(maxBytes in 1..MAX_BYTES)
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) { "Destination exists; import never overwrites" }
        requireStreamingHeadroom()
        val key = key(keyFile)
        val stage = privateDirectory(destination.toAbsolutePath().parent)
        try {
            var bytes = 0L
            var chunks = 0
            val digest = MessageDigest.getInstance("SHA-256")
            val summary: DatasetSummary
            DatasetWriter(stage, key).use { writer ->
                source.let { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val data = readChunk(input)
                        if (data.isEmpty()) break
                        try {
                            bytes += data.size
                            require(bytes <= maxBytes && chunks < MAX_CHUNKS) { "Dataset limit exceeded" }
                            digest.update(data)
                            writer.append(data, chunks++)
                        } finally { data.fill(0) }
                    }
                }
                summary = DatasetSummary(bytes, chunks, digest.digest().toHex())
                writer.finish(summary)
            }
            publishDataset(stage, destination)
            return summary
        } finally { cleanup(stage) }
    }

    suspend fun verify(directory: Path, keyFile: Path, maxBytes: Long): DatasetSummary {
        require(maxBytes in 1..MAX_BYTES)
        requireStreamingHeadroom()
        return verified(directory, key(keyFile), maxBytes) { _, _ -> }
    }

    private fun requireStreamingHeadroom() {
        val runtime = Runtime.getRuntime()
        val available = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) -
            32L * 1024 * 1024).coerceAtLeast(0) / 2
        require(available >= 32L * 1024 * 1024) { "Insufficient heap headroom for encrypted dataset buffers" }
    }

    private suspend fun readChunk(input: InputStream): ByteArray {
        val buffer = ByteArray(CHUNK_BYTES)
        try {
            var used = 0
            while (used < buffer.size) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer, used, minOf(64 * 1024, buffer.size - used))
                if (count < 0) break
                check(count > 0) { "Input provider made no progress" }
                used += count
            }
            return buffer.copyOf(used)
        } finally { buffer.fill(0) }
    }

    suspend fun export(directory: Path, destination: Path, keyFile: Path, maxBytes: Long): DatasetSummary {
        require(maxBytes in 1..MAX_BYTES && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) { "Destination exists; export never overwrites" }
        requireStreamingHeadroom()
        val key = key(keyFile)
        val stageDir = privateDirectory(destination.toAbsolutePath().parent)
        val stage = stageDir.resolve("output")
        try {
            Files.createFile(stage, *privateAttributes(stageDir))
            val summary = Files.newOutputStream(stage).use { output ->
                verified(directory, key, maxBytes) { data, _ -> output.write(data) }
            }
            currentCoroutineContext().ensureActive()
            // Same-filesystem hard-link creation fails atomically if the destination
            // already exists. Unsupported filesystems fail instead of overwriting.
            Files.createLink(destination, stage)
            return summary
        } finally { cleanup(stageDir) }
    }

    suspend fun rekey(directory: Path, destination: Path, oldKeyFile: Path,
                      newKeyFile: Path, maxBytes: Long): DatasetSummary {
        require(maxBytes in 1..MAX_BYTES && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) { "Destination exists; rekey never overwrites" }
        requireStreamingHeadroom()
        val oldKey = key(oldKeyFile)
        val newKey = key(newKeyFile)
        require(keyId(oldKey) != keyId(newKey)) { "Choose a different new key" }
        val stage = privateDirectory(destination.toAbsolutePath().parent)
        try {
            val summary = DatasetWriter(stage, newKey).use { writer ->
                val verified = verified(directory, oldKey, maxBytes) { data, index -> writer.append(data, index) }
                writer.finish(verified)
                verified
            }
            publishDataset(stage, destination)
            return summary
        } finally { cleanup(stage) }
    }
}
