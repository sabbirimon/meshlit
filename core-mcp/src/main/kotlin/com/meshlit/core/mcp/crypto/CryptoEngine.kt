package com.meshlit.core.mcp.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Local JCA primitives, no home-grown cipher, password key derivation or key storage. */
object CryptoEngine {
    val operations = listOf("sha256", "sha512", "random_key", "hmac_sha256", "hmac_verify", "encrypt", "decrypt")
    private const val PREFIX = "ML1"
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun bytes(value: String, max: Int): ByteArray {
        require(value.length <= max * 2 && value.length % 2 == 0 && value.all { it in '0'..'9' || it in 'a'..'f' }) { "Canonical lowercase hex required" }
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun run(operation: String, text: String = "", keyHex: String = "", aad: String = "", expectedHex: String = ""): String {
        require(operation in operations) { "Unknown cryptography operation" }
        require(text.length <= 140000 && aad.toByteArray().size <= 8192) { "Cryptography input too large" }
        if (operation == "random_key") return hex(ByteArray(32).also { SecureRandom().nextBytes(it) })
        if (operation == "sha256" || operation == "sha512") {
            val input = text.toByteArray(Charsets.UTF_8); require(input.size <= 65536)
            return hex(MessageDigest.getInstance(if (operation == "sha256") "SHA-256" else "SHA-512").digest(input))
        }
        val key = bytes(keyHex, 64)
        try {
            if (operation.startsWith("hmac")) {
                require(key.size in 16..64); val input = text.toByteArray(Charsets.UTF_8); require(input.size <= 65536)
                val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(key, "HmacSHA256"))
                val actual = mac.doFinal(input)
                return if (operation == "hmac_verify") {
                    val expected = bytes(expectedHex, 32); require(expected.size == 32)
                    MessageDigest.isEqual(actual, expected).toString()
                } else hex(actual)
            }
            require(key.size == 32) { "AES-256 needs a 32-byte random key; this field is not a password" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            if (operation == "encrypt") {
                val plain = text.toByteArray(Charsets.UTF_8); require(plain.size <= 65536)
                val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD("$PREFIX\u0000$aad".toByteArray(Charsets.UTF_8))
                return "$PREFIX.${hex(nonce)}.${hex(cipher.doFinal(plain))}"
            }
            val fields = text.split('.'); require(fields.size == 3 && fields[0] == PREFIX)
            val nonce = bytes(fields[1], 12); require(nonce.size == 12)
            val encrypted = bytes(fields[2], 65552); require(encrypted.size in 16..65552)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD("$PREFIX\u0000$aad".toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(encrypted)
            // Strict decoding avoids quietly changing an authenticated plaintext.
            return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString()
        } finally { key.fill(0) }
    }
}
