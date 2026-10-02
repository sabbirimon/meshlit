package com.meshlit.disco

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Walks the device's network interfaces and returns the /24 IPv4
 * prefixes of every non-loopback, up interface. Cached on the
 * repo's call site so the classifier sees a stable list during
 * a single classification pass.
 *
 * - Filters out `127.0.0.0/8` (loopback) and `169.254.0.0/16`
 *   (link-local — they're transient and not real cluster peers).
 * - Returns `["192.168.1"]` for a single Wi-Fi interface, so
 *   other phones on the same AP land in the LOCAL bucket.
 *
 * Throws nothing — a hostile SELinux context or emulator with
 * zero addresses returns `emptyList()`, which the classifier
 * handles by falling through to GROUP for everything (safe
 * sandbox default).
 */
fun collectLocalIPv4Prefixes(): List<String> {
    return try {
        val prefixes = mutableListOf<String>()
        Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { iface ->
            if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) return@forEach
            Collections.list(iface.inetAddresses).forEach { addr ->
                if (addr !is Inet4Address) return@forEach
                val raw = addr.hostAddress ?: return@forEach
                val prefix = ipv4Prefix(raw) ?: return@forEach
                // Filter loopback + link-local explicitly here
                // even though ipv4Prefix() would accept them —
                // the caller wants "my real subnet".
                if (raw.startsWith("127.") || raw.startsWith("169.254.")) return@forEach
                if (prefix.isNotEmpty() && prefix !in prefixes) prefixes.add(prefix)
            }
        }
        prefixes
    } catch (t: Throwable) {
        // No permissions to enumerate interfaces, or the
        // device is offline — return empty so callers fall
        // back to the GROUP bucket.
        emptyList()
    }
}
