package com.meshlit.core.cloudmcp.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI-compatible Chat Completions request/response DTOs.
 * NaraRouter (`https://router.bynara.id/`) accepts this exact
 * shape — confirmed by the NaraRouter reference client.
 *
 * Wire format mirrors OpenAI's `/v1/chat/completions` endpoint.
 * Streaming uses Server-Sent Events with frames like:
 *
 *     data: {"id":"chatcmpl-…","object":"chat.completion.chunk",
 *            "choices":[{"delta":{"content":"Hello"},"index":0}]}
 *
 *     data: [DONE]
 *
 * Tool-call deltas come through `choices[].delta.tool_calls[]`
 * with a streamed `function.arguments` JSON string that we
 * accumulate across frames before parsing.
 */

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val tools: List<OpenAITool> = emptyList(),
    val tool_choice: String = "auto",
    val stream: Boolean = true,
    val temperature: Double = 0.7,
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String? = null,
    val tool_call_id: String? = null,
    val tool_calls: List<OpenAIToolCallRef>? = null,
)

@Serializable
data class OpenAITool(
    val type: String = "function",
    val function: OpenAIToolFunction,
)

@Serializable
data class OpenAIToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class OpenAIToolCallRef(
    val id: String,
    val type: String = "function",
    val function: OpenAIToolCallFunction,
)

@Serializable
data class OpenAIToolCallFunction(
    val name: String,
    val arguments: String,
)
