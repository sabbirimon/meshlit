package com.meshlit.core.federation

/**
 * Wire-level constants for the Meshlit federation protocol.
 *
 * ## Versioning (ADR-008)
 *
 * Every handshake frame carries [PROTOCOL_VERSION]. The semantic is:
 *
 *  - **Major** version mismatch (computed by `majorVersion()` from the
 *    advertised version) ⇒ **refuse** the handshake. The peer rejects
 *    the connection with [FederationError.VersionMismatch].
 *  - **Minor** version mismatch ⇒ **accept**, but log a warning and
 *    proceed with the smaller of the two version numbers (the
 *    intersection of supported features). This lets us ship additive
 *    fields on a `1.x` line without breaking an installed peer.
 *
 * Bumping the major version is reserved for breaking wire changes
 * (renaming a request field, changing an HTTP method, removing a
 * `/v1/...` endpoint). Anything additive stays within `1.x`.
 *
 * The current version is intentionally `1.0` — the first release that
 * implements the `/v1/...` surface in plan §6.1.
 */
object FederationProtocol {
    /** Wire-level protocol version advertised in every handshake. */
    const val PROTOCOL_VERSION: Int = 1_00_00 // 1.0.0 packed: major=1, minor=0, patch=0

    /** Wire-level content type. JSON only; protobuf / msgpack are not
     *  supported in v1. */
    const val CONTENT_TYPE: String = "application/json"

    /** TLS configuration. Meshlit refuses TLS < 1.3. The wire is
     *  authenticated with mTLS (both peers present certs); the trust
     *  store is the Android Keystore-backed one from `:core-trust`,
     *  never a PEM file shipped with the APK. */
    const val MIN_TLS_VERSION: String = "TLS_1_3"

    /** Header carrying the protocol version on every request. The
     *  server (downstream parser) checks this header before any other
     *  work. */
    const val HEADER_PROTOCOL_VERSION: String = "X-Meshlit-Protocol-Version"

    /** Header carrying the requester's stable node id. The server
     *  records it for trust policy + audit. */
    const val HEADER_NODE_ID: String = "X-Meshlit-Node-Id"

    /** Header carrying the SHA-256 fingerprint of the requester's
     *  long-term public key. Used by `:core-trust` to gate tool /
     *  handoff calls. Never the key itself. */
    const val HEADER_KEY_FINGERPRINT: String = "X-Meshlit-Key-Fingerprint"

    /** Path prefix for federation traffic. The full path lives in
     *  [FederationEndpoints]. */
    const val PATH_PREFIX: String = "/v1"

    /** Returns the major version embedded in a packed `MAJOR*10000
     *  + MINOR*100 + PATCH` integer. */
    fun majorVersion(packed: Int): Int = packed / 10_000

    /** Returns the minor version. */
    fun minorVersion(packed: Int): Int = (packed % 10_000) / 100

    /** Returns the patch version. */
    fun patchVersion(packed: Int): Int = packed % 100

    /** Pretty rendering for logs and the Devices screen. */
    fun formatVersion(packed: Int): String =
        "${majorVersion(packed)}.${minorVersion(packed)}.${patchVersion(packed)}"

    /**
     * Negotiate a common version given the local + remote packed
     * versions. Returns `null` if no compatible version exists
     * (major-version mismatch); otherwise returns the smaller of the
     * two integers, which the server treats as the negotiated floor.
     */
    fun negotiate(local: Int, remote: Int): Int? {
        if (majorVersion(local) != majorVersion(remote)) return null
        return minOf(local, remote)
    }
}