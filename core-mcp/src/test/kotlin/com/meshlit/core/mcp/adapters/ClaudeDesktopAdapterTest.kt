package com.meshlit.core.mcp.adapters

import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the Claude Desktop stdio adapter. The reader and
 * writer are replaced with in-memory fakes so we can verify the
 * round-trip without a real stdin/stdout pair.
 *
 *   - One request line in -> one response line out.
 *   - Notifications (no `id`) emit no response.
 *   - Parse errors are surfaced as a JSON-RPC -32700 envelope.
 */
class ClaudeDesktopAdapterTest {

    @Test
    fun handleLine_round_trips_initialize() {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(name = "echo", description = "echo", handler = { McpToolResult.Text("hi") }))
        val adapter = ClaudeDesktopAdapter(reg, reader = { null }, writer = { })
        val resp = adapter.handleLine(
            """{"jsonrpc":"2.0","id":1,"method":"initialize"}""",
        )
        assertNotNull(resp)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(resp!!).jsonObject
        assertEquals("2.0", (parsed["jsonrpc"] as JsonPrimitive).content)
        val result = parsed["result"] as JsonObject
        val serverInfo = result["serverInfo"] as JsonObject
        assertEquals("meshlit-mcp", (serverInfo["name"] as JsonPrimitive).content)
    }

    @Test
    fun handleLine_returns_null_for_notification() {
        val reg = McpToolRegistry()
        val adapter = ClaudeDesktopAdapter(reg, reader = { null }, writer = { })
        // Notifications carry no `id` and we should not respond.
        val resp = adapter.handleLine(
            """{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"reason":"user"}}""",
        )
        assertNull("notifications must not produce a response", resp)
    }

    @Test
    fun handleLine_returns_parse_error_for_invalid_json() {
        val reg = McpToolRegistry()
        val adapter = ClaudeDesktopAdapter(reg, reader = { null }, writer = { })
        val resp = adapter.handleLine("this is not json")
        assertNotNull(resp)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(resp!!).jsonObject
        val err = parsed["error"] as JsonObject
        assertEquals(-32700, (err["code"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun start_processes_multiple_lines_in_order() {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "echo",
            description = "echo",
            inputSchema = objectSchema(properties = mapOf("msg" to stringProp()), required = listOf("msg")),
            handler = { args ->
                val m = (args as JsonObject)["msg"] as JsonPrimitive
                McpToolResult.Text(m.content)
            },
        ))
        val inputs = arrayOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize"}""",
            """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""",
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"msg":"yo"}}}""",
            null, // EOF
        )
        val idx = java.util.concurrent.atomic.AtomicInteger(0)
        val outputs = java.util.Collections.synchronizedList(mutableListOf<String>())
        val adapter = ClaudeDesktopAdapter(
            registry = reg,
            reader = { inputs.getOrNull(idx.getAndIncrement()) },
            writer = { outputs += it },
        )
        adapter.start()
        // Give the IO dispatcher a moment to drain the loop.
        Thread.sleep(500)
        adapter.stop()
        assertEquals(3, outputs.size)
        val ids = outputs.toList().map { line ->
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(line).jsonObject
            (parsed["id"] as JsonPrimitive).content
        }
        assertEquals(listOf("1", "2", "3"), ids)
        // Last response should be the echo result.
        val last = kotlinx.serialization.json.Json.parseToJsonElement(outputs.last()).jsonObject
        val result = last["result"] as JsonObject
        val content = result["content"] as JsonObject
        assertTrue((content["text"] as JsonPrimitive).content.contains("yo"))
    }
}
