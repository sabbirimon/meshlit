package com.meshlit.registry

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.flags.DefaultFlags
import com.meshlit.core.lifecycle.ManagedService
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.registry.ServiceKind

/**
 * Phase 0.2 stub for the agent runtime. No-op until Phase 1 wires up
 * the real `AgentRuntime`. Exists so the lifecycle controller has
 * something to start/stop/health-check that proves the loop end-to-
 * end before any of the real agent surface lands.
 */
class AgentRuntimeStub : ManagedService {

    override val id: String = "agent-runtime"
    override val kind: ServiceKind = ServiceKind.AgentRuntime
    override val dependencies: List<String> = emptyList()
    override val requiredFeatureFlag: String? = DefaultFlags.LIFECYCLE_AGENT_STUB.name

    override val descriptorFactory: (String) -> ServiceDescriptor = { nodeId ->
        ServiceDescriptor(
            id = id,
            name = "Agent runtime (stub)",
            kind = kind,
            ownerNodeId = nodeId,
            version = "0.0.1",
            capabilities = emptyList(),
            health = HealthState.Healthy,
            registeredAtMs = System.currentTimeMillis(),
        )
    }

    override suspend fun start(): MeshlitResult<Unit> = MeshlitResult.Success(Unit)
    override suspend fun stop(): MeshlitResult<Unit> = MeshlitResult.Success(Unit)
    override suspend fun healthCheck(): HealthState = HealthState.Healthy
}
