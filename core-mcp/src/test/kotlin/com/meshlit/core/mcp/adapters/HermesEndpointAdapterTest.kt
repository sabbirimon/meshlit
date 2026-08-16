package com.meshlit.core.mcp.adapters

import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the Hermes self-hosted endpoint adapter. The
 * HttpPoster is stubbed so we never reach the network. We verify:
 *   - Request body includes temperature, max_tokens, system+user.
 *   - Tool calls are parsed and dispatched.
 *   - Final text is returned verbatim.
 */
class HermesEndpointAdapterTest {

    @Test
    fun chat_uses_chat_completions_url_and_temperature() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "lookup",
            description = "look something up",
            inputSchema = objectSchema(properties = mapOf("q" to stringProp()), required = listOf("q")),
            handler = { McpToolResult.Text("42") },
        ))
        var capturedUrl: String? = null
        var capturedBody: JsonObject? = null
        val poster = OllamaAdapter.HttpPoster { url, body ->
            capturedUrl = url
            capturedBody = body
            """{"choices":[{"message":{"role":"assistant","content":"<tool_call>{\"name\":\"lookup\",\"arguments\":{\"q\":\"meaning of life\"}}</tool_call>"}}]}"""
        }
        val adapter = HermesEndpointAdapter(
            registry = reg,
            temperature = 0.3,
            maxTokens = 256,
            poster = poster,
        )
        adapter.chat("meaning?")
        assertNotNull(capturedUrl)
        assertTrue("url should be chat completions", capturedUrl!!.endsWith("/chat/completions"))
        assertNotNull(capturedBody)
        val body = capturedBody!!
        assertEquals(0.3, (body["temperature"] as JsonPrimitive).content.toDouble(), 0.001)
        assertEquals(256, (body["max_tokens"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun chat_with_tool_call_returns_final_text() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "lookup",
            description = "lookup",
            inputSchema = objectSchema(properties = mapOf("q" to stringProp()), required = listOf("q")),
            handler = { McpToolResult.Text("42") },
        ))
        var n = 0
        val poster = OllamaAdapter.HttpPoster { _, _ ->
            n += 1
            if (n == 1) {
                """{"choices":[{"message":{"role":"assistant","content":"<tool_call>{\"name\":\"lookup\",\"arguments\":{\"q\":\"life\"}}</tool_call>"}}]}"""
            } else {
                """{"choices":[{"message":{"role":"assistant","content":"the answer is 42"}}]}"""
            }
        }
        val adapter = HermesEndpointAdapter(reg, poster = poster)
        val result = adapter.chat("?")
        assertEquals(2, n)
        assertEquals("the answer is 42", result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("lookup", result.toolCalls[0].name)
    }

    @Test
    fun chat_without_tool_call_returns_empty_tool_list() = runBlocking {
        val reg = McpToolRegistry()
        val poster = OllamaAdapter.HttpPoster { _, _ ->
            """{"choices":[{"message":{"role":"assistant","content":"hello"}}]}"""
        }
        val adapter = HermesEndpointAdapter(reg, poster = poster)
        val result = adapter.chat("hi")
        assertEquals("hello", result.text)
        assertTrue(result.toolCalls.isEmpty())
    }
}
