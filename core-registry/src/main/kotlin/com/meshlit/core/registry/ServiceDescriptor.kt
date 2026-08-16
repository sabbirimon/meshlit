package com.meshlit.core.registry

/**
 * Coarse taxonomy of the services a node can register. The router
 * keys on this to decide where to forward a job.
 *
 * New kinds land here rather than as free-form strings so the
 * switch statements in the dispatcher can be exhaustive.
 */
enum class ServiceKind {
    /** Generic anything-else category. Used by the agent runtime
     *  stub and any future plug-in service. */
    Generic,

    /** Embedded MCP HTTP server (the production MeshlitServerController). */
    McpServer,

    /** Agent runtime — receives prompts, dispatches tools. */
    AgentRuntime,

    /** Hardware-accelerated inference (llama.cpp, ONNX, etc.). */
    InferenceEngine,

    /** Cluster storage — peer-to-peer file / blob sharing. */
    ClusterStorage,
}

/**
 * The descriptor the registry holds for every registered service.
 * Updated in place as the lifecycle controller brings the service
 * up, runs health checks, and tears it down.
 *
 * @property id Stable across the process lifetime — typically the
 *  service's Koin singleton key.
 * @property name Human-readable label (e.g. "MCP server").
 * @property kind Router-relevant category.
 * @property ownerNodeId The local node id (`BootstrapSnapshot.nodeId`).
 * @property version Free-form, semver-ish. The router prefers
 *  higher-version peers when several are eligible.
 * @property capabilities Free-form list of capability tags the
 *  service implements. Empty for the agent stub.
 * @property health Latest health-check result.
 * @property registeredAtMs Wall-clock ms when the descriptor was
 *  first added.
 * @property lastHeartbeatMs Wall-clock ms when the last successful
 *  health check ran. `null` if never checked.
 */
data class ServiceDescriptor(
    val id: String,
    val name: String,
    val kind: ServiceKind,
    val ownerNodeId: String,
    val version: String,
    val capabilities: List<String> = emptyList(),
    val health: HealthState = HealthState.Unknown,
    val registeredAtMs: Long,
    val lastHeartbeatMs: Long? = null,
)
