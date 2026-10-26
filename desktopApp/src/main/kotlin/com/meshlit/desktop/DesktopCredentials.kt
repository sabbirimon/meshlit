package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Explicitly unlocked, endpoint/protocol/purpose-bound local credential storage. */
internal class DesktopCredentials(private val directory: Path = Path.of(System.getProperty("user.home"), ".meshlit", "credentials")) {
    private val file get() = directory.resolve("credentials.mcv")
    private var key: ByteArray? = null
    private var salt: ByteArray? = null
    private var entries: Map<String, String>? = null
    private var revision = 0L
    private var owner: java.nio.channels.FileChannel? = null
    private var ownership: java.nio.channels.FileLock? = null
    private val random = SecureRandom()
    @Synchronized fun isUnlocked() = entries != null
    internal class Lease internal constructor(internal val binding: String, internal val epoch: Long)
    private fun binding(host: HostEndpoint) = "interactive-inference|${host.protocol.name}|${host.base}"
    @Synchronized fun save(host: HostEndpoint, secret: String) {
        require(secret.length in 1..8192 && secret.none(Char::isISOControl))
        val next = checkNotNull(entries) { "Unlock credentials first" } + (binding(host) to secret)
        require(next.size <= 64)
        commit(next)
    }
    @Synchronized fun remove(host: HostEndpoint) {
        commit(checkNotNull(entries) { "Unlock credentials first" } - binding(host))
    }
    @Synchronized fun lease(host: HostEndpoint): Lease {
        check(checkNotNull(entries) { "Unlock credentials first" }.containsKey(binding(host))) { "No saved credential for this host and protocol" }
        return Lease(binding(host), revision)
    }
    @Synchronized fun resolve(lease: Lease, host: HostEndpoint): String {
        check(lease.epoch == revision && lease.binding == binding(host)) { "Credential access changed" }
        return checkNotNull(checkNotNull(entries)[lease.binding]) { "Credential was removed" }
    }
    private fun commit(next: Map<String, String>) {
        try {
            persist(JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString(), revision)
            entries = next
            revision++ // Replacement/removal invalidates every issued lease.
        } catch (e: Exception) { lock(); throw IllegalStateException("Credential storage failed; unlock again before use", e) }
    }
    @Synchronized fun lock() { revision++; key?.fill(0); key = null; salt?.fill(0); salt = null; entries = null; ownership?.release(); ownership = null; owner?.close(); owner = null }
    @Synchronized fun unlock(password: CharArray) {
        require(password.size in 12..256) { "Use a credential vault passphrase of 12–256 characters" }
        lock()
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file)) { "Credential storage cannot be a symbolic link" }
        Files.createDirectories(directory)
        privatePermissions(directory, "rwx------")
        val lockPath = directory.resolve("owner.lock")
        require(!Files.isSymbolicLink(lockPath))
        owner = java.nio.channels.FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        try { ownership = owner!!.tryLock(); check(ownership != null) { "Credential vault is open in another Studio process" } }
        catch (e: Exception) { lock(); throw e }
        try {
        val stored = if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 1_200_048)
            Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(1_200_049) }.also { require(it.size <= 1_200_048) }
        } else null
        val chosenSalt = if (stored != null) {
            require(stored.size >= 49 && stored.copyOfRange(0, 4).contentEquals(byteArrayOf(77, 67, 86, 1))) { "Unsupported credential vault" }
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
            val opened = text?.let { value ->
                val obj = Json.parseToJsonElement(value).jsonObject
                require(obj.size <= 64)
                obj.mapValues { (binding, secret) ->
                    require(binding.length in 1..2200 && binding.startsWith("interactive-inference|"))
                    require(secret is JsonPrimitive && secret.isString)
                    secret.content.also { require(it.length in 1..8192 && it.none(Char::isISOControl)) }
                }
            } ?: emptyMap()
            entries = opened
            if (stored == null) commit(opened)
        } catch (e: Exception) { derived.fill(0); lock(); throw IllegalStateException("Cannot unlock credential vault: wrong passphrase or damaged storage", e) }
        } catch(e: Exception) { lock(); throw e }
    }
    @Synchronized private fun persist(text: String, epoch: Long) {
        check(epoch == revision) { "Credential vault session changed" }
        val activeKey = checkNotNull(key) { "Credential vault was locked" }
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file))
        val header = byteArrayOf(77, 67, 86, 1) + checkNotNull(salt)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(activeKey, "AES"), GCMParameterSpec(128, nonce)); cipher.updateAAD(header)
        val plain = text.toByteArray(Charsets.UTF_8)
        val payload = try { require(plain.size <= 1_200_000); header + nonce + cipher.doFinal(plain) } finally { plain.fill(0) }
        val temporary = Files.createTempFile(directory, "credentials-", ".tmp")
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
