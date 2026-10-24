package com.meshlit.core.trust
import kotlinx.serialization.Serializable

@Serializable
data class HandshakeRequest(
    val nodeId: String,
    val publicKey: String = "",
    val requestedTier: String,
    val nonce: String,
    val signatureHex: String = "",
    val identityProof: IdentityProof? = null,
)

@Serializable
data class HandshakeResponse(
    val acceptedTier: String,
    val tokenHex: String,
    val expiresAtMs: Long,
    val serverPublicKey: String = "",
    val serverNodeId: String,
    val identityProof: IdentityProof? = null,
    val responseSignature: String = "",
)


fun HandshakeResponse.message(): ByteArray =
    "meshlit.handshake.v1\n$acceptedTier\n$tokenHex\n$expiresAtMs\n$serverPublicKey\n$serverNodeId\n${identityProof?.nonce.orEmpty()}".toByteArray(Charsets.UTF_8)
