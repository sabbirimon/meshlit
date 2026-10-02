package com.meshlit.core.federation

import kotlinx.serialization.Serializable

/**
 * Typed error returned by every federation endpoint when a request
 * fails. The shape is identical across endpoints so a client can parse
 * any error with one decoder.
 *
 * Mapping to HTTP status codes (enforced by the server):
 *
 *  - [VersionMismatch]       → `426 Upgrade Required`
 *  - [UnknownPeer]           → `403 Forbidden`
 *  - [TrustDenied]           → `403 Forbidden`
 *  - [UnsupportedFeature]    → `501 Not Implemented`
 *  - [ModelUnavailable]      → `404 Not Found`
 *  - [DispatchBusy]          → `503 Service Unavailable`
 *  - [DispatchCancelled]     → `499 Client Closed Request`
 *  - [InvalidRequest]        → `400 Bad Request`
 *  - [InternalError]         → `500 Internal Server Error`
 *
 * Clients MUST treat unknown `kind` values as a fatal
 * [InternalError] and surface them in the Activity timeline.
 *
 * Wire format: `{ "kind": "<kind_tag>", "message": "<human readable>",
 * "details": <optional object> }`. The `details` field is deliberately
 * open (`Map<String, String>`) so endpoints can attach machine-readable
 * context (e.g. the negotiated version, the requested model id)
 * without forcing a schema change on every new error.
 */
@Serializable
data class FederationError(
    val kind: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
) {
    /** Parses a JSON string into a [FederationError]. Falls back to
     *  [InternalError] on parse failure (so a malformed body still
     *  surfaces a typed result). */
    companion object {
        /** Major version mismatch — handshake refused. */
        fun versionMismatch(localMajor: Int, remoteMajor: Int, remote: Int): FederationError =
            FederationError(
                kind = KIND_VERSION_MISMATCH,
                message = "Incompatible protocol version: " +
                    "local=${FederationProtocol.formatVersion(localMajor * 10_000)} " +
                    "remote=${FederationProtocol.formatVersion(remote)}",
                details = mapOf(
                    "local_major" to localMajor.toString(),
                    "remote_major" to remoteMajor.toString(),
                    "remote" to remote.toString(),
                ),
            )

        fun unknownPeer(nodeId: String): FederationError =
            FederationError(
                kind = KIND_UNKNOWN_PEER,
                message = "Unknown peer: $nodeId",
                details = mapOf("node_id" to nodeId),
            )

        fun trustDenied(nodeId: String, reason: String): FederationError =
            FederationError(
                kind = KIND_TRUST_DENIED,
                message = "Trust denied for peer $nodeId: $reason",
                details = mapOf("node_id" to nodeId, "reason" to reason),
            )

        fun unsupportedFeature(feature: String): FederationError =
            FederationError(
                kind = KIND_UNSUPPORTED_FEATURE,
                message = "Feature not supported by this peer: $feature",
                details = mapOf("feature" to feature),
            )

        fun modelUnavailable(modelId: String, reason: String): FederationError =
            FederationError(
                kind = KIND_MODEL_UNAVAILABLE,
                message = "Model unavailable on this peer: $modelId ($reason)",
                details = mapOf("model_id" to modelId, "reason" to reason),
            )

        fun dispatchBusy(nodeId: String): FederationError =
            FederationError(
                kind = KIND_DISPATCH_BUSY,
                message = "Peer $nodeId is at capacity; retry later",
                details = mapOf("node_id" to nodeId),
            )

        fun dispatchCancelled(requestId: String): FederationError =
            FederationError(
                kind = KIND_DISPATCH_CANCELLED,
                message = "Dispatch cancelled by caller",
                details = mapOf("request_id" to requestId),
            )

        fun invalidRequest(reason: String): FederationError =
            FederationError(
                kind = KIND_INVALID_REQUEST,
                message = "Invalid request: $reason",
            )

        fun internalError(reason: String): FederationError =
            FederationError(
                kind = KIND_INTERNAL_ERROR,
                message = "Internal error: $reason",
            )

        /** Sentinel constants so consumers can match on `kind` without
         *  mistyping a string. Mirrored in tests. */
        const val KIND_VERSION_MISMATCH = "version_mismatch"
        const val KIND_UNKNOWN_PEER = "unknown_peer"
        const val KIND_TRUST_DENIED = "trust_denied"
        const val KIND_UNSUPPORTED_FEATURE = "unsupported_feature"
        const val KIND_MODEL_UNAVAILABLE = "model_unavailable"
        const val KIND_DISPATCH_BUSY = "dispatch_busy"
        const val KIND_DISPATCH_CANCELLED = "dispatch_cancelled"
        const val KIND_INVALID_REQUEST = "invalid_request"
        const val KIND_INTERNAL_ERROR = "internal_error"
    }
}
