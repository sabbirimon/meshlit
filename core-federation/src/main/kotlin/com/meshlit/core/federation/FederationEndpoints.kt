package com.meshlit.core.federation

/**
 * Canonical endpoints exposed by every Meshlit peer.
 *
 * The full URL is computed as `${baseUrl}${path}` where `baseUrl` is
 * `https://<host>:<port>` derived from [com.meshlit.core.discovery.PeerAdvertisement].
 *
 * Per ADR-008, all endpoints live under [FederationProtocol.PATH_PREFIX]
 * and speak [FederationProtocol.CONTENT_TYPE] over TLS 1.3. Every
 * endpoint requires a valid `X-Meshlit-Protocol-Version` header.
 */
object FederationEndpoints {
    /** `POST /v1/peer.hello` — handshake. Establishes protocol
     *  version, exchanges public keys, and (in v1) the static
     *  capability matrix. The server responds with `200 OK` and a
     *  [PeerHelloResponse] if the handshake succeeds; with `426
     *  Upgrade Required` + a [FederationError.VersionMismatch] body
     *  if the major versions disagree; with `403 Forbidden` if the
     *  caller's key fingerprint is not in the trust store. */
    const val PEER_HELLO: String = "/v1/peer.hello"

    /** `POST /v1/model.shardRanges` — returns GGUF shard layout for
     *  a model. Used by Slice 4 (deferred to Phase 7 per plan §4.13).
     *  The server responds with `404 Not Found` if the model id is
     *  unknown to this peer. */
    const val MODEL_SHARD_RANGES: String = "/v1/model.shardRanges"

    /** `POST /v1/inference.dispatch` — delegate an [InferenceDispatchRequest]
     *  to a peer. The response is a chunked transfer of [TokenChunk]
     *  JSON objects, each on its own line. The connection stays open
     *  until the server emits a final chunk with
     *  [TokenChunk.finishReason] set. */
    const val INFERENCE_DISPATCH: String = "/v1/inference.dispatch"

    /** `POST /v1/agent.handoff` — accept a signed [HandoffToken] from
     *  another peer. Used by the durable agent kernel (Phase 2) when
     *  it hands off a session. The server validates the signature,
     *  the version, the expiry, and the user consent flag. */
    const val AGENT_HANDOFF: String = "/v1/agent.handoff"

    /** `POST /v1/health.capability` — exchange a [CapabilityExchange]
     *  between two peers. Lightweight probe that does not require a
     *  full handshake. Used by the agent kernel to decide whether
     *  to route a `Task` to a particular peer. */
    const val HEALTH_CAPABILITY: String = "/v1/health.capability"
}