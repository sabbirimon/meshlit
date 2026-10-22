package com.meshlit.desktop.peering

import com.meshlit.core.trust.*
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** Explicitly unlocked identity vault. Secrets never appear in address-book records. */
class PeerWallet(private val directory: Path) {
    private val identityFile get() = directory.resolve("identity.enc")
    private val receiptFile get() = directory.resolve("receipts.json")
    private val serializer = ListSerializer(ContributionReceipt.serializer())
    @Volatile private var unlocked: RecoverableIdentity? = null
    val exists get() = Files.exists(identityFile, LinkOption.NOFOLLOW_LINKS)
    val identity get() = unlocked ?: error("Unlock the identity vault first")
    fun lock() { unlocked = null }
    @Synchronized fun create(passphrase: CharArray): RecoverableIdentity {
        require(!exists) { "An identity already exists; unlock or restore it" }
        val created = PeerCrypto.generate(UUID.randomUUID().toString())
        write(identityFile, PeerCrypto.backup(created, passphrase))
        unlocked = created
        return created
    }
    @Synchronized fun unlock(passphrase: CharArray): RecoverableIdentity =
        PeerCrypto.restore(read(identityFile, 32768), passphrase).also { key ->
            require(ContributionLedger.verify(receipts(), key.nodeId, key.publicKey)) { "Receipt history belongs to another identity or is corrupted" }
            unlocked = key
        }
    @Synchronized fun restore(raw: String, passphrase: CharArray): RecoverableIdentity {
        require(raw.length <= 32768)
        val recovered = PeerCrypto.restore(raw, passphrase)
        val history = receipts()
        require(ContributionLedger.verify(history, recovered.nodeId, recovered.publicKey)) { "Restore would detach the current receipt history" }
        if (exists) require(identity.nodeId == recovered.nodeId && identity.publicKey == recovered.publicKey) { "An existing different identity must not be overwritten" }
        write(identityFile, raw); unlocked = recovered
        return recovered
    }
    @Synchronized fun exportIdentity(): String { identity; return read(identityFile, 32768) }
    @Synchronized fun receipts(): List<ContributionReceipt> = if (!Files.exists(receiptFile, LinkOption.NOFOLLOW_LINKS)) emptyList()
        else PeerCrypto.json.decodeFromString(serializer, read(receiptFile, 16 * 1024 * 1024))
    @Synchronized fun verifiedReceipts(): List<ContributionReceipt> = receipts().also {
        require(ContributionLedger.verify(it, identity.nodeId, identity.publicKey)) { "Invalid receipt signatures or history" }
    }
    @Synchronized fun exportReceipts() = PeerCrypto.json.encodeToString(serializer, verifiedReceipts())
    @Synchronized fun importReceipts(raw: String) {
        require(raw.length <= 16 * 1024 * 1024)
        val incoming = PeerCrypto.json.decodeFromString(serializer, raw)
        require(incoming.size <= 10000 && ContributionLedger.verify(incoming, identity.nodeId, identity.publicKey)) { "Wrong owner or invalid receipt signatures" }
        val current = verifiedReceipts()
        val overlap = minOf(current.size, incoming.size)
        require(current.take(overlap) == incoming.take(overlap)) { "Conflicting receipt history" }
        if (incoming.size > current.size) write(receiptFile, raw)
    }
    /** Call only for completed hosted jobs with real backend token accounting. */
    @Synchronized fun recordHostedJob(jobId: String, generatedTokens: Int) {
        val current = receipts()
        val updated = ContributionLedger.append(current, identity, jobId, generatedTokens)
        if (updated.size == current.size) return
        require(updated.size <= 10000)
        write(receiptFile, PeerCrypto.json.encodeToString(serializer, updated))
    }
    private fun read(file: Path, limit: Int): String {
        require(!Files.isSymbolicLink(directory) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Invalid wallet file" }
        require(Files.size(file) <= limit)
        return Files.newInputStream(file).use { input ->
            val bytes = input.readNBytes(limit + 1); require(bytes.size <= limit)
            bytes.toString(Charsets.UTF_8)
        }
    }
    private fun write(file: Path, value: String) {
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file)) { "Invalid wallet directory" }
        Files.createDirectories(directory)
        fun permissions(path: Path, mode: String) {
            if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null)
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode))
        }
        permissions(directory, "rwx------")
        val temp = Files.createTempFile(directory, "mesh-wallet-", ".tmp")
        try {
            permissions(temp, "rw-------")
            java.io.FileOutputStream(temp.toFile()).use { out -> out.write(value.toByteArray(Charsets.UTF_8)); out.fd.sync() }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
