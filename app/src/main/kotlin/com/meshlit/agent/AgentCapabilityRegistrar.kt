package com.meshlit.agent

import com.meshlit.core.cloudmcp.ToolRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRouter
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Bridges [com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry]
 * into the [ToolRegistry] the agent loop pulls from.
 *
 * For each [AgentCapability] we observe the live state — when
 * it's enabled AND the runtime permission is granted, we put its
 * `agent_*` tools into the cloud-MCP registry; when either flips
 * off, we remove them.
 *
 * **Why this lives in the app module, not the core:**
 * The tool registry's `put` / `removeProvider` API is in
 * `core-cloud-mcp`, but the `AppScope` + `settingsRepository`
 * live in the app. The registrar glues them.
 */
class AgentCapabilityRegistrar(
    private val appScope: CoroutineScope,
    private val toolRegistry: ToolRegistry,
    private val holder: AgentCapabilityRegistryHolder,
) {
    fun start() {
        for (cap in AgentCapability.entries) {
            appScope.launch {
                // enabledByUser and permissionGranted are both
                // observed on the holder.state flow. We expose the
                // whole map and filter per capability inside the
                // combine so we don't need N flow accessors.
                combine(
                    holder.registry.state.map { it[cap]?.enabledByUser ?: false },
                    holder.registry.state.map { it[cap]?.permissionGranted ?: false },
                ) { enabled, granted -> enabled && granted }.collect { active ->
                    val key = AgentCapabilityTools.PROVIDER_ID
                    if (active) {
                        toolRegistry.putAll(key, AgentCapabilityTools.toolsFor(cap))
                    } else {
                        // Only remove the tools owned by this
                        // capability — leave the others alone.
                        for (tool in AgentCapabilityTools.toolsFor(cap)) {
                            toolRegistry.remove(key, tool.name)
                        }
                    }
                }
            }
        }
    }
}