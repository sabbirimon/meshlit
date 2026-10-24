package com.meshlit.core.inference.models

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared artifact checks for install and load. These reject invalid containers,
 * not unsupported model architectures; the native loader does the latter. */
object ModelFiles {
    fun validateGguf(file: File) {
        require(file.isFile && file.length() >= 24) { "Model is missing or incomplete" }
        val header = ByteArray(24)
        file.inputStream().use { input ->
            var count = 0
            while (count < header.size) {
                val n = input.read(header, count, header.size - count)
                require(n > 0) { "Incomplete GGUF header" }
                count += n
            }
        }
        require(header.copyOfRange(0, 4).contentEquals("GGUF".toByteArray())) {
            "Not a GGUF model: the source may be HTML, a Git LFS pointer or another format"
        }
        val values = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        values.position(4)
        require(values.int in 2..3) { "Unsupported GGUF version" }
        require(values.long in 1..1_000_000 && values.long in 1..1_000_000) { "Invalid GGUF header" }
    }

    suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Content-based identity prevents basename collisions and stale same-size copies. */
    suspend fun installForSdk(source: File, frameworkDir: File): Pair<String, File> {
        validateGguf(source)
        val hash = sha256(source)
        val id = "local-$hash"
        val directory = File(frameworkDir, id).apply { check(mkdirs() || isDirectory) }
        val target = File(directory, "$id.gguf")
        if (source.canonicalFile == target.canonicalFile) return id to target
        if (target.isFile && target.length() == source.length() && sha256(target) == hash) return id to target
        require(directory.usableSpace >= source.length() + 64L * 1024 * 1024) {
            "Not enough storage to prepare the model for the inference backend"
        }
        val part = File.createTempFile("model-", ".part", directory)
        try {
            source.inputStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }
            require(sha256(part) == hash) { "Model changed while preparing it; retry import" }
            if (!part.renameTo(target)) throw IOException("Cannot atomically install model")
            return id to target
        } finally { part.delete() }
    }
}
