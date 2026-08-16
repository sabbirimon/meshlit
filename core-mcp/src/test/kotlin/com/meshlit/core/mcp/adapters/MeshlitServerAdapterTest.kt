package com.meshlit.core.mcp.adapters

import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Smoke tests for the MeshlitServerAdapter. We boot the server on
 * `127.0.0.1:0` (NanoHTTPD picks a free port), speak one HTTP
 * request at a time with plain sockets, and tear it down.
 *
 * The goal is just to prove the wire surface: a JSON-RPC envelope
 * in -> a JSON-RPC envelope out, plus the /mcp/health probe.
 */
class MeshlitServerAdapterTest {

    private var server: MeshlitServerAdapter? = null
    private var host: String = "127.0.0.1"
    private var port: Int = 0

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    @Test
    fun health_endpoint_returns_ok() {
        boot { reg ->
            reg.register(McpToolSpec(name = "x", description = "x", handler = { McpToolResult.Text("x") }))
        }
        val raw = httpGet("/mcp/health")
        assertEquals(200, statusOf(raw))
        assertTrue(bodyOf(raw).contains("\"status\":\"ok\""))
    }

    @Test
    fun mcp_endpoint_handles_initialize() {
        boot { reg ->
            reg.register(McpToolSpec(name = "x", description = "x", handler = { McpToolResult.Text("x") }))
        }
        val envelope = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
        val raw = httpPost("/mcp", envelope)
        assertEquals(200, statusOf(raw))
        val body = bodyOf(raw)
        assertTrue(body, body.contains("\"serverInfo\""))
        assertTrue(body, body.contains("\"meshlit-mcp\""))
    }

    @Test
    fun mcp_endpoint_handles_tools_call() {
        boot { reg ->
            reg.register(McpToolSpec(
                name = "echo",
                description = "echo msg",
                inputSchema = objectSchema(
                    properties = mapOf("msg" to stringProp()),
                    required = listOf("msg"),
                ),
                handler = { args ->
                    val m = (args as JsonObject)["msg"] as JsonPrimitive
                    McpToolResult.Text(m.content)
                },
            ))
        }
        val envelope = """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"echo","arguments":{"msg":"hi"}}}"""
        val raw = httpPost("/mcp", envelope)
        assertEquals(200, statusOf(raw))
        val body = bodyOf(raw)
        assertTrue(body, body.contains("\"text\":\"hi\""))
    }

    @Test
    fun unknown_path_returns_404() {
        boot { /* no tools */ }
        val raw = httpGet("/mcp/whatever")
        assertEquals(404, statusOf(raw))
    }

    // --- helpers -------------------------------------------------------------

    private fun boot(setup: (McpToolRegistry) -> Unit) {
        val reg = McpToolRegistry()
        setup(reg)
        // Pick a free port by binding a temp socket and reading it back.
        val picker = java.net.ServerSocket(0)
        port = picker.localPort
        picker.close()
        val adapter = MeshlitServerAdapter(reg, port = port, host = host)
        adapter.start()
        port = adapter.listeningPort
        waitForListening(port)
        server = adapter
    }

    private fun waitForListening(port: Int, attempts: Int = 50) {
        repeat(attempts) {
            try {
                java.net.Socket(host, port).use { /* connected */ }
                return
            } catch (_: Throwable) {
                Thread.sleep(20)
            }
        }
        error("server did not start on $host:$port after ${attempts * 20}ms")
    }

    private fun httpGet(path: String): String = Socket(host, port).use { sock ->
        val raw = sock.getOutputStream()
        raw.write(("GET $path HTTP/1.1\r\n").toByteArray())
        raw.write(("Host: $host:$port\r\n").toByteArray())
        raw.write("Connection: close\r\n\r\n".toByteArray())
        raw.flush()
        BufferedReader(InputStreamReader(sock.getInputStream())).readText()
    }

    private fun httpPost(path: String, body: String): String = Socket(host, port).use { sock ->
        val raw = sock.getOutputStream()
        raw.write("POST $path HTTP/1.1\r\n".toByteArray())
        raw.write("Host: $host:$port\r\n".toByteArray())
        raw.write("Content-Type: application/json\r\n".toByteArray())
        raw.write("Content-Length: ${body.toByteArray().size}\r\n".toByteArray())
        raw.write("Connection: close\r\n\r\n".toByteArray())
        raw.write(body.toByteArray())
        raw.flush()
        BufferedReader(InputStreamReader(sock.getInputStream())).readText()
    }

    private fun statusOf(raw: String): Int {
        val statusLine = raw.lineSequence().firstOrNull().orEmpty()
        val parts = statusLine.split(" ")
        return parts.getOrNull(1)?.toInt() ?: -1
    }

    private fun bodyOf(raw: String): String {
        val marker = "\r\n\r\n"
        val idx = raw.indexOf(marker)
        return if (idx >= 0) raw.substring(idx + marker.length) else raw
    }
}
