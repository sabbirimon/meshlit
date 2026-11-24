package com.meshlit.workspace

/** OS observation is deliberately separate from the installed VPN companion's identity. */
data class VpnNetworkObservation(
    val observedAt: Long, val hasNetwork: Boolean?, val vpnForApp: Boolean?,
    val validated: Boolean?, val metered: Boolean?, val interfaceName: String?,
    val addresses: List<String>, val tailscaleInstalled: Boolean?,
) {
    val summary: String get() = when {
        hasNetwork == null -> "Network status unavailable"
        !hasNetwork -> "No active network for Meshlit"
        vpnForApp == true -> "VPN active for Meshlit"
        vpnForApp == false -> "No VPN on Meshlit's active network"
        else -> "VPN transport unknown"
    }
    // Installed app, 100.x addresses and VPN transport never prove the provider's identity.
    val provider: String get() = "VPN provider identity unavailable from Android"
}

fun vpnSnapshotIsFresh(observedAt: Long, now: Long): Boolean = now - observedAt in 0..60_000
