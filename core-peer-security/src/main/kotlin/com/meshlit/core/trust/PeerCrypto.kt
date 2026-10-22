package com.meshlit.core.trust

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.*
import java.security.spec.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, domain-separated identity proofs. QR supplies the expected key out of band. */
@Serializable
data class IdentityProof(val nodeId: String, val publicKey: String, val nonce: String,
    val issuedAtMs: Long, val signature: String)

@Serializable
data class RecoverableIdentity(val nodeId: String, val publicKey: String, val privateKey: String)

object PeerCrypto {
    private val random = SecureRandom()
    private val hexDigits = "0123456789abcdef".toCharArray()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun hex(bytes: ByteArray): String = String(CharArray(bytes.size * 2) { index ->
        val value = bytes[index / 2].toInt() and 0xff
        hexDigits[if (index % 2 == 0) value ushr 4 else value and 0x0f]
    })
    fun bytes(hex: String): ByteArray {
        require(hex.length % 2 == 0 && hex.length <= 32768 && hex.all { it in "0123456789abcdefABCDEF" }) { "Invalid encoded data" }
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun randomHex(size: Int = 32): String = hex(ByteArray(size).also(random::nextBytes))
    fun fingerprint(publicKey: String): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes(publicKey)))
    fun generate(nodeId: String): RecoverableIdentity {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()
        return RecoverableIdentity(nodeId, hex(pair.public.encoded), hex(pair.private.encoded))
    }
    private fun proofMessage(node: String, key: String, nonce: String, time: Long) =
        "meshlit.identity.v1\n$node\n$key\n$nonce\n$time".toByteArray(Charsets.UTF_8)
    fun sign(identity: RecoverableIdentity, message: ByteArray): String {
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes(identity.privateKey)))
        return hex(Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(message) }.sign())
    }
    internal fun verifiedPublicKey(key: String): PublicKey {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes(key)))
        val params = (pub as java.security.interfaces.ECPublicKey).params
        require(params.curve.field.fieldSize == 256 && params.order == java.math.BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16))
        return pub
    }
    internal fun verifySignature(key: PublicKey, message: ByteArray, signature: String): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(message) }.verify(bytes(signature))
    }.getOrDefault(false)
    fun verifySignature(key: String, message: ByteArray, signature: String): Boolean = runCatching {
        verifySignature(verifiedPublicKey(key), message, signature)
    }.getOrDefault(false)
    fun prove(identity: RecoverableIdentity, nonce: String, now: Long = System.currentTimeMillis()): IdentityProof {
        require(nonce.matches(Regex("[a-f0-9]{64}"))) { "Invalid identity challenge" }
        return IdentityProof(identity.nodeId, identity.publicKey, nonce, now,
            sign(identity, proofMessage(identity.nodeId, identity.publicKey, nonce, now)))
    }
    fun verify(proof: IdentityProof, expectedNode: String, expectedKey: String, nonce: String,
        now: Long = System.currentTimeMillis()): Boolean {
        if (expectedKey.isBlank() || proof.nodeId != expectedNode || proof.publicKey != expectedKey || proof.nonce != nonce) return false
        if (proof.issuedAtMs < now - 120_000 || proof.issuedAtMs > now + 30_000) return false
        return verifySignature(proof.publicKey, proofMessage(proof.nodeId, proof.publicKey, proof.nonce, proof.issuedAtMs), proof.signature)
    }
    // PBKDF2-HMAC-SHA1 remains available at the API-24 floor; high iteration count, 256-bit AES key.
    private fun recoveryKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        require(passphrase.size >= 12) { "Use a passphrase of at least 12 characters" }
        val spec = PBEKeySpec(passphrase, salt, 600_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    fun backup(identity: RecoverableIdentity, passphrase: CharArray): String {
        val salt = ByteArray(16).also(random::nextBytes); val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, recoveryKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD("meshlit.backup.v1".toByteArray())
        val encrypted = cipher.doFinal(json.encodeToString(RecoverableIdentity.serializer(), identity).toByteArray())
        return "mesh-backup-v1.${hex(salt)}.${hex(iv)}.${hex(encrypted)}"
    }
    fun restore(backup: String, passphrase: CharArray): RecoverableIdentity {
        require(backup.length <= 16384) { "Backup too large" }
        val parts = backup.trim().split('.')
        require(parts.size == 4 && parts[0] == "mesh-backup-v1") { "Invalid recovery backup" }
        val salt = bytes(parts[1]); val iv = bytes(parts[2])
        require(salt.size == 16 && iv.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, recoveryKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD("meshlit.backup.v1".toByteArray())
        val identity = json.decodeFromString(RecoverableIdentity.serializer(), String(cipher.doFinal(bytes(parts[3])), Charsets.UTF_8))
        require(identity.nodeId.isNotBlank() && identity.nodeId.length <= 128)
        val proof = prove(identity, randomHex())
        require(verify(proof, identity.nodeId, identity.publicKey, proof.nonce)) { "Backup key mismatch" }
        return identity
    }
}
