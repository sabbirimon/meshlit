package com.meshlit.core.ssh

import kotlinx.serialization.Serializable

/**
 * SSH tunnel configuration. Phase 3 only persists the configuration —
 * actual SSH client implementation lands in a later wave (the SSH
 * library choice — JSch / sshj / custom — is a non-trivial dep
 * decision that doesn't affect Phase 3 surfaces).
 *
 * Persisted via JSON in DataStore when the user saves a remote
 * gateway they want Meshlit to forward through. Validation happens
 * at construction time so a malformed config never reaches the
 * (future) SSH client.
 */
@Serializable
data class SshTunnelConfig(
    val id: String,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: SshAuth,
    val remoteForwardHost: String,
    val remoteForwardPort: Int,
    val keepAliveSeconds: Int = 30,
) {
    init {
        require(port in 1..65535) { "ssh port out of range: $port" }
        require(remoteForwardPort in 1..65535) { "forward port out of range: $remoteForwardPort" }
        require(host.isNotBlank()) { "host is required" }
        require(username.isNotBlank()) { "username is required" }
    }

    /** Pair (host, port, username) uniquely identifies a logical gateway. */
    fun fingerprint(): String = "$username@$host:$port"
}

@Serializable
sealed class SshAuth {
    @Serializable
    data class PrivateKeyPath(val path: String) : SshAuth()

    @Serializable
    data class PrivateKeyInline(val pem: String, val passphrase: String? = null) : SshAuth()

    @Serializable
    data class Password(val password: String) : SshAuth()
}
