package com.meshlit.disco

import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.trust.TrustTier

/**
 * Bucket a discovered peer into one of four categories the user
 * sees on the v2 Scan screen.
 *
 *  - [LOCAL]: same /24 subnet (or link-local) → fastest path,
 *    implicitly trusted for cluster traffic.
 *  - [CLUSTER]: known cluster — fingerprint matches a previously
 *    paired peer or the local `clusterFingerprints` set.
 *  - [GROUP]: same SSID or VPN — reachable but untrusted
 *    (firewall-default = sandbox).
 *  - [INTERNET]: public IP (TrustTier.WAN) — needs explicit
 *    per-port rules.
 *
 * The classifier runs synchronously on each `DiscoveryCoordinator`
 * emission; no caching. With ~50 peers it's still sub-ms.
 */
enum class PeerCategory(
    val displayName: String,
    val tint: androidx.compose.ui.graphics.Color,
    val subtitle: String,
) {
    LOCAL("Local subnet", androidx.compose.ui.graphics.Color(0xFF4DD9C0), "Same /24 — direct, fast, trusted"),
    CLUSTER("Cluster", androidx.compose.ui.graphics.Color(0xFF7C6FF2), "Paired fingerprint — cluster trusted"),
    GROUP("Group / VPN", androidx.compose.ui.graphics.Color(0xFFE8C56F), "Same SSID or VPN — sandboxed by default"),
    INTERNET("Internet", androidx.compose.ui.graphics.Color(0xFFE8735F), "Public — per-port allowlist"),
}

/**
 * One peer + its classification bucket. The screen renders
 * `ClassifiedPeer`s grouped by `category`, ordered LOCAL →
 * CLUSTER → GROUP → INTERNET for a stable left-to-right visual
 * progression.
 */
data class ClassifiedPeer(
    val advertisement: PeerAdvertisement,
    val category: PeerCategory,
    val distance: PeerDistance,
)

/**
 * Cheap heuristic for the row's "distance" label. We don't have
 * real RSSI from mDNS (NSD doesn't report it), but for the
 * typical Android home cluster we can infer:
 *  - "Same AP" → host is a private RFC1918 and matches the
 *    device's own /24 prefix.
 *  - "Same subnet" → private RFC1918, different /24 from us.
 *  - "Cross-NAT" → private RFC1918 but unreachable per the
 *    routing table.
 *  - "Internet" → public IP.
 *
 * This is intentionally fuzzy; the goal is a row subtitle the
 * user can read at a glance, not a traceroute-grade measurement.
 */
enum class PeerDistance(val label: String) {
    SAME_AP("Same AP"),
    SAME_SUBNET("Same subnet"),
    CROSS_NAT("Cross-NAT"),
    INTERNET("Internet"),
    UNKNOWN("Reach unknown"),
}

/**
 * Decide which bucket a peer falls into.
 *
 * Inputs:
 *  - [adv]: the discovered `PeerAdvertisement`.
 *  - [localIPv4Prefixes]: the host's own IPv4 prefixes, each in
 *    "192.168.1" form (truncated at /24). Empty disables LOCAL
 *    matching → all peers fall through to GROUP.
 *  - [clusterFingerprints]: trust fingerprints of previously
 *    paired cluster peers. If [adv.fingerprint] matches, the
 *    peer is CLUSTER.
 *
 * Classification logic in priority order:
 *  1. [TrustTier.WAN] → INTERNET (overrides CIDR checks).
 *  2. Fingerprint in clusterFingerprints → CLUSTER.
 *  3. Host shares a /24 with us → LOCAL.
 *  4. Host is a private RFC1918 (10/8, 172.16/12, 192.168/16) →
 *     GROUP.
 *  5. Anything else (link-local, IPv6 ULA, public but trust=LOCAL)
 *     → UNKNOWN → GROUP (safest sandbox default).
 *
 * `TrustTier` overrides win even when the IP looks local — a peer
 * advertising `tier=wan` is treated as internet until proven
 * otherwise.
 */
fun classify(
    adv: PeerAdvertisement,
    localIPv4Prefixes: List<String>,
    clusterFingerprints: Set<String>,
): ClassifiedPeer {
    val tier = adv.trustTierOrDefault()
    if (tier == TrustTier.WAN) {
        return ClassifiedPeer(adv, PeerCategory.INTERNET, PeerDistance.INTERNET)
    }
    if (adv.fingerprint in clusterFingerprints) {
        return ClassifiedPeer(adv, PeerCategory.CLUSTER, PeerDistance.SAME_AP)
    }
    val prefix = ipv4Prefix(adv.host)
    if (prefix != null && prefix in localIPv4Prefixes) {
        return ClassifiedPeer(adv, PeerCategory.LOCAL, PeerDistance.SAME_AP)
    }
    if (isPrivateRFC1918(adv.host)) {
        return ClassifiedPeer(adv, PeerCategory.GROUP, PeerDistance.SAME_SUBNET)
    }
    if (isLinkLocal(adv.host)) {
        return ClassifiedPeer(adv, PeerCategory.GROUP, PeerDistance.CROSS_NAT)
    }
    return ClassifiedPeer(adv, PeerCategory.GROUP, PeerDistance.UNKNOWN)
}

/**
 * Extract the /24 prefix of an IPv4 address as a `String`
 * ("192.168.1"). Returns `null` for IPv6, hostnames, or
 * malformed inputs.
 */
fun ipv4Prefix(host: String): String? {
    if (host.isEmpty() || host.contains(':')) return null
    val parts = host.split('.')
    if (parts.size != 4) return null
    val first = parts[0].toIntOrNull() ?: return null
    if (first !in 0..255) return null
    if (parts[1].toIntOrNull() !in 0..255) return null
    if (parts[2].toIntOrNull() !in 0..255) return null
    if (parts[3].toIntOrNull() !in 0..255) return null
    return "${parts[0]}.${parts[1]}.${parts[2]}"
}

/**
 * True for RFC1918 private ranges: 10.0.0.0/8, 172.16.0.0/12,
 * 192.168.0.0/16.
 */
fun isPrivateRFC1918(host: String): Boolean {
    if (host.isEmpty() || host.contains(':')) return false
    val parts = host.split('.')
    if (parts.size != 4) return false
    val a = parts[0].toIntOrNull() ?: return false
    val b = parts[1].toIntOrNull() ?: return false
    if (b !in 0..255) return false
    if (parts[2].toIntOrNull() !in 0..255) return false
    if (parts[3].toIntOrNull() !in 0..255) return false
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        else -> false
    }
}

/**
 * True for IPv4 link-local (169.254.0.0/16) — typical for
 * BLE-tethered peers that haven't been routed.
 */
fun isLinkLocal(host: String): Boolean {
    if (host.isEmpty() || host.contains(':')) return false
    val parts = host.split('.')
    if (parts.size != 4) return false
    val a = parts[0].toIntOrNull() ?: return false
    val b = parts[1].toIntOrNull() ?: return false
    if (parts[2].toIntOrNull() !in 0..255) return false
    if (parts[3].toIntOrNull() !in 0..255) return false
    return a == 169 && b == 254
}
