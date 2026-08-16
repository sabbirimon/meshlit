package com.meshlit.core.cloudmcp.llm

import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.McpTool
import com.meshlit.core.cloudmcp.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Streaming client for NaraRouter (`https://router.bynara.id/`).
 *
 * The wire format is OpenAI-compatible chat completions. Each
 * SSE frame is one chunk of the response — `delta.content` for
 * text, `delta.tool_calls[]` for streamed tool invocations.
 *
 * Usage:
 * ```
 * val client = NaraRouterClient(httpClient, apiKey = "nara-…")
 * client.chatCompletions(
 *     model = NaraRouterModel.Default,
 *     messages = listOf(OpenAIMessage("user", "List my EC2 instances")),
 *     tools = toolRegistry.ordered(),
 * ).collect { chunk ->
 *     when (chunk) {
 *         is LlmChunk.Text -> println(chunk.delta)
 *         is LlmChunk.ToolCall -> println("calling ${chunk.name}")
 *         LlmChunk.Done -> break
 *     }
 * }
 * ```
 *
 * The client never invokes tools itself — it streams chunks up
 * to the agent loop, which dispatches [McpEvent.ToolCall] back
 * to [com.meshlit.core.cloudmcp.CloudMcpCoordinator.callTool].
 */
class NaraRouterClient(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://router.bynara.id",
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
     * a synthetic `nara-…` id when the cloud LLM itself isn't a
     * registered [com.meshlit.core.cloudmcp.ProviderKind].
     */
    fun chatCompletions(
        providerId: String,
        model: NaraRouterModel,
        messages: List<OpenAIMessage>,
        tools: List<McpTool>,
    ): Flow<LlmChunk> = flow {
        val requestBody = OpenAIChatRequest(
            model = model.slug,
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
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
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
                        message = "NaraRouter HTTP ${response.code}: ${response.message}",
                    ))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(LlmChunk.Error(providerId, message = "empty body"))
                    return@flow
                }
                val parser = SseParser()
                // Track tool-call deltas across frames so a single
                // streamed `function.arguments` JSON string is
                // accumulated before we re-emit as [LlmChunk.ToolCall].
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
                PendingToolCall(callId = callIdFromApi ?: "nara-$index")
            }
            if (callIdFromApi != null) pending.callId = callIdFromApi
            if (type != null) pending.type = type
            function?.let { fn ->
                fn["name"]?.jsonPrimitive?.content?.let { pending.name = it }
                fn["arguments"]?.jsonPrimitive?.content?.let { pending.argsBuffer.append(it) }
            }
        }

        // We only emit one ToolCall per fully-streamed tool. We
        // use the finish_reason on the parent `choice` to know
        // when the LLM is done sending deltas for this choice.
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
        if (finishReason == "tool_calls" || finishReason == "stop") {
            val completed = pendingCalls.values.toList()
            pendingCalls.clear()
            // Return the first completed call; subsequent ones
            // are emitted on the next iteration of the consumer.
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

/** One streamed chunk from NaraRouter. */
sealed class LlmChunk {
    abstract val providerId: String

    data class Text(
        override val providerId: String,
        val delta: String,
        val isFinal: Boolean,
    ) : LlmChunk()

    data class ToolCall(
        override val providerId: String,
        val callId: String,
        val name: String,
        val args: JsonObject,
    ) : LlmChunk()

    data class Error(
        override val providerId: String,
        val message: String,
    ) : LlmChunk()

    data class Done(override val providerId: String) : LlmChunk()
}