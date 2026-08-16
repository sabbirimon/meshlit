package com.meshlit.core.role

/**
 * The advisory role for a single node.
 *
 *  - [Idle] — the device is not a useful contributor. Always
 *    eligible (so the policy always returns a non-null suggestion),
 *    but never the winner unless nothing else scored above its
 *    baseline.
 *  - [Brain] — flagship SoC + NPU + 6 GB+ RAM + battery + no
 *    thermal stress. The router treats Brain as the preferred
 *    inference target.
 *  - [Tool] — mid-tier device with enough RAM to host an MCP server
 *    or run a small model. The second-most-preferred role.
 *  - [Monitor] — anything that can stay awake and reachable. The
 *    router uses monitors for health checks + relay traffic.
 *  - [Relay] — wall-powered device that can forward traffic.
 *    Reserved for Phase 0.5 (needs bypass-charge detection).
 *
 * New roles are added here as the cluster surface grows.
 */
enum class Role {
    Idle,
    Brain,
    Tool,
    Monitor,
    Relay,
}
