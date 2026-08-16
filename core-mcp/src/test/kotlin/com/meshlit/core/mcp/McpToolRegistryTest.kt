package com.meshlit.core.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolRegistryTest {

    @Test
    fun register_and_invoke_dispatches_to_handler() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "echo",
            description = "echo args back",
            handler = { args -> McpToolResult.Text(args.toString()) },
        ))
        val result = reg.invoke(McpToolRequest(name = "echo", arguments = buildJsonObject {
            put("msg", "hi")
        }))
        assertTrue(result is McpToolResult.Text)
        assertTrue((result as McpToolResult.Text).text.contains("\"msg\":\"hi\""))
    }

    @Test
    fun unknown_tool_returns_not_found() = runBlocking {
        val reg = McpToolRegistry()
        val result = reg.invoke(McpToolRequest(name = "nope"))
        assertTrue(result is McpToolResult.Error)
        assertEquals(
            McpToolResult.ErrorCode.NOT_FOUND,
            (result as McpToolResult.Error).code,
        )
    }

    @Test
    fun required_args_missing_returns_invalid() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "needs_path",
            description = "needs a path arg",
            inputSchema = objectSchema(
                properties = mapOf("path" to stringProp()),
                required = listOf("path"),
            ),
            handler = { McpToolResult.Text("ok") },
        ))
        val result = reg.invoke(McpToolRequest(
            name = "needs_path",
            arguments = JsonObject(emptyMap()),
        ))
        assertTrue(result is McpToolResult.Error)
        assertEquals(
            McpToolResult.ErrorCode.INVALID_ARGS,
            (result as McpToolResult.Error).code,
        )
    }

    @Test
    fun required_args_present_passes_validation() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "needs_path",
            description = "needs a path arg",
            inputSchema = objectSchema(
                properties = mapOf("path" to stringProp()),
                required = listOf("path"),
            ),
            handler = { McpToolResult.Text("ok") },
        ))
        val result = reg.invoke(McpToolRequest(
            name = "needs_path",
            arguments = buildJsonObject { put("path", "/tmp") },
        ))
        assertTrue(result is McpToolResult.Text)
    }

    @Test
    fun handler_exception_becomes_exec_failed_error() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "boom",
            description = "always throws",
            handler = { _ -> throw IllegalStateException("kaboom") },
        ))
        val result = reg.invoke(McpToolRequest(name = "boom"))
        assertTrue(result is McpToolResult.Error)
        assertEquals(
            McpToolResult.ErrorCode.EXEC_FAILED,
            (result as McpToolResult.Error).code,
        )
        assertTrue((result as McpToolResult.Error).message.contains("kaboom"))
    }

    @Test
    fun list_returns_tools_sorted_by_name() {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(name = "zeta", description = "z", handler = { McpToolResult.Text("z") }))
        reg.register(McpToolSpec(name = "alpha", description = "a", handler = { McpToolResult.Text("a") }))
        reg.register(McpToolSpec(name = "mu", description = "m", handler = { McpToolResult.Text("m") }))
        val names = reg.list().map { it.name }
        assertEquals(listOf("alpha", "mu", "zeta"), names)
    }

    @Test
    fun unregister_removes_tool() {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(name = "x", description = "", handler = { McpToolResult.Text("") }))
        assertNotNull(reg.get("x"))
        reg.unregister("x")
        assertNull(reg.get("x"))
    }

    @Test
    fun toWireResponse_text_wraps_content() {
        val reg = McpToolRegistry()
        val r = reg.toWireResponse(McpToolResult.Text("hello"))
        val content = (r["content"] as JsonObject)
        assertEquals("text", (content["type"] as JsonPrimitive).content)
        assertEquals("hello", (content["text"] as JsonPrimitive).content)
        assertEquals(false, (r["isError"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun toWireResponse_error_marks_isError_true() {
        val reg = McpToolRegistry()
        val r = reg.toWireResponse(
            McpToolResult.Error(McpToolResult.ErrorCode.NOT_FOUND, "missing"),
        )
        assertEquals(true, (r["isError"] as JsonPrimitive).content.toBoolean())
        val content = r["content"] as JsonObject
        assertTrue((content["text"] as JsonPrimitive).content.contains("not_found"))
    }

    @Test
    fun null_args_is_accepted() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(name = "zero", description = "", handler = { McpToolResult.Text("ok") }))
        val result = reg.invoke(McpToolRequest(name = "zero", arguments = JsonNull))
        assertTrue(result is McpToolResult.Text)
    }

    @Test
    fun registerAll_registers_each() {
        val reg = McpToolRegistry()
        reg.registerAll(listOf(
            McpToolSpec(name = "a", description = "", handler = { McpToolResult.Text("a") }),
            McpToolSpec(name = "b", description = "", handler = { McpToolResult.Text("b") }),
        ))
        assertEquals(2, reg.list().size)
    }
}