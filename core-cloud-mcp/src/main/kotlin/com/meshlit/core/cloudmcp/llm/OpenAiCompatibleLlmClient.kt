package com.meshlit.core.cloudmcp.llm

import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.McpTool
import com.meshlit.core.cloudmcp.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Streaming client for any **OpenAI-compatible**
 * `/v1/chat/completions` endpoint. The wire format is identical
 * to OpenAI's chat-completions API — most third-party gateways
 * (OpenRouter, Together, Groq, Ollama, LM Studio, vLLM,
 * NaraRouter) implement it without changes.
 *
 * The legacy [NaraRouterClient] is preserved for callers that
 * hard-code the NaraRouter slug enum; this class is the
 * forward-compatible form that takes a free-form model slug.
 *
 * Each SSE frame is one chunk of the response — `delta.content`
 * for text, `delta.tool_calls[]` for streamed tool invocations.
 *
 * Usage:
 * ```
 * val client = OpenAiCompatibleLlmClient(
 *     httpClient,
 *     baseUrl = "https://openrouter.ai/api",
 *     apiKey = "sk-…",
 *     model = "anthropic/claude-4.5-sonnet",
 * )
 * client.chatCompletions(
 *     providerId = "user-llm",
 *     messages = listOf(OpenAIMessage("user", "List my EC2 instances")),
 *     tools = toolRegistry.ordered(),
 * ).collect { chunk -> … }
 * ```
 *
 * The client never invokes tools itself — it streams chunks up
 * to the agent loop, which dispatches [McpEvent.ToolCall] back
 * to [com.meshlit.core.cloudmcp.CloudMcpCoordinator.callTool].
 */
class OpenAiCompatibleLlmClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Stream a chat completion. The flow emits one [LlmChunk] per
     * SSE frame; the terminal frame is [LlmChunk.Done].
     *
     * [providerId] is the agent-loop's "owner" provider — usually
     * "user-llm" when the configured LLM is a user-supplied
     * OpenAI-compatible endpoint, or a synthetic "nara-…" id when
     * the cloud LLM itself isn't a registered
     * [com.meshlit.core.cloudmcp.ProviderKind].
     */
    fun chatCompletions(
        providerId: String,
        messages: List<OpenAIMessage>,
        tools: List<McpTool>,
    ): Flow<LlmChunk> = flow {
        val requestBody = OpenAIChatRequest(
            model = model,
            messages = messages,
            tools = tools.map { tool ->
                OpenAITool(
                    function = OpenAIToolFunction(
                        name = tool.name,
                        description = tool.description,
                        parameters = tool.inputSchema,
                    ),
                )
            },
            stream = true,
        )

        val mediaType = "application/json".toMediaType()
        val url = baseUrl.trimEnd('/') + "/v1/chat/completions"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(OpenAIChatRequest.serializer(), requestBody)
                .toRequestBody(mediaType))
            .build()

        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(LlmChunk.Error(
                        providerId = providerId,
                        message = "LLM HTTP ${response.code}: ${response.message}",
                    ))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(LlmChunk.Error(providerId, message = "empty body"))
                    return@flow
                }
                val parser = SseParser()
                val pendingCalls = mutableMapOf<Int, PendingToolCall>()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    parser.feed(line).forEach { ev ->
                        handleFrame(providerId, ev.data, pendingCalls)?.let { emit(it) }
                    }
                }
                parser.flush()?.let { ev ->
                    handleFrame(providerId, ev.data, pendingCalls)?.let { emit(it) }
                }
                emit(LlmChunk.Done(providerId))
            }
        } catch (e: IOException) {
            emit(LlmChunk.Error(providerId, message = e.message ?: "network error"))
        }
    }.flowOn(Dispatchers.IO)

    private fun handleFrame(
        providerId: String,
        data: String,
        pendingCalls: MutableMap<Int, PendingToolCall>,
    ): LlmChunk? {
        if (data.isBlank() || data == "[DONE]") return null
        val root: JsonObject = try {
            json.parseToJsonElement(data).jsonObject
        } catch (e: IOException) {
            return LlmChunk.Error(providerId, message = "malformed frame: ${e.message}")
        }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val delta = choice["delta"]?.jsonObject ?: return null

        // 1. Text content delta.
        val text = delta["content"]?.jsonPrimitive?.content
        if (!text.isNullOrEmpty()) {
            return LlmChunk.Text(providerId, text, isFinal = false)
        }

        // 2. Tool-call deltas. Each frame may carry one or more
        // entries in `tool_calls[]`; we accumulate by `index`.
        val toolCallNodes = delta["tool_calls"]?.jsonArray ?: return null
        toolCallNodes.forEach { node ->
            val obj = node.jsonObject
            val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val id = obj["id"]?.jsonPrimitive?.content
            val type = obj["type"]?.jsonPrimitive?.content
            val function = obj["function"]?.jsonObject
            val callIdFromApi = id
            val pending = pendingCalls.getOrPut(index) {
                PendingToolCall(callId = callIdFromApi ?: "llm-$index")
            }
            if (callIdFromApi != null) pending.callId = callIdFromApi
            if (type != null) pending.type = type
            function?.let { fn ->
                fn["name"]?.jsonPrimitive?.content?.let { pending.name = it }
                fn["arguments"]?.jsonPrimitive?.content?.let { pending.argsBuffer.append(it) }
            }
        }

        // Emit one ToolCall per fully-streamed tool. We rely on
        // the parent `choice.finish_reason` to know when the LLM
        // is done sending deltas for this choice.
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
        if (finishReason == "tool_calls" || finishReason == "stop") {
            val completed = pendingCalls.values.toList()
            pendingCalls.clear()
            completed.firstOrNull()?.let { pending ->
                val argsText = pending.argsBuffer.toString()
                val argsObj = try {
                    json.parseToJsonElement(argsText).jsonObject
                } catch (e: IOException) {
                    buildJsonObject {}
                }
                return LlmChunk.ToolCall(
                    providerId = providerId,
                    callId = pending.callId,
                    name = pending.name,
                    args = argsObj,
                )
            }
        }
        return null
    }

    private class PendingToolCall(
        var callId: String,
        var name: String = "",
        var type: String = "function",
        val argsBuffer: StringBuilder = StringBuilder(),
    )
}
