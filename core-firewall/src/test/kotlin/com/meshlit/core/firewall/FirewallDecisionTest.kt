package com.meshlit.core.firewall

import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Test

class FirewallDecisionTest {

    private val policy = FirewallPolicy.Default

    @Test
    fun trusted_inside_lan_subnet_is_allowed() {
        // 192.168.1.42 falls inside the Default policy's
        // AllowSubnet("192.168.0.0/16") rule.
        val d = policy.decide("192.168.1.42", remoteNodeId = null, remoteTier = null)
        assertEquals(Decision.ALLOW, d)
    }

    @Test
    fun explicitly_trusted_node_id_is_allowed() {
        val custom = FirewallPolicy(
            rules = listOf(FirewallRule.AllowNode("node-X")),
            defaultAllow = false,
        )
        assertEquals(Decision.ALLOW, custom.decide("0.0.0.0", "node-X", null))
        assertEquals(Decision.DENY, custom.decide("0.0.0.0", "node-Y", null))
    }

    @Test
    fun unknown_ip_outside_subnet_is_denied() {
        val d = policy.decide("8.8.8.8", remoteNodeId = null, remoteTier = null)
        assertEquals(Decision.DENY, d)
    }

    @Test
    fun local_sandboxed_call_to_write_endpoint_is_quarantined() {
        val filter = PortFilter(policy)
        val d = filter.decide(
            remoteAddr = "192.168.1.42", // inside /16 — would be ALLOW
            remoteNodeId = "guest",
            remoteTier = TrustTier.LOCAL_SANDBOXED,
            endpointPath = "/v1/infer",
        )
        assertEquals(Decision.QUARANTINE, d)
    }

    @Test
    fun local_sandboxed_call_to_read_endpoint_is_allowed() {
        val filter = PortFilter(policy)
        val d = filter.decide(
            remoteAddr = "192.168.1.42",
            remoteNodeId = "guest",
            remoteTier = TrustTier.LOCAL_SANDBOXED,
            endpointPath = "/v1/capabilities",
        )
        assertEquals(Decision.ALLOW, d)
    }

    @Test
    fun wan_tier_is_denied() {
        // The Default policy has no AllowTier(WAN) rule. With
        // defaultAllow=false, an unknown IP is denied.
        val d = policy.decide("203.0.113.7", null, TrustTier.WAN)
        assertEquals(Decision.DENY, d)
    }

    @Test
    fun deny_all_blocks_everything() {
        val p = FirewallPolicy(rules = listOf(FirewallRule.DenyAll), defaultAllow = false)
        assertEquals(Decision.DENY, p.decide("10.0.0.1", "any", TrustTier.LOCAL_TRUSTED))
    }

    @Test
    fun cidr_matcher_basic_24() {
        val m = CidrMatcher.parse("192.168.1.0/24")!!
        assertEquals(true, m.contains("192.168.1.5"))
        assertEquals(true, m.contains("192.168.1.255"))
        assertEquals(false, m.contains("192.168.2.5"))
    }

    @Test
    fun cidr_matcher_invalid_input_returns_null() {
        assertEquals(null, CidrMatcher.parse("not-an-ip/24"))
        assertEquals(null, CidrMatcher.parse("192.168.1.0/64"))
    }

    @Test
    fun port_filter_classifies_endpoints() {
        val f = PortFilter(policy)
        assertEquals(EndpointKind.READ_ONLY, f.classifyEndpoint("/v1/capabilities"))
        assertEquals(EndpointKind.READ_ONLY, f.classifyEndpoint("/v1/health"))
        assertEquals(EndpointKind.WRITE, f.classifyEndpoint("/v1/infer"))
        assertEquals(EndpointKind.READ_ONLY, f.classifyEndpoint("/v1/manifest/llm"))
    }
}
