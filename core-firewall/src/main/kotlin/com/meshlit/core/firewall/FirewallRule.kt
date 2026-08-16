package com.meshlit.core.firewall

import com.meshlit.core.trust.TrustTier

/**
 * Building blocks for [FirewallPolicy].
 *
 * Rules are evaluated in order: the first match wins. The final
 * fallback is `DenyAll` when no rule matches.
 *
 *  - [AllowSubnet]  — match by IPv4 CIDR (e.g. `192.168.1.0/24`).
 *  - [AllowNode]   — match by nodeId string (e.g. the value persisted
 *                    in `DeviceTrustPolicy.nodeId`).
 *  - [AllowTier]   — match by `TrustTier`. The remote tier is read
 *                    from `LocalTrustPolicy.current()` for inbound,
 *                    or from a peer's `DeviceTrustPolicy` for outbound
 *                    routing decisions.
 *  - [DenyAll]     — catch-all.
 */
sealed class FirewallRule {
    abstract fun matches(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Boolean

    /** IPv4 CIDR. v1 supports `/8` … `/32` only. */
    data class AllowSubnet(val cidr: String) : FirewallRule() {
        private val parsed: Cidr? by lazy { CidrMatcher.parse(cidr) }
        override fun matches(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Boolean =
            parsed?.contains(remoteAddr) ?: false
    }

    data class AllowNode(val nodeId: String) : FirewallRule() {
        override fun matches(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Boolean =
            remoteNodeId != null && remoteNodeId == nodeId
    }

    data class AllowTier(val tier: TrustTier) : FirewallRule() {
        override fun matches(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Boolean =
            remoteTier == tier
    }

    object DenyAll : FirewallRule() {
        override fun matches(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Boolean = true
    }
}

/**
 * A composable firewall. The first rule that matches wins. If no rule
 * matches, the [defaultAllow] flag decides — `false` is the safer
 * default for an open port on `0.0.0.0`.
 */
data class FirewallPolicy(
    val rules: List<FirewallRule>,
    val defaultAllow: Boolean = false,
) {
    fun decide(remoteAddr: String, remoteNodeId: String?, remoteTier: TrustTier?): Decision {
        for (rule in rules) {
            if (rule.matches(remoteAddr, remoteNodeId, remoteTier)) {
                return when (rule) {
                    is FirewallRule.AllowSubnet,
                    is FirewallRule.AllowNode,
                    is FirewallRule.AllowTier -> Decision.ALLOW
                    is FirewallRule.DenyAll -> Decision.DENY
                }
            }
        }
        return if (defaultAllow) Decision.ALLOW else Decision.DENY
    }

    companion object {
        /** Default Phase 3 policy:
         *  - allow same-subnet /24 callers (LAN)
         *  - allow explicitly-paired nodes
         *  - allow LOCAL_TRUSTED tier
         *  - quarantine LOCAL_SANDBOXED (deny by default; QUARANTINE
         *    is a sentinel returned by the HTTP layer for read-only
         *    endpoints — see [FirewallPolicy.decide])
         *  - deny WAN
         *  - deny by default
         */
        val Default: FirewallPolicy = FirewallPolicy(
            rules = listOf(
                FirewallRule.AllowSubnet("192.168.0.0/16"),
                FirewallRule.AllowSubnet("10.0.0.0/8"),
                FirewallRule.AllowSubnet("172.16.0.0/12"),
                FirewallRule.AllowTier(TrustTier.LOCAL_TRUSTED),
            ),
            defaultAllow = false,
        )
    }
}

enum class Decision { ALLOW, QUARANTINE, DENY }

/** Tiny CIDR matcher (IPv4 only). Internal to `core-firewall`. */
internal object CidrMatcher {
    fun parse(cidr: String): Cidr? {
        val parts = cidr.split('/')
        if (parts.size != 2) return null
        val addr = ipv4ToLong(parts[0]) ?: return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        return Cidr(addr and mask, mask)
    }

    fun ipv4ToLong(addr: String): Long? {
        val parts = addr.split('.')
        if (parts.size != 4) return null
        var result = 0L
        for (p in parts) {
            val n = p.toLongOrNull() ?: return null
            if (n !in 0L..255L) return null
            result = (result shl 8) or n
        }
        return result
    }
}

internal class Cidr internal constructor(
    private val network: Long,
    private val mask: Long,
) {
    fun contains(addr: String): Boolean {
        val asLong = CidrMatcher.ipv4ToLong(addr) ?: return false
        return (asLong and mask) == network
    }
}
