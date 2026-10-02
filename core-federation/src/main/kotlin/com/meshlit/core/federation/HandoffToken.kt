package com.meshlit.core.federation

import kotlinx.serialization.Serializable

/**
 * Body of `POST /v1/agent.handoff`. The client ships a signed,
 * versioned blob that the receiving peer uses to reconstruct an
 * `AgentSession` (defined in `:core-agent-memory`).
 *
 * Wire fields:
 *  - [tokenId]          — unique id assigned by the originator. The
 *                          receiving peer records it so a replay attack
 *                          can be detected.
 *  - [sessionId]        — the agent session id being handed off.
 *  - [originatorNodeId] — the peer's stable node id.
 *  - [originatorKeyFingerprint] — SHA-256 of the originator's public
 *                          key. Used by the receiver to look up the
 *                          matching entry in its trust store.
 *  - [originatorProtocolVersion] — the version under which the session
 *                          was running. The receiver bumps it up to
 *                          its own version if higher.
 *  - [targetNodeId]     — the peer the originator is handing off to.
 *  - [issuedAtMillis]   — wall-clock time the token was signed.
 *  - [expiresAtMillis]  — token expiry. The receiver refuses tokens
 *                          whose expiry is in the past.
 *  - [requiresUserConsent] — if `true`, the receiver MUST prompt the
 *                          user for explicit consent before accepting.
 *  - [payload]          — base64-encoded, Ed25519-signed JSON snapshot
 *                          of the durable session state (per Phase 2
 *                          plan §2.7). The signature covers
 *                          `[tokenId, sessionId, originatorNodeId,
 *                          targetNodeId, issuedAtMillis, payload]`.
 *  - [signature]        — base64-encoded Ed25519 signature.
 *
 * Acceptance invariants (enforced by the server, asserted by tests):
 *
 *  1. [signature] verifies against the public key whose fingerprint
 *     matches [originatorKeyFingerprint].
 *  2. Major version of [originatorProtocolVersion] matches the
 *     receiver's major version (ADR-008).
 *  3. `now < expiresAtMillis`.
 *  4. [tokenId] has not been seen by this receiver before (replay
 *     protection — receiver maintains a bounded LRU).
 *  5. If [requiresUserConsent] is true, the receiver MUST collect an
 *     `Allow` verdict before constructing the `AgentSession`.
 */
@Serializable
data class HandoffToken(
    val tokenId: String,
    val sessionId: String,
    val originatorNodeId: String,
    val originatorKeyFingerprint: String,
    val originatorProtocolVersion: Int,
    val targetNodeId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val requiresUserConsent: Boolean = true,
    val payload: String,
    val signature: String,
)

/**
 * Body returned by `POST /v1/agent.handoff` on success.
 *
 *  - [accepted] — true if the handoff succeeded.
 *  - [rehydratedSessionId] — the new session id on the receiving
 *    peer. The original session on the originator MUST be marked
 *    `HandedOff` once it sees this id.
 *  - [newProtocolVersion] — the protocol version the rehydrated
 *    session is now running under.
 */
@Serializable
data class HandoffResponse(
    val accepted: Boolean = true,
    val rehydratedSessionId: String,
    val newProtocolVersion: Int,
)