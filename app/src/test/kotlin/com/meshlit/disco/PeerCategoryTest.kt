package com.meshlit.disco

import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the v2 peer classifier — pure JVM, no
 * Android dependency.
 *
 * Goals:
 *  - TrustTier.WAN → INTERNET (overrides CIDR checks).
 *  - Cluster fingerprint match → CLUSTER.
 *  - Host shares a /24 with us → LOCAL.
 *  - Private RFC1918 → GROUP.
 *  - Link-local → GROUP with CROSS_NAT distance.
 *  - Hostname (not an IP) → GROUP / UNKNOWN.
 *
 * Plus the IP-parsing helpers (`ipv4Prefix`,
 * `isPrivateRFC1918`, `isLinkLocal`) — these get the same
 * coverage as `classify()` because they're the source of any
 * "wrong bucket" bug.
 */
class PeerCategoryTest {

    private fun peer(host: String, tier: String = "trusted", fingerprint: String = "abc123") =
        PeerAdvertisement(
            nodeId = "node-${host.hashCode()}",
            host = host,
            port = 8080,
            tier = tier,
            fingerprint = fingerprint,
        )

    @Test
    fun localSubnet_matchesSamePrefix() {
        val result = classify(
            adv = peer("192.168.1.45"),
            localIPv4Prefixes = listOf("192.168.1"),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.LOCAL, result.category)
        assertEquals(PeerDistance.SAME_AP, result.distance)
    }

    @Test
    fun clusterFingerprint_winsOverSubnet() {
        // Even though 192.168.1.45 is "local" to us, a
        // matching cluster fingerprint makes it CLUSTER.
        val result = classify(
            adv = peer("192.168.1.45", fingerprint = "trusted-cluster-fp"),
            localIPv4Prefixes = listOf("192.168.1"),
            clusterFingerprints = setOf("trusted-cluster-fp"),
        )
        assertEquals(PeerCategory.CLUSTER, result.category)
        assertEquals(PeerDistance.SAME_AP, result.distance)
    }

    @Test
    fun trustTierWan_overridesLocality() {
        // Host looks like a private RFC1918, but tier=wan
        // means the peer is publicly reachable — must be
        // INTERNET to force per-port allowlist.
        val result = classify(
            adv = peer("10.0.0.5", tier = "wan"),
            localIPv4Prefixes = listOf("10.0.0"),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.INTERNET, result.category)
        assertEquals(PeerDistance.INTERNET, result.distance)
    }

    @Test
    fun privateRfc1918_differentSubnet_isGroup() {
        // 10.20.30.40 is private (RFC1918), but it's not on
        // our /24 — treat as GROUP (untrusted, sandbox default).
        val result = classify(
            adv = peer("10.20.30.40"),
            localIPv4Prefixes = listOf("192.168.1"),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.GROUP, result.category)
        assertEquals(PeerDistance.SAME_SUBNET, result.distance)
    }

    @Test
    fun linkLocal_isCrossNat() {
        // BLE-tethered peers that haven't been routed.
        val result = classify(
            adv = peer("169.254.1.5"),
            localIPv4Prefixes = emptyList(),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.GROUP, result.category)
        assertEquals(PeerDistance.CROSS_NAT, result.distance)
    }

    @Test
    fun hostnameNotIp_isUnknown() {
        // mDNS sometimes returns bare hostnames like
        // "mesh-b.local." — the IP parser returns null so the
        // classifier falls through to GROUP / UNKNOWN.
        val result = classify(
            adv = peer("mesh-b.local"),
            localIPv4Prefixes = listOf("192.168.1"),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.GROUP, result.category)
        assertEquals(PeerDistance.UNKNOWN, result.distance)
    }

    @Test
    fun ipv6Host_isUnknown() {
        val result = classify(
            adv = peer("fe80::1ff:fe23:4567:890a"),
            localIPv4Prefixes = emptyList(),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.GROUP, result.category)
        assertEquals(PeerDistance.UNKNOWN, result.distance)
    }

    @Test
    fun emptyLocalPrefixes_disablesLocalMatching() {
        // On a device with no captured /24 (offline, or a
        // rooted emulator with restricted inet enumeration),
        // EVERY private peer falls through to GROUP — safe
        // sandbox default; the user can promote to CLUSTER
        // manually via the QR pairing flow.
        val result = classify(
            adv = peer("192.168.1.45"),
            localIPv4Prefixes = emptyList(),
            clusterFingerprints = emptySet(),
        )
        assertEquals(PeerCategory.GROUP, result.category)
    }

    @Test
    fun ipv4Prefix_extractsFirstThreeOctets() {
        assertEquals("192.168.1", ipv4Prefix("192.168.1.45"))
        assertEquals("10.0.0", ipv4Prefix("10.0.0.1"))
        assertEquals(null, ipv4Prefix("mesh-b.local"))
        assertEquals(null, ipv4Prefix("fe80::1"))
        assertEquals(null, ipv4Prefix(""))
        assertEquals(null, ipv4Prefix("999.999.999.999"))
        assertEquals(null, ipv4Prefix("192.168.1"))
    }

    @Test
    fun rfc1918_matchesAllThreeRanges() {
        assertEquals(true, isPrivateRFC1918("10.0.0.1"))
        assertEquals(true, isPrivateRFC1918("172.16.0.1"))
        assertEquals(true, isPrivateRFC1918("172.31.255.255"))
        assertEquals(true, isPrivateRFC1918("192.168.1.1"))
        assertEquals(false, isPrivateRFC1918("172.32.0.1"))  // outside 172.16/12
        assertEquals(false, isPrivateRFC1918("172.15.0.1"))  // outside
        assertEquals(false, isPrivateRFC1918("8.8.8.8"))     // public
        assertEquals(false, isPrivateRFC1918("169.254.1.5")) // link-local, not rfc1918
        assertEquals(false, isPrivateRFC1918("mesh.local"))
    }

    @Test
    fun linkLocal_recognized() {
        assertEquals(true, isLinkLocal("169.254.0.1"))
        assertEquals(true, isLinkLocal("169.254.255.255"))
        assertEquals(false, isLinkLocal("169.255.0.1"))
        assertEquals(false, isLinkLocal("192.168.1.1"))
        assertEquals(false, isLinkLocal(""))
        assertEquals(false, isLinkLocal("fe80::1"))
    }

    @Test
    fun trustTierOrDefault_parsesFromTag() {
        // The classifier relies on this — exercise the three
        // tag values that mDNS commonly advertises
        // (`TrustTier.tag` is the canonical serialized form).
        assertEquals(TrustTier.WAN, peer("1.2.3.4", tier = TrustTier.WAN.tag).trustTierOrDefault())
        assertEquals(TrustTier.LOCAL_TRUSTED, peer("1.2.3.4", tier = TrustTier.LOCAL_TRUSTED.tag).trustTierOrDefault())
        // Unknown tag → sandboxed default (defensive).
        assertEquals(TrustTier.LOCAL_SANDBOXED, peer("1.2.3.4", tier = "garbage").trustTierOrDefault())
    }
}
