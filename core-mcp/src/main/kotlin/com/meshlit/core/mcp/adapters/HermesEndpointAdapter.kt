package com.meshlit.core.mcp.adapters

import com.meshlit.core.common.logger
import com.meshlit.core.mcp.McpHermesBridge
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.McpToolRequest
import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Adapter that lets Meshlit consume a NousResearch Hermes self-hosted
 * endpoint as a Tool-role model. Hermes self-hosted servers expose
 * an OpenAI-compatible `/v1/chat/completions` route — we speak that
 * wire format and translate tool calls back into [McpToolRequest].
 *
 * The Hermes chat templates emit `<tool_call>{"name":"...",
 * "arguments":{...}}</tool_call>` blocks; the bridge already parses
 * that. The adapter differs from [OllamaAdapter] only in the URL
 * shape and the request body fields (`temperature`, `top_p`,
 * `max_tokens` are Hermes-specific knobs we surface directly).
 */
class HermesEndpointAdapter(
    private val registry: McpToolRegistry,
    private val bridge: McpHermesBridge = McpHermesBridge(registry),
    private val baseUrl: String = "https://hermes.nousresearch.ai/v1",
    private val model: String = "Hermes-3-Llama-3.1-70B",
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 1024,
    private val poster: OllamaAdapter.HttpPoster = UnconfiguredHermesPoster,
) {
    private val log = logger("HermesEndpointAdapter")

    suspend fun chat(userPrompt: String): HermesChatResult {
        val prompt = bridge.buildPrompt(userPrompt)
        val firstReq = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
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
        val body = poster.post("$baseUrl/chat/completions", firstReq)
        val content = extractAssistantContent(body)
        val call = bridge.parseToolCall(content)
        if (call == null) {
            return HermesChatResult(text = content, toolCalls = emptyList())
        }
        val toolResult = registry.invoke(McpToolRequest(name = call.name, arguments = call.arguments))
        val secondReq = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
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
        val finalBody = poster.post("$baseUrl/chat/completions", secondReq)
        val finalText = extractAssistantContent(finalBody)
        log.info(
            "hermes.chat.done",
            "completed",
            mapOf("model" to model, "tool" to call.name),
        )
        return HermesChatResult(
            text = finalText,
            toolCalls = listOf(HermesToolCall(name = call.name, result = toolResult)),
        )
    }

    private fun extractAssistantContent(body: String): String {
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
        val choices = parsed["choices"] as? JsonArray ?: return ""
        val first = choices.firstOrNull() as? JsonObject ?: return ""
        val message = first["message"] as? JsonObject ?: return ""
        return (message["content"] as? JsonPrimitive)?.content.orEmpty()
    }

    private fun renderToolResult(result: McpToolResult): String = when (result) {
        is McpToolResult.Text -> result.text
        is McpToolResult.Json -> result.value.toString()
        is McpToolResult.Error -> "${result.code.wireValue}: ${result.message}"
    }
}

data class HermesChatResult(
    val text: String,
    val toolCalls: List<HermesToolCall>,
)

data class HermesToolCall(
    val name: String,
    val result: McpToolResult,
)

private object UnconfiguredHermesPoster : OllamaAdapter.HttpPoster {
    override suspend fun post(url: String, body: JsonObject): String =
        error("HermesEndpointAdapter.HttpPoster not configured — wire one in from :app")
}