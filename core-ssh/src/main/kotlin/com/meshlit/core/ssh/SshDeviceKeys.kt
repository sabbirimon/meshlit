package com.meshlit.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

/** A device-owned SSH client key. The private part must stay in encrypted storage. */
data class GeneratedSshDeviceKey(val publicKey: String, val privateKey: String)

object SshDeviceKeys {
    fun generate(): GeneratedSshDeviceKey {
        val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
        return try {
            val publicBytes = ByteArrayOutputStream()
            val privateBytes = ByteArrayOutputStream()
            keyPair.writePublicKey(publicBytes, "meshlit-device")
            keyPair.writePrivateKey(privateBytes)
            val publicKey = publicBytes.toString(Charsets.UTF_8.name()).trim()
            val privateKey = privateBytes.toString(Charsets.UTF_8.name())
            check(publicKey.startsWith("ecdsa-sha2-nistp256 ") && publicKey.length <= 4096) {
                "SSH public key export failed"
            }
            check(privateKey.contains("PRIVATE KEY")) { "SSH private key export failed" }
            GeneratedSshDeviceKey(publicKey, privateKey)
        } finally {
            keyPair.dispose()
        }
    }
}
