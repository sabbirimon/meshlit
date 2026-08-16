package com.meshlit.core.trust

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pairing / trust handshake wire types. Phase 3 ships the *shape* of the
 * handshake; full cryptographic signing lands in a later wave (BouncyCastle
 * or Tink dependency decision is non-trivial). Today the handshake is
 * plaintext over the same HTTPS surface as `/v1/capabilities` and
 * exchanges only:
 *
 *  - the requester's claimed [TrustTier]
 *  - a server-signed acknowledgement
 *  - a token (random hex) the requester can attach to subsequent calls
 *
 * The `publicKey` field is a placeholder — callers put an empty string
 * today; when the signature scheme lands, callers will populate it with
 * a base64 SPKI public key.
 */
@Serializable
data class HandshakeRequest(
    val nodeId: String,
    val publicKey: String = "",
    val requestedTier: String,
    val nonce: String,
    val signatureHex: String = "",
)

@Serializable
data class HandshakeResponse(
    val acceptedTier: String,
    val tokenHex: String,
    val expiresAtMs: Long,
    val serverPublicKey: String = "",
    val serverNodeId: String,
)

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

/**
 * Run a handshake against [peerBaseUrl]. On success the returned
 * policy is also [TrustStore.upsert]ed so subsequent reads see it.
 *
 * Failure modes:
 *  - transport failure → [MeshlitError.Network]
 *  - non-200 / malformed body → [MeshlitError.Auth]
 *  - 200 but the server returned an UNKNOWN tier → [MeshlitError.Auth]
 *
 * The actual signature verification is a no-op in this wave — see the
 * file comment. The handshake still gets us a token + tier even
 * without crypto, which is enough for Wave 3A's gating.
 */
suspend fun runHandshake(
    transport: HandshakeTransport,
    trustStore: TrustStore,
    selfNodeId: NodeId,
    peerBaseUrl: String,
    requestedTier: TrustTier,
    nonce: String,
): MeshlitResult<DeviceTrustPolicy> {
    val req = HandshakeRequest(
        nodeId = selfNodeId.value,
        publicKey = "",
        requestedTier = requestedTier.tag,
        nonce = nonce,
        signatureHex = "",
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
    val policy = DeviceTrustPolicy(
        nodeId = parsed.serverNodeId,
        trustTier = accepted,
        allowedRoles = setOf(ClusterRoleTag.BRAIN, ClusterRoleTag.TOOL, ClusterRoleTag.MONITOR),
        tokenExpiryMs = parsed.expiresAtMs,
        publicKeyFingerprint = parsed.serverPublicKey.takeIf { it.isNotEmpty() },
    )
    trustStore.upsert(policy)
    return MeshlitResult.Success(policy)
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
