package com.meshlit.core.mcp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpHermesBridgeTest {

    @Test
    fun buildPrompt_includes_tool_names() {
        val reg = McpToolRegistry()
        reg.register(McpToolSpec(
            name = "weather",
            description = "Get weather for a city",
            inputSchema = objectSchema(
                properties = mapOf("city" to stringProp()),
                required = listOf("city"),
            ),
            handler = { McpToolResult.Text("sunny") },
        ))
        val bridge = McpHermesBridge(reg)
        val prompt = bridge.buildPrompt("What is the weather in Paris?")
        assertTrue(prompt.system.contains("weather"))
        assertTrue(prompt.user.contains("Paris"))
    }

    @Test
    fun parseToolCall_extracts_name_and_arguments() {
        val reg = McpToolRegistry()
        val bridge = McpHermesBridge(reg)
        val raw = "I should call the tool.\n<tool_call>{\"name\":\"weather\",\"arguments\":{\"city\":\"Paris\"}}</tool_call>"
        val call = bridge.parseToolCall(raw)
        assertNotNull(call)
        assertEquals("weather", call!!.name)
        assertEquals(
            "Paris",
            (call.arguments as kotlinx.serialization.json.JsonObject)["city"]
                ?.let { (it as JsonPrimitive).content },
        )
    }

    @Test
    fun parseToolCall_returns_null_when_no_block() {
        val reg = McpToolRegistry()
        val bridge = McpHermesBridge(reg)
        assertNull(bridge.parseToolCall("just text, no tool call"))
    }

    @Test
    fun parseToolCall_returns_null_for_malformed_json() {
        val reg = McpToolRegistry()
        val bridge = McpHermesBridge(reg)
        assertNull(bridge.parseToolCall("<tool_call>not json</tool_call>"))
    }

    @Test
    fun buildToolResultPrompt_includes_result_text() {
        val reg = McpToolRegistry()
        val bridge = McpHermesBridge(reg)
        val prompt = bridge.buildToolResultPrompt(
            initialPrompt = "What's the weather?",
            toolName = "weather",
            toolResult = McpToolResult.Text("sunny"),
        )
        assertTrue(prompt.user.contains("<tool_response>"))
        assertTrue(prompt.user.contains("sunny"))
    }
}