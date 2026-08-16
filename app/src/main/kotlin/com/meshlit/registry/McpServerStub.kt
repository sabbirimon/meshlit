package com.meshlit.registry

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.flags.DefaultFlags
import com.meshlit.core.lifecycle.ManagedService
import com.meshlit.core.mcp.MeshlitServerController
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.registry.ServiceKind
import kotlinx.coroutines.flow.first

/**
 * Phase 0.2 stub for the embedded MCP HTTP server.
 *
 * Wraps the existing [MeshlitServerController] but does not *replace*
 * it — `MeshlitApplication.bootMcp()` still starts the controller
 * directly on its own port (7700). The stub registers itself in the
 * [ServiceRegistry] and re-uses the controller so its lifecycle
 * shows up in the dynamic-foundation view.
 *
 * Real MCP work (JSON-RPC, tool handlers, sse streaming) is owned by
 * `MeshlitServerController` + `MeshlitServerAdapter` and is unchanged.
 */
class McpServerStub(
    private val controller: MeshlitServerController,
) : ManagedService {

    override val id: String = "mcp-server"
    override val kind: ServiceKind = ServiceKind.McpServer
    override val dependencies: List<String> = emptyList()
    override val requiredFeatureFlag: String? = DefaultFlags.LIFECYCLE_MCP_STUB.name

    override val descriptorFactory: (String) -> ServiceDescriptor = { nodeId ->
        ServiceDescriptor(
            id = id,
            name = "Embedded MCP server",
            kind = kind,
            ownerNodeId = nodeId,
            version = "0.1.0",
            capabilities = listOf("mcp.jsonrpc", "mcp.sse"),
            health = HealthState.Unknown,
            registeredAtMs = System.currentTimeMillis(),
        )
    }

    private val log = logger("McpServerStub")

    override suspend fun start(): MeshlitResult<Unit> = controller.start()

    override suspend fun stop(): MeshlitResult<Unit> = controller.stop()

    override suspend fun healthCheck(): HealthState {
        val st = controller.state.first()
        return when (st) {
            is com.meshlit.core.mcp.MeshlitServerState.Running -> HealthState.Healthy
            com.meshlit.core.mcp.MeshlitServerState.Starting,
            com.meshlit.core.mcp.MeshlitServerState.Stopping -> HealthState.Degraded("transitioning")
            com.meshlit.core.mcp.MeshlitServerState.Stopped -> HealthState.Unreachable("not running")
        }
    }
}
