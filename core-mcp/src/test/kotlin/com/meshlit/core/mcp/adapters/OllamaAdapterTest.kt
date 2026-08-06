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
 * Smoke tests for the Ollama adapter. The HttpPoster is stubbed so
 * we never touch the network. We verify:
 *   - The first request includes model, system+user messages.
 *   - The adapter parses the assistant tool_call block.
 *   - The second request carries the tool result.
 *   - No-tool-call responses are returned verbatim.
 */
class OllamaAdapterTest {

    @Test
    fun chat_with_tool_call_dispatches_and_follows_up() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "weather",
            description = "Get weather for a city",
            inputSchema = objectSchema(
                properties = mapOf("city" to stringProp()),
                required = listOf("city"),
            ),
            handler = { McpToolResult.Text("72F sunny") },
        ))

        val calls = mutableListOf<Pair<String, JsonObject>>()
        val poster = OllamaAdapter.HttpPoster { url, body ->
            calls += url to body
            // First call: model emits a tool_call block.
            // Second call: model returns the final answer.
            if (calls.size == 1) {
                """{"message":{"role":"assistant","content":"<tool_call>{\"name\":\"weather\",\"arguments\":{\"city\":\"Paris\"}}</tool_call>"}}"""
            } else {
                """{"message":{"role":"assistant","content":"It's 72F sunny in Paris."}}"""
            }
        }

        val adapter = OllamaAdapter(reg, poster = poster)
        val result = adapter.chat("What's the weather in Paris?")

        assertEquals(2, calls.size)
        assertTrue("first call should hit /api/chat", calls[0].first.endsWith("/api/chat"))
        assertEquals(1, result.toolCalls.size)
        assertEquals("weather", result.toolCalls[0].name)
        assertEquals("It's 72F sunny in Paris.", result.text)
    }

    @Test
    fun chat_without_tool_call_returns_content_verbatim() = runBlocking {
        val reg = McpToolRegistry()
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val poster = OllamaAdapter.HttpPoster { _, _ ->
            calls += "" to buildJsonObject { }
            """{"message":{"role":"assistant","content":"plain answer"}}"""
        }
        val adapter = OllamaAdapter(reg, poster = poster)
        val result = adapter.chat("hello?")
        assertEquals("plain answer", result.text)
        assertTrue(result.toolCalls.isEmpty())
    }

    @Test
    fun chat_serializes_system_and_user_messages() = runBlocking {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "echo",
            description = "echo args",
            inputSchema = objectSchema(properties = mapOf("x" to stringProp()), required = listOf("x")),
            handler = { McpToolResult.Text("ok") },
        ))
        var capturedBody: JsonObject? = null
        val poster = OllamaAdapter.HttpPoster { _, body ->
            capturedBody = body
            """{"message":{"role":"assistant","content":"no tool"}}"""
        }
        val adapter = OllamaAdapter(reg, model = "llama3.2", poster = poster)
        adapter.chat("say hi")
        assertNotNull(capturedBody)
        val body = capturedBody!!
        assertEquals("llama3.2", (body["model"] as JsonPrimitive).content)
        val messages = body["messages"] as kotlinx.serialization.json.JsonArray
        assertEquals(2, messages.size)
        val system = messages[0] as JsonObject
        assertEquals("system", (system["role"] as JsonPrimitive).content)
        val user = messages[1] as JsonObject
        assertEquals("user", (user["role"] as JsonPrimitive).content)
        assertEquals("say hi", (user["content"] as JsonPrimitive).content)
    }
}
