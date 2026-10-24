package com.meshlit.core.trust

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned ECDSA pairing messages. Keys must match a QR/invite identity approved by the owner. */
/**
 * Minimal HTTP transport interface the handshake runs over. The
 * `core-trust` module has no Android / OkHttp dependency, so the
 * app-side caller plugs in an adapter (e.g. one built on OkHttp's
 * `Call.execute()`).
 */
interface HandshakeTransport {
    /**
     * POST `body` to `baseUrl + path`, returning the response body as
     * a string. Implementations should reject on non-2xx with a
     * [MeshlitError.Network] so the caller can distinguish a refused
     * connection from a 403 / tier rejection.
     */
    suspend fun postJson(baseUrl: String, path: String, body: String): MeshlitResult<String>
}

/** Mutual signed enrollment; legacy callers without a pinned key fail closed. */
suspend fun runHandshake(
    transport: HandshakeTransport,
    trustStore: TrustStore,
    selfNodeId: NodeId,
    peerBaseUrl: String,
    requestedTier: TrustTier,
    nonce: String,
    expectedPeerNodeId: String = "",
    expectedPeerPublicKey: String = "",
    selfIdentity: RecoverableIdentity? = null,
    onSession: ((HandshakeResponse) -> Unit)? = null,
): MeshlitResult<DeviceTrustPolicy> {
    if (selfIdentity == null || selfIdentity.nodeId != selfNodeId.value || expectedPeerNodeId.isBlank() || expectedPeerPublicKey.isBlank() || !nonce.matches(Regex("[a-f0-9]{64}"))) {
        return MeshlitResult.Failure(MeshlitError.Auth("handshake.identity_required"))
    }
    val req = HandshakeRequest(
        nodeId = selfNodeId.value,
        publicKey = selfIdentity.publicKey,
        requestedTier = requestedTier.tag,
        nonce = nonce,
        identityProof = PeerCrypto.prove(selfIdentity, nonce),
    )
    val body = Json.encodeToString(HandshakeRequest.serializer(), req)
    val result = transport.postJson(peerBaseUrl, "/v1/handshake", body)
    val raw = when (result) {
        is MeshlitResult.Success -> result.value
        is MeshlitResult.Failure -> return MeshlitResult.Failure(result.error)
    }
    val parsed = runCatching {
        Json { ignoreUnknownKeys = true }
            .decodeFromString(HandshakeResponse.serializer(), raw)
    }.getOrElse {
        return MeshlitResult.Failure(MeshlitError.Auth("handshake.bad_response", it))
    }
    val accepted = TrustTier.fromTag(parsed.acceptedTier)
        ?: return MeshlitResult.Failure(MeshlitError.Auth("handshake.unknown_tier:${parsed.acceptedTier}"))
    val proof = parsed.identityProof
        ?: return MeshlitResult.Failure(MeshlitError.Auth("handshake.unsigned_response"))
    if (parsed.serverNodeId != expectedPeerNodeId || parsed.serverPublicKey != expectedPeerPublicKey ||
        !PeerCrypto.verify(proof, expectedPeerNodeId, expectedPeerPublicKey, nonce) ||
        parsed.expiresAtMs <= System.currentTimeMillis() || accepted != requestedTier ||
        parsed.expiresAtMs > System.currentTimeMillis() + 3_630_000 ||
        !PeerCrypto.verifySignature(expectedPeerPublicKey, parsed.message(), parsed.responseSignature) ||
        !parsed.tokenHex.matches(Regex("[a-f0-9]{64}"))) {
        return MeshlitResult.Failure(MeshlitError.Auth("handshake.identity_or_scope_mismatch"))
    }
    val policy = DeviceTrustPolicy(
        nodeId = parsed.serverNodeId,
        trustTier = accepted,
        allowedRoles = setOf(ClusterRoleTag.BRAIN),
        tokenExpiryMs = parsed.expiresAtMs,
        publicKeyFingerprint = PeerCrypto.fingerprint(parsed.serverPublicKey),
    )
    return when (val saved = trustStore.upsert(policy)) {
        is MeshlitResult.Success -> {
            onSession?.invoke(parsed)
            MeshlitResult.Success(policy)
        }
        is MeshlitResult.Failure -> MeshlitResult.Failure(saved.error)
    }
}

/**
 * String tags matching [com.meshlit.core.common.ClusterRole.tag]. We
 * keep the constants local so `core-trust` doesn't have to depend on
 * `core-common` for the enum (it does depend on core-common for
 * `NodeId` / `MeshlitResult`, so we could import ClusterRole directly;
 * the indirection makes tests cleaner and keeps the wire format
 * stable if the enum ever evolves).
 */
object ClusterRoleTag {
    const val BRAIN = "brain"
    const val TOOL = "tool"
    const val MONITOR = "monitor"
}
