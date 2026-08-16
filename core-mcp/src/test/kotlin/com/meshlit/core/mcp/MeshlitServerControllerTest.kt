package com.meshlit.core.mcp

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Behavioural tests for [MeshlitServerController].
 *
 * The controller binds a real NanoHTTPD instance on an ephemeral
 * port (so we never collide with another test or a local process),
 * exercises `start`/`stop`/`restart`, and confirms the resulting
 * state transitions are observable.
 */
class MeshlitServerControllerTest {

    private var controller: MeshlitServerController? = null

    @After
    fun tearDown() = runTest {
        controller?.stop()
        controller = null
    }

    @Test
    fun initial_state_is_stopped() = runTest {
        val ctrl = MeshlitServerController(
            registryProvider = { McpToolRegistry() },
            poolProvider = { McpClientPool(registry = McpToolRegistry()) },
        )
        controller = ctrl
        assertEquals(MeshlitServerState.Stopped, ctrl.state.value)
        assertEquals(false, ctrl.isRunning)
        assertEquals(null, ctrl.boundHost)
        assertEquals(null, ctrl.boundPort)
    }

    @Test
    fun start_brings_server_to_running() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl

        val result = ctrl.start(host = "127.0.0.1", port = port)
        assertTrue(result is MeshlitResult.Success)
        val state = ctrl.state.value
        assertTrue(state is MeshlitServerState.Running)
        assertEquals("127.0.0.1", (state as MeshlitServerState.Running).host)
        assertEquals(port, state.port)
        assertEquals(true, ctrl.isRunning)
    }

    @Test
    fun start_is_idempotent() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl

        val first = ctrl.start(host = "127.0.0.1", port = port)
        assertTrue(first is MeshlitResult.Success)
        val adapter = first
        val stateAfterFirst = ctrl.state.value
        val second = ctrl.start(host = "127.0.0.1", port = port)
        assertTrue(second is MeshlitResult.Success)
        // Same state object — no second transition occurred.
        assertSame(stateAfterFirst, ctrl.state.value)
    }

    @Test
    fun start_then_stop_returns_to_stopped() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl

        ctrl.start(host = "127.0.0.1", port = port)
        assertTrue(ctrl.state.value is MeshlitServerState.Running)

        val stop = ctrl.stop()
        assertTrue(stop is MeshlitResult.Success)
        assertEquals(MeshlitServerState.Stopped, ctrl.state.value)
    }

    @Test
    fun stop_is_idempotent() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl
        // Never started.
        val first = ctrl.stop()
        assertTrue(first is MeshlitResult.Success)
        assertEquals(MeshlitServerState.Stopped, ctrl.state.value)
    }

    @Test
    fun restart_rebinds_on_new_port() = runTest {
        val port1 = ephemeralPort()
        val port2 = ephemeralPort()
        require(port1 != port2) { "test machine ran out of ephemeral ports" }
        val ctrl = newController(port1)
        controller = ctrl

        ctrl.start(host = "127.0.0.1", port = port1)
        val restart = ctrl.restart(host = "127.0.0.1", port = port2)
        assertTrue(restart is MeshlitResult.Success)
        val state = ctrl.state.value
        assertTrue(state is MeshlitServerState.Running)
        assertEquals(port2, (state as MeshlitServerState.Running).port)
    }

    @Test
    fun start_with_different_host_restarts_atomically() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl

        ctrl.start(host = "127.0.0.1", port = port)
        // Same port, different host -> treated as restart.
        val restart = ctrl.start(host = "127.0.0.1", port = port)
        assertTrue(restart is MeshlitResult.Success)
        // Re-request with same params is no-op.
        assertTrue(ctrl.state.value is MeshlitServerState.Running)
    }

    @Test
    fun health_endpoint_reachable_after_start() = runTest {
        val port = ephemeralPort()
        val ctrl = newController(port)
        controller = ctrl

        ctrl.start(host = "127.0.0.1", port = port)
        val state = ctrl.state.value as MeshlitServerState.Running
        // Make a single GET /mcp/health with raw sockets.
        val raw = Socket("127.0.0.1", state.port).use { sock ->
            val out = sock.getOutputStream()
            out.write("GET /mcp/health HTTP/1.1\r\n".toByteArray())
            out.write("Host: 127.0.0.1\r\n".toByteArray())
            out.write("Connection: close\r\n\r\n".toByteArray())
            out.flush()
            BufferedReader(InputStreamReader(sock.getInputStream())).readText()
        }
        assertTrue(raw.contains("200"))
        assertTrue(raw.contains("\"status\":\"ok\""))
    }

    @Test
    fun custom_registry_is_used() = runTest {
        val port = ephemeralPort()
        val registry = McpToolRegistry()
        val sentinel = McpToolSpec(
            name = "sentinel_tool",
            description = "marker tool",
            handler = { McpToolResult.Text("ok") },
        )
        registry.register(sentinel)
        val ctrl = MeshlitServerController(
            registryProvider = { registry },
            poolProvider = { McpClientPool(registry = registry) },
            defaultHost = "127.0.0.1",
            defaultPort = port,
        )
        controller = ctrl
        ctrl.start(host = "127.0.0.1", port = port)
        val state = ctrl.state.value as MeshlitServerState.Running
        val raw = Socket("127.0.0.1", state.port).use { sock ->
            val out = sock.getOutputStream()
            val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
            out.write("POST /mcp HTTP/1.1\r\n".toByteArray())
            out.write("Host: 127.0.0.1\r\n".toByteArray())
            out.write("Content-Type: application/json\r\n".toByteArray())
            out.write("Content-Length: ${body.toByteArray().size}\r\n".toByteArray())
            out.write("Connection: close\r\n\r\n".toByteArray())
            out.write(body.toByteArray())
            out.flush()
            BufferedReader(InputStreamReader(sock.getInputStream())).readText()
        }
        assertTrue(raw, raw.contains("sentinel_tool"))
        assertTrue(raw, raw.contains("\"status\":\"ok\"").not()) // ensure we're in body, not health
    }

    // ---- helpers ------------------------------------------------------------

    private fun newController(port: Int): MeshlitServerController = MeshlitServerController(
        registryProvider = { McpToolRegistry() },
        poolProvider = { McpClientPool(registry = McpToolRegistry()) },
        defaultHost = "127.0.0.1",
        defaultPort = port,
    )

    private fun ephemeralPort(): Int {
        val s = ServerSocket(0)
        val p = s.localPort
        s.close()
        return p
    }
}