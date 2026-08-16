package com.meshlit.core.firewall

import com.meshlit.core.common.logger
import com.meshlit.core.trust.TrustTier

/**
 * One port-and-protocol-aware firewall rule. Layered on top of the
 * Phase-3 [FirewallPolicy] (which already covers CIDR / node / tier
 * gating). The two layers compose — [MeshlitFirewall.decide] asks
 * the port layer first, then falls back to the phase-3 layer.
 *
 * Why two layers:
 *  - The phase-3 policy is *who* — CIDR / node / trust tier. Stable
 *    per-peer identity, doesn't change as Meshlit grows ports.
 *  - The port layer is *what* — which ports this peer may hit on
 *    this device, which protocols are allowed, and the direction
 *    of the connection. Adds zero-trust defaults ("deny by
 *    default") without forcing the user to maintain a
 *    hand-rolled CIDR list.
 */
@kotlinx.serialization.Serializable
data class PortRule(
    val id: String,
    val peerFingerprint: String = "",
    val portSpec: PortSpec = PortSpec.Any,
    val protocol: PortProtocol = PortProtocol.TCP,
    val direction: PortDirection = PortDirection.INBOUND,
    val allowed: Boolean = true,
    val priority: Int = 0,
    val reason: String = "",
)

@kotlinx.serialization.Serializable
enum class PortProtocol { TCP, UDP, ANY }

@kotlinx.serialization.Serializable
enum class PortDirection { INBOUND, OUTBOUND, BOTH }

/** Port-matching spec. See [PortSpec.Companion] for helpers. */
@kotlinx.serialization.Serializable
data class PortSpec(
    val kind: Kind,
    val exact: Int = 0,
    val start: Int = 0,
    val end: Int = 0,
    val knownProxy: KnownProxy = KnownProxy.NONE,
) {
    enum class Kind { ANY, SINGLE, RANGE, KNOWN }

    fun matches(port: Int): Boolean = when (kind) {
        Kind.ANY -> true
        Kind.SINGLE -> port == exact
        Kind.RANGE -> port in start..end
        Kind.KNOWN -> when (knownProxy) {
            KnownProxy.MESHLIT_DEFAULT -> port == 8080
            KnownProxy.MESHLIT_AGENT_BRIDGE -> port == 8090
            KnownProxy.MESHLIT_FILE_SHARE -> port == 8100
            KnownProxy.MESHLIT_TERMINAL -> port == 8110
            KnownProxy.MESHLIT_CLUSTER_GATEWAY -> port == 8120
            KnownProxy.NONE -> false
        }
    }

    fun describe(): String = when (kind) {
        Kind.ANY -> "any"
        Kind.SINGLE -> "$exact"
        Kind.RANGE -> "$start-$end"
        Kind.KNOWN -> when (knownProxy) {
            KnownProxy.MESHLIT_DEFAULT -> "Meshlit SSE (8080)"
            KnownProxy.MESHLIT_AGENT_BRIDGE -> "Agent bridge (8090)"
            KnownProxy.MESHLIT_FILE_SHARE -> "File share (8100)"
            KnownProxy.MESHLIT_TERMINAL -> "Terminal (8110)"
            KnownProxy.MESHLIT_CLUSTER_GATEWAY -> "Cluster gateway (8120)"
            KnownProxy.NONE -> "?"
        }
    }

    companion object {
        val Any = PortSpec(kind = Kind.ANY)
        fun Single(p: Int) = PortSpec(kind = Kind.SINGLE, exact = p)
        fun Range(s: Int, e: Int) = PortSpec(kind = Kind.RANGE, start = s, end = e)
        fun Known(p: KnownProxy) = PortSpec(kind = Kind.KNOWN, knownProxy = p)
    }
}

/** Well-known port bundles. Mirrors the catalogue the Settings UI
 *  renders as quick-pick chips. */
enum class KnownProxy(val tag: String, val port: Int) {
    NONE("", 0),
    MESHLIT_DEFAULT("meshlit-default", 8080),
    MESHLIT_AGENT_BRIDGE("meshlit-agent-bridge", 8090),
    MESHLIT_FILE_SHARE("meshlit-file-share", 8100),
    MESHLIT_TERMINAL("meshlit-terminal", 8110),
    MESHLIT_CLUSTER_GATEWAY("meshlit-cluster-gateway", 8120);

    companion object {
        fun fromTag(tag: String): KnownProxy =
            entries.firstOrNull { it.tag == tag } ?: NONE
    }
}

@kotlinx.serialization.Serializable
enum class PortDefaultAction { ALLOW, DENY }

@kotlinx.serialization.Serializable
data class PortLayerPolicy(
    val rules: List<PortRule> = emptyList(),
    val defaultAction: PortDefaultAction = PortDefaultAction.DENY,
)

/**
 * The composite firewall. Wraps the phase-3 [FirewallPolicy] (CIDR /
 * node / tier) and adds the port / protocol / direction layer. The
 * final verdict is `DENY` if either layer says so — a permissive
 * port layer can't override a strict phase-3 deny.
 */
