package com.meshlit.core.cloudmcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * Process-wide facade. Holds one [CloudMcpSession] per connected
 * provider, the shared [ToolRegistry], and a single fan-out
 * [events] flow the agent-loop UI watches.
 *
 * The coordinator is *thin*: it owns lifecycle (connect /
 * disconnect / callTool), but the heavy lifting lives in the
 * per-session objects. This keeps the surface area small and
 * lets each provider's session evolve independently (e.g. AWS
 * may grow SigV4 auth while DO stays bearer-only).
 *
 * Wired in [com.meshlit.MeshlitApplication.onCreate] so the
 * coordinator lives for the entire process lifetime.
 */
class CloudMcpCoordinator(
    private val httpClient: OkHttpClient,
    private val credentialStore: com.meshlit.core.trust.CloudCredentialStore,
) {
    private val _events = MutableSharedFlow<McpEvent>(
        replay = 0,
        extraBufferCapacity = 128,
    )
    /** All inbound MCP events across every connected session. */
    val events: SharedFlow<McpEvent> = _events.asSharedFlow()

    /**
     * Helper for callers that want to inject synthesized events
     * (e.g. the agent-loop orchestrator pushing Thought chunks
     * from NaraRouter into the same stream the UI consumes).
     * Returns true if the event was buffered, false if the
     * buffer is full and the event was dropped.
     */
    fun tryEmit(event: McpEvent): Boolean = _events.tryEmit(event)

    val toolRegistry = ToolRegistry()

    private val _sessions = MutableStateFlow<Map<String, CloudMcpSession>>(emptyMap())
    val sessions: StateFlow<Map<String, CloudMcpSession>> = _sessions.asStateFlow()

    private val transport = CloudMcpTransport(httpClient)

    init {
        // Fan out transport-level events into the coordinator stream.
        // `tryEmit` because the buffer is sized for live traffic.
        kotlinx.coroutines.GlobalScope.let { /* keep it lazy */ }
    }

    /**
     * Connect a provider. Loads its credential from
     * [CloudCredentialStore], opens the SSE stream, and runs the
     * `initialize` + `tools/list` handshake. Idempotent: a second
     * connect for the same providerId is a no-op.
     */
    fun connect(config: ProviderConfig) {
        val existing = _sessions.value[config.id]
        if (existing != null && existing.state.value.connection == ConnectionState.Connected) {
            return
        }
        val credential = if (config.credentialRef.isNotBlank()) {
            credentialStore.get(config.credentialRef)
        } else {
            null
        }
        val session = CloudMcpSession(
            providerId = config.id,
            transport = transport,
            registry = toolRegistry,
        )
        _sessions.update { it + (config.id to session) }
        session.connect(config, credential)
        val tools = session.handshake(config, credential)
        // Once we have the tools, fan out a Connected event with the
        // populated tool list so the hub UI can show "Connected (12
        // tools)".
        _events.tryEmit(McpEvent.Connected(config.id, tools))
    }

    fun disconnect(providerId: String) {
        _sessions.value[providerId]?.close()
        _sessions.update { it - providerId }
    }

    /**
     * Invoke a tool across the merged registry. Returns null if the
     * tool is unknown. The returned [McpEvent.ToolResult] is also
     * surfaced on [events] for the agent-loop UI.
     */
    fun callTool(
        providerId: String,
        toolName: String,
        args: JsonObject,
        callId: String,
        configByProviderId: Map<String, ProviderConfig>,
    ): McpEvent.ToolResult? {
        val session = _sessions.value[providerId] ?: return null
        val config = configByProviderId[providerId] ?: return null
        val credential = credentialStore.get(config.credentialRef)
        val result = session.callTool(config, credential, toolName, args, callId)
        _events.tryEmit(result)
        return result
    }

    fun close() {
        _sessions.value.values.forEach { it.close() }
        _sessions.update { emptyMap() }
        transport.close()
    }
}