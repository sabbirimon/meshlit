package com.meshlit.core.tunnel

import kotlinx.serialization.Serializable

/**
 * Generic tunnel specification. Phase 3 only persists the configuration
 * — actual tunnel clients (WireGuard / Tailscale / SSH-bridged) land
 * in a later wave. The [mode] discriminator lets future implementations
 * pick their transport without changing the persistence shape.
 *
 * `tcp` is the default and matches today's "local-port forwards to a
 * remote-port via an upstream tunnel" use case. `udp` covers
 * WireGuard-style packet forwarding. `mesh` is reserved for the
 * eventually-consistent cluster mode (Phase 4+).
 */
@Serializable
data class TunnelSpec(
    val id: String,
    val label: String,
    val mode: TunnelMode = TunnelMode.TCP,
    val localHost: String = "127.0.0.1",
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
    val enabled: Boolean = true,
) {
    init {
        require(localPort in 1..65535) { "local port out of range: $localPort" }
        require(remotePort in 1..65535) { "remote port out of range: $remotePort" }
        require(localHost.isNotBlank()) { "localHost is required" }
        require(remoteHost.isNotBlank()) { "remoteHost is required" }
    }
}

@Serializable
enum class TunnelMode { TCP, UDP, MESH }

/**
 * In-memory registry. Persistence (DataStore) lands in Wave 3B.5
 * alongside the Settings UI. The interface exists now so the future
 * persistence layer can be swapped in without touching call sites.
 */
interface TunnelRegistry {
    fun list(): List<TunnelSpec>
    fun upsert(spec: TunnelSpec)
    fun remove(id: String)
}

class InMemoryTunnelRegistry : TunnelRegistry {
    private val map = LinkedHashMap<String, TunnelSpec>()
    override fun list(): List<TunnelSpec> = map.values.toList()
    override fun upsert(spec: TunnelSpec) { map[spec.id] = spec }
    override fun remove(id: String) { map.remove(id) }
}
