package com.meshlit.core.mcp.adapters

import com.meshlit.core.common.logger
import com.meshlit.core.mcp.McpHermesBridge
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.McpToolRequest
import com.meshlit.core.mcp.McpToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * Adapter that lets Meshlit consume an external Ollama server's
 * `/api/chat` endpoint as if it were a local MCP-aware model.
 *
 * The flow:
 *  1. Build the chat payload (system prompt describing the local
 *     MCP tools, user prompt, model name).
 *  2. POST to `<baseUrl>/api/chat`. The Ollama server streams
 *     NDJSON back; we read until `done: true`.
 *  3. The final `message.content` is parsed by [McpHermesBridge.parseToolCall].
 *     If a `<tool_call>` block is present, dispatch through the
 *     registry and return the tool result wrapped in the Ollama
 *     response shape (`{message: {role: "tool", content: "..."}}`).
 *
 * This adapter does not pull in any HTTP client — callers pass a
 * [HttpPoster] lambda so core-mcp stays free of OkHttp / Ktor.
 * Production wiring in `:app` supplies a Ktor-backed implementation.
 */
class OllamaAdapter(
    private val registry: McpToolRegistry,
    private val bridge: McpHermesBridge = McpHermesBridge(registry),
    private val baseUrl: String = "http://127.0.0.1:11434",
    private val model: String = "llama3.1",
    private val poster: HttpPoster = UnconfiguredPoster,
) {
    private val log = logger("OllamaAdapter")

    /** Caller-side hook so the unit tests can swap out network. */
    fun interface HttpPoster {
        suspend fun post(url: String, body: JsonObject): String
    }

    /** Chat entry point. Takes a user prompt; returns the model's
     *  final answer, with tool calls dispatched inline. */
    suspend fun chat(userPrompt: String): OllamaChatResult {
        val prompt = bridge.buildPrompt(userPrompt)
        val payload = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt.system)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt.user)
                })
            })
        }
        val body = poster.post("$baseUrl/api/chat", payload)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        val firstMessage = (parsed["message"] as? JsonObject) ?: return OllamaChatResult(
            text = "",
            toolCalls = emptyList(),
        )
        val content = (firstMessage["content"] as? JsonPrimitive)?.content.orEmpty()
        val call = bridge.parseToolCall(content)
        if (call == null) {
            return OllamaChatResult(text = content, toolCalls = emptyList())
        }
        val toolResult = registry.invoke(McpToolRequest(name = call.name, arguments = call.arguments))
        // Re-issue with the tool result folded into a follow-up message.
        val followUp = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", prompt.system)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt.user)
                })
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                })
                add(buildJsonObject {
                    put("role", "tool")
                    put("name", call.name)
                    put("content", renderToolResult(toolResult))
                })
            })
        }
        val finalBody = poster.post("$baseUrl/api/chat", followUp)
        val finalMsg = (kotlinx.serialization.json.Json.parseToJsonElement(finalBody).jsonObject
            .get("message") as? JsonObject)
        val finalText = (finalMsg?.get("content") as? JsonPrimitive)?.content.orEmpty()
        log.info(
            "ollama.chat.done",
            "completed",
            mapOf("model" to model, "tool" to call.name),
        )
        return OllamaChatResult(
            text = finalText,
            toolCalls = listOf(OllamaToolCall(name = call.name, result = toolResult)),
        )
    }

    private fun renderToolResult(result: McpToolResult): String = when (result) {
        is McpToolResult.Text -> result.text
        is McpToolResult.Json -> result.value.toString()
        is McpToolResult.Error -> "${result.code.wireValue}: ${result.message}"
    }
}

data class OllamaChatResult(
    val text: String,
    val toolCalls: List<OllamaToolCall>,
)

data class OllamaToolCall(
    val name: String,
    val result: McpToolResult,
)

/** Sentinel — produces an error if the app didn't inject a real poster. */
private object UnconfiguredPoster : OllamaAdapter.HttpPoster {
    override suspend fun post(url: String, body: JsonObject): String =
        error("OllamaAdapter.HttpPoster not configured — wire one in from :app")
}