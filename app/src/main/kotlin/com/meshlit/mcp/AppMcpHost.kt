package com.meshlit.mcp

import com.meshlit.MeshlitApplication
import com.meshlit.feature.advanced.McpHost
import com.meshlit.feature.advanced.McpHostState
import com.meshlit.core.mcp.MeshlitServerState
import com.meshlit.core.mcp.UserMcpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-side implementation of [com.meshlit.feature.advanced.McpHost].
 *
 * Bridges the `:feature-advanced` UI surface to the actual
 * [com.meshlit.core.mcp.MeshlitServerController] and
 * [com.meshlit.core.mcp.UserMcpServerStore] singletons living on
 * [MeshlitApplication].
 *
 * The host exposes:
 *  - a [state] flow deriving a [McpHostState] from the controller's
 *    [MeshlitServerState];
 *  - a [userServers] flow of user-added MCP server names;
 *  - imperative [start]/[stop]/[addServer]/[removeServer] methods
 *    that call into the real controller and store.
 */
class AppMcpHost(private val app: MeshlitApplication) : McpHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val state: StateFlow<McpHostState> =
        app.meshlitServerController.state
            .let { upstream ->
                val flow = MutableStateFlow(McpHostState())
                scope.launch {
                    upstream.collect { s ->
                        flow.value = when (s) {
                            is MeshlitServerState.Running ->
                                McpHostState(true, s.host, s.port)
                            else -> McpHostState(false)
                        }
                    }
                }
                flow.asStateFlow()
            }

    override val userServers: StateFlow<List<String>> =
        app.userMcpServerStore.servers
            .let { upstream ->
                val flow = MutableStateFlow<List<String>>(emptyList())
                scope.launch {
                    upstream.collect { servers -> flow.value = servers.map { it.name } }
                }
                flow.asStateFlow()
            }

    override fun start() {
        scope.launch { app.meshlitServerController.start() }
    }

    override fun stop() {
        scope.launch { app.meshlitServerController.stop() }
    }

    override fun addServer(name: String, command: String) {
        scope.launch {
            val id = "user-${System.nanoTime()}"
            val entry = UserMcpServer(
                id = id,
                name = name,
                command = command,
                enabled = true,
            )
            app.userMcpServerStore.upsert(entry)
            app.userMcpServerStore.applyTo(app.mcpClientPool)
        }
    }

    override fun removeServer(id: String) {
        scope.launch {
            app.userMcpServerStore.remove(id)
            app.userMcpServerStore.applyTo(app.mcpClientPool)
        }
    }
}