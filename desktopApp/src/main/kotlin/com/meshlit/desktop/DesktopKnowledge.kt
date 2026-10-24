package com.meshlit.desktop

import com.meshlit.workspace.KnowledgeCorpus
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-unlocked encrypted corpus. No plaintext document or passphrase is persisted. */
internal class DesktopKnowledge(private val directory: Path = Path.of(System.getProperty("user.home"), ".meshlit", "knowledge")) {
    private val file get() = directory.resolve("library.mkl")
    private var key: ByteArray? = null
    private var salt: ByteArray? = null
    private var corpus: KnowledgeCorpus? = null
    private var revision = 0L
    private val random = SecureRandom()
    @Synchronized fun isUnlocked() = corpus != null
    @Synchronized fun lockedCorpus() = checkNotNull(corpus) { "Unlock the knowledge library first" }
    @Synchronized fun lock() { revision++; key?.fill(0); key = null; salt?.fill(0); salt = null; corpus = null }
    fun context(query: String, remote: Boolean): String {
        val current = synchronized(this) { revision to corpus }
        val result = current.second?.context(query, remote).orEmpty()
        return synchronized(this) { if (revision == current.first) result else "" }
    }
    @Synchronized fun unlock(password: CharArray) {
        require(password.size in 12..256) { "Use a library passphrase of 12–256 characters" }
        lock()
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file)) { "Knowledge storage cannot be a symbolic link" }
        Files.createDirectories(directory)
        privatePermissions(directory, "rwx------")
        val stored = if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 8_000_000)
            Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(8_000_001) }.also { require(it.size <= 8_000_000) }
        } else null
        val chosenSalt = if (stored != null) {
            require(stored.size >= 49 && stored.copyOfRange(0, 4).contentEquals(byteArrayOf(77, 75, 76, 1))) { "Unsupported knowledge vault" }
            stored.copyOfRange(4, 20)
        } else ByteArray(16).also(random::nextBytes)
        val spec = PBEKeySpec(password, chosenSalt, 210_000, 256)
        val derived = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        try {
            val text = stored?.let { encrypted ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, encrypted.copyOfRange(20, 32)))
                cipher.updateAAD(encrypted.copyOfRange(0, 20))
                val plain = cipher.doFinal(encrypted.copyOfRange(32, encrypted.size))
                try { plain.decodeToString(throwOnInvalidSequence = true) } finally { plain.fill(0) }
            }
            key = derived; salt = chosenSalt
            val epoch = revision
            val opened = KnowledgeCorpus({ text }, { persist(it, epoch) })
            opened.snapshot() // Validate before exposing the unlocked state.
            if (stored == null) opened.setPolicy(com.meshlit.workspace.KnowledgePolicy())
            corpus = opened
        } catch (e: Exception) { derived.fill(0); lock(); throw IllegalStateException("Cannot unlock knowledge library: wrong passphrase or damaged storage", e) }
    }
    @Synchronized private fun persist(text: String, epoch: Long) {
        check(epoch == revision) { "Knowledge library session changed" }
        val activeKey = checkNotNull(key) { "Knowledge library was locked" }
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file))
        val header = byteArrayOf(77, 75, 76, 1) + checkNotNull(salt)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(activeKey, "AES"), GCMParameterSpec(128, nonce)); cipher.updateAAD(header)
        val plain = text.toByteArray(Charsets.UTF_8)
        val payload = try { header + nonce + cipher.doFinal(plain) } finally { plain.fill(0) }
        val temporary = Files.createTempFile(directory, "library-", ".tmp")
        try {
            privatePermissions(temporary, "rw-------")
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(payload)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun privatePermissions(path: Path, permissions: String) {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    }
}
