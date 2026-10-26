package com.meshlit.desktop

import com.meshlit.workspace.UsageLedger
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-unlocked encrypted ledger. No plaintext document or passphrase is persisted. */
internal class DesktopUsage(private val directory: Path = Path.of(System.getProperty("user.home"), ".meshlit", "usage")) {
    private val file get() = directory.resolve("history.mul")
    private var key: ByteArray? = null
    private var salt: ByteArray? = null
    private var ledger: UsageLedger? = null
    private var revision = 0L
    private var owner: java.nio.channels.FileChannel? = null
    private var ownership: java.nio.channels.FileLock? = null
    private val random = SecureRandom()
    @Synchronized fun isUnlocked() = ledger != null
    @Synchronized fun journal() = checkNotNull(ledger) { "Unlock the usage history first" }
    @Synchronized fun lock() { revision++; key?.fill(0); key = null; salt?.fill(0); salt = null; ledger = null; ownership?.release(); ownership = null; owner?.close(); owner = null }
    @Synchronized fun unlock(password: CharArray) {
        require(password.size in 12..256) { "Use a usage history passphrase of 12–256 characters" }
        lock()
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file)) { "Usage storage cannot be a symbolic link" }
        Files.createDirectories(directory)
        privatePermissions(directory, "rwx------")
        val lockPath = directory.resolve("owner.lock")
        require(!Files.isSymbolicLink(lockPath))
        owner = java.nio.channels.FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        try { ownership = owner!!.tryLock(); check(ownership != null) { "Usage history is open in another Studio process" } }
        catch (e: Exception) { lock(); throw e }
        try {
        val stored = if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 12_000_048)
            Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(12_000_049) }.also { require(it.size <= 12_000_048) }
        } else null
        val chosenSalt = if (stored != null) {
            require(stored.size >= 49 && stored.copyOfRange(0, 4).contentEquals(byteArrayOf(77, 85, 76, 1))) { "Unsupported usage vault" }
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
            val opened = UsageLedger({ text }, { persist(it, epoch) })
            opened.snapshot() // Validate before exposing the unlocked state.
            if (stored == null) opened.setPolicy(false, null, "USD")
            opened.recoverInterrupted()
            ledger = opened
        } catch (e: Exception) { derived.fill(0); lock(); throw IllegalStateException("Cannot unlock usage history: wrong passphrase or damaged storage", e) }
        } catch(e: Exception) { lock(); throw e }
    }
    @Synchronized private fun persist(text: String, epoch: Long) {
        check(epoch == revision) { "Usage history session changed" }
        val activeKey = checkNotNull(key) { "Usage history was locked" }
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file))
        val header = byteArrayOf(77, 85, 76, 1) + checkNotNull(salt)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(activeKey, "AES"), GCMParameterSpec(128, nonce)); cipher.updateAAD(header)
        val plain = text.toByteArray(Charsets.UTF_8)
        val payload = try { header + nonce + cipher.doFinal(plain) } finally { plain.fill(0) }
        val temporary = Files.createTempFile(directory, "history-", ".tmp")
        try {
            privatePermissions(temporary, "rw-------")
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(payload)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // Persist the renamed directory entry on POSIX hosts as well as the encrypted file bytes.
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
            }
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun privatePermissions(path: Path, permissions: String) {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    }
}