class MeshlitFirewall(
    private val phase3Policy: FirewallPolicy,
    var portLayer: PortLayerPolicy = PortLayerPolicy(),
) {
    private val log = logger("MeshlitFirewall")

    fun decide(
        remoteAddr: String,
        remoteNodeId: String?,
        remoteTier: TrustTier?,
        port: Int,
        protocol: PortProtocol = PortProtocol.TCP,
        direction: PortDirection = PortDirection.INBOUND,
    ): MeshlitFirewallVerdict {
        // Phase 3 first — "who".
        val phase3 = phase3Policy.decide(remoteAddr, remoteNodeId, remoteTier)
        if (phase3 == Decision.DENY) {
            return MeshlitFirewallVerdict.Deny(
                source = MeshlitFirewallSource.PHASE3,
                ruleId = "",
                reason = "phase3-deny",
            )
        }

        // Port layer — "what".
        val portMatch = matchPortLayer(
            peerFingerprint = remoteNodeId ?: "",
            port = port,
            protocol = protocol,
            direction = direction,
        )
        if (portMatch != null) {
            return if (portMatch.allowed) {
                MeshlitFirewallVerdict.Allow(
                    source = MeshlitFirewallSource.PORT_LAYER,
                    ruleId = portMatch.id,
                    reason = portMatch.reason,
                )
            } else {
                MeshlitFirewallVerdict.Deny(
                    source = MeshlitFirewallSource.PORT_LAYER,
                    ruleId = portMatch.id,
                    reason = portMatch.reason,
                )
            }
        }
        return when (portLayer.defaultAction) {
            PortDefaultAction.ALLOW -> MeshlitFirewallVerdict.Allow(
                source = MeshlitFirewallSource.DEFAULT,
                ruleId = "",
                reason = "port-default-allow",
            )
            PortDefaultAction.DENY -> MeshlitFirewallVerdict.Deny(
                source = MeshlitFirewallSource.DEFAULT,
                ruleId = "",
                reason = "port-default-deny",
            )
        }
    }

    /**
     * Bridge for callers that already speak [Decision] (the legacy
     * [PortFilter] + [InferenceHttpServer] gate). Returns
     * `DENY` if either layer denies; `QUARANTINE` if the peer
     * survives both layers but the phase-3 tier is
     * `LOCAL_SANDBOXED` (so write endpoints still deny via
     * [PortFilter]); `ALLOW` otherwise.
     */
    fun decideLegacy(
        remoteAddr: String,
        remoteNodeId: String?,
        remoteTier: TrustTier?,
        port: Int,
        protocol: PortProtocol = PortProtocol.TCP,
        direction: PortDirection = PortDirection.INBOUND,
    ): Decision {
        val v = decide(remoteAddr, remoteNodeId, remoteTier, port, protocol, direction)
        if (!v.allowed) return Decision.DENY
        // Sandbox + writes → PortFilter still escalates to QUARANTINE.
        if (remoteTier == TrustTier.LOCAL_SANDBOXED) return Decision.QUARANTINE
        return Decision.ALLOW
    }

    private fun matchPortLayer(
        peerFingerprint: String,
        port: Int,
        protocol: PortProtocol,
        direction: PortDirection,
    ): PortRule? {
        val ordered = portLayer.rules.sortedByDescending { it.priority }
        return ordered.firstOrNull { rule ->
            if (rule.direction != PortDirection.BOTH && rule.direction != direction) return@firstOrNull false
            if (rule.protocol != PortProtocol.ANY && rule.protocol != protocol) return@firstOrNull false
            if (rule.peerFingerprint.isNotBlank() &&
                rule.peerFingerprint != peerFingerprint
            ) return@firstOrNull false
            rule.portSpec.matches(port)
        }
    }

    companion object {
        /**
         * Default starter: phase-3 CIDR allow-list + port layer
         * opens Meshlit's default SSE port (8080) on TCP inbound
         * for any peer that survives phase 3. Anything else falls
         * through to the port layer's `default-deny`, which is the
         * safe starter posture.
         */
        val Starter: MeshlitFirewall = MeshlitFirewall(
            phase3Policy = FirewallPolicy.Default,
            portLayer = PortLayerPolicy(
                rules = listOf(
                    PortRule(
                        id = "starter-meshlit-8080",
                        peerFingerprint = "",
                        portSpec = PortSpec.Known(KnownProxy.MESHLIT_DEFAULT),
                        protocol = PortProtocol.TCP,
                        direction = PortDirection.INBOUND,
                        allowed = true,
                        priority = 0,
                        reason = "Meshlit SSE / health / model mgmt",
                    ),
                ),
                defaultAction = PortDefaultAction.DENY,
            ),
        )
    }
}

enum class MeshlitFirewallSource { PHASE3, PORT_LAYER, DEFAULT }

data class MeshlitFirewallVerdict(
    val allowed: Boolean,
    val source: MeshlitFirewallSource,
    val ruleId: String,
    val reason: String,
) {
    companion object {
        fun Allow(source: MeshlitFirewallSource, ruleId: String, reason: String) =
            MeshlitFirewallVerdict(true, source, ruleId, reason)
        fun Deny(source: MeshlitFirewallSource, ruleId: String, reason: String) =
            MeshlitFirewallVerdict(false, source, ruleId, reason)
    }
}