package com.meshlit.core.federation

import kotlinx.serialization.Serializable

/**
 * Body of `POST /v1/peer.hello` — the first message a client sends to
 * a remote peer after the TLS handshake completes.
 *
 * Wire fields:
 *  - [peerId]            — the sender's stable [com.meshlit.core.common.NodeId].
 *                          The server looks it up in its trust store.
 *  - [protocolVersion]   — the sender's [FederationProtocol.PROTOCOL_VERSION].
 *                          The server compares major versions and either
 *                          accepts the call or returns
 *                          [FederationError.versionMismatch].
 *  - [publicKey]         — base64-encoded long-term public key. The
 *                          server records it for the trust store and
 *                          uses it to verify signatures on
 *                          [HandoffToken]s. **Never logged.**
 *  - [keyFingerprint]    — the SHA-256 hex digest of [publicKey]. Sent
 *                          in plaintext in the `X-Meshlit-Key-Fingerprint`
 *                          header and echoed here for ease of logging.
 *  - [capability]        — the sender's static [com.meshlit.core.common.CapabilityMatrix].
 *                          The server can use this to decide which
 *                          endpoints are reachable (e.g. only certain
 *                          peers can answer `model.shardRanges`).
 *  - [supports]          — the wire-level features this client knows
 *                          how to speak. Lets the server feature-gate
 *                          endpoints without bumping the protocol
 *                          version for every additive capability.
 *  - [clientVersion]     — Meshlit app version string. Diagnostic only.
 */
@Serializable
data class PeerHelloRequest(
    val peerId: String,
    val protocolVersion: Int,
    val publicKey: String,
    val keyFingerprint: String,
    val capability: CapabilityMatrixDto,
    val supports: Set<String> = emptySet(),
    val clientVersion: String = "",
)

/**
 * Body returned by `POST /v1/peer.hello` on success.
 *
 *  - [accepted]              — always `true` on `200 OK`.
 *  - [peerId]                — the **server's** node id.
 *  - [negotiatedProtocolVersion] — the version the server will use for
 *                                 subsequent calls. Equal to `min(client, server)`
 *                                 on the same major version.
 *  - [publicKey]             — base64-encoded server long-term public key.
 *  - [keyFingerprint]        — its SHA-256 hex digest.
 *  - [capability]            — server's static capability matrix.
 *  - [trustGranted]          — `true` if the server now considers the
 *                              caller trusted enough to receive
 *                              `/v1/inference.dispatch` calls.
 *  - [sessionToken]          — opaque token the client must echo in
 *                              the `X-Meshlit-Session-Token` header on
 *                              every subsequent call. Lets the server
 *                              revalidate the trust store cheaply.
 */
@Serializable
data class PeerHelloResponse(
    val accepted: Boolean = true,
    val peerId: String,
    val negotiatedProtocolVersion: Int,
    val publicKey: String,
    val keyFingerprint: String,
    val capability: CapabilityMatrixDto,
    val trustGranted: Boolean,
    val sessionToken: String,
)

/**
 * Optional header carrying the session token returned by [PeerHelloResponse].
 * Endpoints that need a session may refuse requests without it.
 */
const val HEADER_SESSION_TOKEN: String = "X-Meshlit-Session-Token"