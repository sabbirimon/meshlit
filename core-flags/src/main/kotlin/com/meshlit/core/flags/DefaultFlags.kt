package com.meshlit.core.flags

/**
 * The set of feature flags recognised by the Phase 0 dynamic
 * foundation. New flags land here so [InMemoryFeatureFlagRegistry]
 * and [com.meshlit.flags.DataStoreFeatureFlagRegistry] (production)
 * agree on what defaults look like without either having to import
 * the other.
 *
 * Keep this table small. Every flag is a permanent API surface —
 * renaming a flag means a migration on every installed device.
 */
object DefaultFlags {

    /** NSD-based LAN discovery (the existing
     *  [com.meshlit.core.discovery.DiscoveryCoordinator] +
     *  NsdDiscoveryTransport path). */
    val DISCOVERY_NSD = FeatureFlag(
        name = "feature.discovery.nsd",
        default = true,
        description = "Discover peers on the local network via Android NSD.",
    )

    /** Gossip membership protocol. Off until Phase 0.4 ships the
     *  SWIM state machine. */
    val GOSSIP_ENABLED = FeatureFlag(
        name = "feature.gossip.enabled",
        default = false,
        description = "Run the SWIM gossip protocol for cluster membership.",
    )

    /** Detect bypass-charge (USB-PD / wireless charger that does not
     *  count against battery state of charge). Phase 0.5. */
    val POWER_BYPASS_CHARGE = FeatureFlag(
        name = "feature.power.bypass_charge",
        default = true,
        description = "Treat bypass-charge as wall power for power-policy decisions.",
    )

    /** Register the McpServerStub in the service registry. */
    val LIFECYCLE_MCP_STUB = FeatureFlag(
        name = "feature.lifecycle.mcp_stub",
        default = true,
        description = "Register the embedded MCP server as a ManagedService stub.",
    )

    /** Register the AgentRuntimeStub in the service registry. */
    val LIFECYCLE_AGENT_STUB = FeatureFlag(
        name = "feature.lifecycle.agent_stub",
        default = true,
        description = "Register the Agent runtime as a ManagedService stub.",
    )

    /** Every flag shipped by default. Add new flags here. */
    val ALL: List<FeatureFlag> = listOf(
        DISCOVERY_NSD,
        GOSSIP_ENABLED,
        POWER_BYPASS_CHARGE,
        LIFECYCLE_MCP_STUB,
        LIFECYCLE_AGENT_STUB,
    )
}
