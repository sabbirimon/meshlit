@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.core.mcp

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.mcp.adapters.MeshlitServerAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide controller for the embedded MCP HTTP server.
 *
 * Wraps [MeshlitServerAdapter] with idempotent [start] / [stop] /
 * [restart] semantics and exposes the current [MeshlitServerState]
 * as a [StateFlow] so the Settings → MCP screen can render a live
 * toggle without polling.
 *
 * Lifecycle:
 *  - [MeshlitApplication.onCreate] calls [start] eagerly so the
 *    embedded MCP server is reachable on first launch.
 *  - Settings → MCP flips a checkbox → calls [start] / [stop].
 *  - Idempotent: a second [start] is a no-op while one is already
 *    running. A second [stop] is also a no-op.
 *  - [restart] is the safe helper for "port changed" or "host
 *    bind changed" — it stops the current adapter, then starts a
 *    new one with the requested config.
 *
 * Bind host policy: defaults to `127.0.0.1` so the server is reachable
 * only on-device. The advanced settings UI can flip to `0.0.0.0`
 * after a confirmation dialog.
 */
class MeshlitServerController(
    private val registryProvider: () -> McpToolRegistry = { McpToolRegistry() },
    private val poolProvider: () -> McpClientPool = {
        McpClientPool(registry = McpToolRegistry())
    },
    private val defaultHost: String = "127.0.0.1",
    private val defaultPort: Int = 7700,
) {

    private val log = logger("MeshlitServerController")

    private val mutex = Mutex()

    private val _state = MutableStateFlow<MeshlitServerState>(MeshlitServerState.Stopped)
    val state: StateFlow<MeshlitServerState> = _state.asStateFlow()

    private var adapter: MeshlitServerAdapter? = null

    /** Effective host the current adapter is bound to. `null` when stopped. */
    val boundHost: String? get() = (state.value as? MeshlitServerState.Running)?.host

    /** Effective port the current adapter is bound to. `null` when stopped. */
    val boundPort: Int? get() = (state.value as? MeshlitServerState.Running)?.port

    /** Convenience: `true` when [state] is [MeshlitServerState.Running]. */
    val isRunning: Boolean get() = state.value is MeshlitServerState.Running

    /**
     * Start the embedded MCP HTTP server on [host]:[port]. Idempotent:
     * calling while already running with the same parameters is a
     * no-op success. Calling with different parameters issues a
     * restart under the same mutex.
     */
    suspend fun start(
        host: String = defaultHost,
        port: Int = defaultPort,
    ): MeshlitResult<Unit> = mutex.withLock {
        val current = state.value
        if (current is MeshlitServerState.Running
            && current.host == host
            && current.port == port
        ) {
            log.info("mcp.server.start.noop", "server already running", mapOf("host" to host, "port" to port))
            return@withLock MeshlitResult.Success(Unit)
        }
        if (current is MeshlitServerState.Running) {
            // Different bind — tear down the current adapter first.
            stopAdapterLocked()
        }
        return@withLock startAdapterLocked(host, port)
    }

    /**
     * Stop the embedded MCP HTTP server. Idempotent: calling while
     * stopped is a no-op success.
     */
    suspend fun stop(): MeshlitResult<Unit> = mutex.withLock {
        if (state.value !is MeshlitServerState.Running) {
            return@withLock MeshlitResult.Success(Unit)
        }
        stopAdapterLocked()
        MeshlitResult.Success(Unit)
    }

    /**
     * Stop, then start. Equivalent to `stop()` followed by `start(...)`
     * under a single mutex acquisition so the bind change is atomic.
     */
    suspend fun restart(
        host: String = defaultHost,
        port: Int = defaultPort,
    ): MeshlitResult<Unit> = mutex.withLock {
        if (state.value is MeshlitServerState.Running) {
            stopAdapterLocked()
        }
        startAdapterLocked(host, port)
    }

    // ---- internal helpers (caller must hold [mutex]) -----------------------

    private fun startAdapterLocked(host: String, port: Int): MeshlitResult<Unit> {
        _state.value = MeshlitServerState.Starting
        return try {
            val newAdapter = MeshlitServerAdapter(
                registry = registryProvider(),
                pool = poolProvider(),
                port = port,
                host = host,
            )
            newAdapter.start()
            val resolved = newAdapter.listeningPort
            adapter = newAdapter
            _state.value = MeshlitServerState.Running(
                host = host,
                port = if (resolved > 0) resolved else port,
                startedAtMs = System.currentTimeMillis(),
            )
            log.info(
                "mcp.server.start",
                "embedded MCP server started",
                mapOf("host" to host, "port" to (_state.value as MeshlitServerState.Running).port),
            )
            MeshlitResult.Success(Unit)
        } catch (t: Throwable) {
            _state.value = MeshlitServerState.Stopped
            log.error("mcp.server.start.fail", "embedded MCP server failed to start", t)
            MeshlitResult.Failure(MeshlitError.Native("mcp_server_start_fail", t))
        }
    }

    private fun stopAdapterLocked() {
        val running = adapter ?: return
        _state.value = MeshlitServerState.Stopping
        try {
            running.stop()
        } catch (t: Throwable) {
            log.warn("mcp.server.stop.fail", "stop threw", mapOf("err" to (t.message ?: t.javaClass.simpleName)))
        } finally {
            adapter = null
            _state.value = MeshlitServerState.Stopped
            log.info("mcp.server.stop", "embedded MCP server stopped")
        }
    }
}

/** Lifecycle states of the embedded MCP HTTP server. */
sealed class MeshlitServerState {
    /** The server is not running. */
    data object Stopped : MeshlitServerState()

    /** A start is in flight. The UI typically renders a spinner. */
    data object Starting : MeshlitServerState()

    /** A stop is in flight. */
    data object Stopping : MeshlitServerState()

    /** The server is bound and accepting connections. */
    data class Running(
        val host: String,
        val port: Int,
        val startedAtMs: Long,
    ) : MeshlitServerState()
}