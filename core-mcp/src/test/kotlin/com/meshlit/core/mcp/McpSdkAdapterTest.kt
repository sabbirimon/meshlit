package com.meshlit.core.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSdkAdapterTest {

    @Test
    fun initialize_returns_server_info() = runBlocking {
        val reg = McpToolRegistry()
        val adapter = McpSdkAdapter(reg)
        val resp = adapter.handleRpc(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "1")
            put("method", "initialize")
        })
        val result = resp["result"] as JsonObject
        val serverInfo = result["serverInfo"] as JsonObject
        assertEquals("meshlit-mcp", (serverInfo["name"] as JsonPrimitive).content)
    }

    @Test
    fun tools_list_returns_all_registered() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(name = "alpha", description = "a", handler = { McpToolResult.Text("") }))
        reg.register(McpToolSpec(name = "beta", description = "b", handler = { McpToolResult.Text("") }))
        val adapter = McpSdkAdapter(reg)
        val resp = adapter.handleRpc(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "2")
            put("method", "tools/list")
        })
        val result = resp["result"] as JsonObject
        val tools = result["tools"] as kotlinx.serialization.json.JsonArray
        assertEquals(2, tools.size)
        val names = tools.map { (it as JsonObject)["name"] }
            .map { (it as JsonPrimitive).content }
            .toSet()
        assertTrue("alpha" in names)
        assertTrue("beta" in names)
    }

    @Test
    fun tools_call_invokes_named_tool() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "echo",
            description = "echo",
            handler = { args -> McpToolResult.Text(args.toString()) },
        ))
        val adapter = McpSdkAdapter(reg)
        val resp = adapter.handleRpc(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "3")
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", "echo")
                put("arguments", buildJsonObject { put("msg", "yo") })
            })
        })
        val result = resp["result"] as JsonObject
        val content = result["content"] as JsonObject
        assertTrue((content["text"] as JsonPrimitive).content.contains("\"msg\":\"yo\""))
        assertEquals(false, (result["isError"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun unknown_method_returns_method_not_found() = runBlocking {
        val reg = McpToolRegistry()
        val adapter = McpSdkAdapter(reg)
        val resp = adapter.handleRpc(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "4")
            put("method", "weird/method")
        })
        assertNotNull(resp["error"])
        val error = resp["error"] as JsonObject
        assertEquals(-32601, (error["code"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun ping_returns_empty_object() = runBlocking {
        val reg = McpToolRegistry()
        val adapter = McpSdkAdapter(reg)
        val resp = adapter.handleRpc(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "5")
            put("method", "ping")
        })
        val result = resp["result"] as JsonObject
        assertEquals(0, result.size)
    }
}