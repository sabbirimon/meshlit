package com.meshlit.core.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Bridge between the [McpToolRegistry] and a NousResearch Hermes
 * model running on the local inference coordinator.
 *
 * The flow:
 *
 *  1. Caller hands a user prompt to [buildPrompt].
 *  2. Hermes is asked to respond — either with `<tool_call>{...}</tool_call>`
 *     or a plain text answer.
 *  3. [parseToolCall] extracts the first tool call (if any) and the
 *     free-text fallback.
 *  4. The caller dispatches [HermesToolCall] through the registry.
 *  5. The result is folded into a follow-up prompt by
 *     [buildToolResultPrompt] so Hermes can produce a final answer.
 *
 * This class is **pure**: it doesn't call the inference coordinator
 * itself. The app glues the pieces together (Hermes model loaded →
 * coordinator → inference result string → `parseToolCall` → registry
 * dispatch → `buildToolResultPrompt` → coordinator → final answer).
 *
 * The bridge is what makes a local GGUF usable as a Tool-role model
 * without needing the agent to know about MCP at all — it just sees
 * `<tool_call>{...}</tool_call>` and plain text, which is the format
 * the Hermes chat templates were tuned for.
 */
class McpHermesBridge(
    private val registry: McpToolRegistry,
) {
    /** Compose the prompt the LLM sees at conversation start. The
     *  system prompt describes available tools in Hermes'
     *  function-calling format; the user prompt is appended verbatim. */
    fun buildPrompt(userPrompt: String): HermesPrompt {
        val tools = registry.list()
        val systemPrompt = buildSystemPrompt(tools)
        return HermesPrompt(
            system = systemPrompt,
            user = userPrompt,
        )
    }

    /** Compose the second-turn prompt after a tool call has been
     *  resolved. The tool result is appended as a `<tool_response>` so
     *  Hermes can continue the conversation with the new evidence. */
    fun buildToolResultPrompt(
        initialPrompt: String,
        toolName: String,
        toolResult: McpToolResult,
    ): HermesPrompt {
        val toolResultText = when (toolResult) {
            is McpToolResult.Text -> toolResult.text
            is McpToolResult.Json -> toolResult.value.toString()
            is McpToolResult.Error -> "${toolResult.code.wireValue}: ${toolResult.message}"
        }
        val systemPrompt = buildSystemPrompt(registry.list())
        val user = buildString {
            append(initialPrompt)
            append("\n\n")
            append("<tool_call>\n")
            append("{\"name\":\"").append(toolName).append("\"}\n")
            append("</tool_call>\n")
            append("<tool_response>\n")
            append(toolResultText)
            append("\n</tool_response>")
        }
        return HermesPrompt(system = systemPrompt, user = user)
    }

    /** Extract a [HermesToolCall] from the LLM's raw response.
     *  Returns `null` when the model produced only text — the caller
     *  treats that as a final answer. */
    fun parseToolCall(rawResponse: String): HermesToolCall? {
        // Hermes emits <tool_call>...</tool_call> blocks. Some chat
        // templates also emit <tool_response>...</tool_response>; we
        // ignore those here because parseToolCall only sees the
        // model's first-pass output.
        val start = rawResponse.indexOf("<tool_call>")
        if (start < 0) return null
        val end = rawResponse.indexOf("</tool_call>", start)
        if (end < 0) return null
        val json = rawResponse.substring(start + "<tool_call>".length, end).trim()
        if (json.isBlank()) return null
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
            val obj = element as? JsonObject ?: return@runCatching null
            val name = (obj["name"] as? JsonPrimitive)?.content ?: return@runCatching null
            val arguments = obj["arguments"] ?: JsonObject(emptyMap())
            HermesToolCall(name = name, arguments = arguments)
        }.getOrNull()
    }

    private fun buildSystemPrompt(tools: List<McpToolSpec>): String = buildString {
        append("You are Meshlit, a Tool-role assistant running on a local device. ")
        append("You have access to the following MCP tools:\n\n")
        if (tools.isEmpty()) {
            append("(no tools registered)\n")
            return@buildString
        }
        tools.forEach { tool ->
            append("Name: ").append(tool.name).append('\n')
            append("Description: ").append(tool.description).append('\n')
            append("Args: ").append(tool.inputSchema.toString()).append('\n')
            append("Origin: ").append(tool.origin.name).append("\n\n")
        }
        append("When you need a tool, respond with EXACTLY ONE <tool_call>")
        append("{\"name\":\"<tool>\",\"arguments\":{...}}</tool_call> block. ")
        append("Otherwise respond in plain text.")
    }
}

/** Two-string prompt as expected by most chat templates. */
data class HermesPrompt(
    val system: String,
    val user: String,
)

/** Parsed tool-call intent extracted from the model's first-pass
 *  output. The caller dispatches `name` + `arguments` through the
 *  registry. */
data class HermesToolCall(
    val name: String,
    val arguments: JsonElement,
)
