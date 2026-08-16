package com.meshlit

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Cheap, lazily-computed network & identity metadata for the
 * local device. Phase 0.3 lifts these out of `MeshlitApplication`
 * so the application class can stay focused on the Koin container
 * + `onCreate` boot flow.
 */
class DeviceInfo {

    val displayName: String by lazy {
        val model = runCatching { Build.MODEL ?: "Meshlit" }.getOrDefault("Meshlit")
        "Meshlit/$model"
    }

    /**
     * Best-effort local IPv4 address. Returns empty string when
     * the device is offline or only has IPv6 — the QR sheet then
     * shows a placeholder and the user can paste the address
     * manually.
     */
    val localIpAddress: String by lazy { resolveLocalIpv4() }

    private fun resolveLocalIpv4(): String = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
            val addrs = iface.inetAddresses?.toList().orEmpty()
            for (addr in addrs) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: continue
                }
            }
        }
        ""
    }.getOrDefault("")
}
