package com.meshlit.core.cloudmcp

/**
 * One observable event surfaced by [CloudMcpCoordinator] /
 * [CloudMcpSession]. The agentic loop renders these in the UI:
 *
 *  - `Thought` — the LLM's reasoning text. Streamed in chunks.
 *  - `ToolCall` — the LLM invoked a tool. Surfaces once per call.
 *    `providerId` lets the UI group events by cloud (AWS / DO / …)
 *    and `callId` is the OpenAI-style correlation token.
 *  - `ToolResult` — the MCP server's response. `ok = false` mirrors
 *    upstream's red-tinted card; the user can re-run the loop from
 *    there.
 *  - `Error` — connection or protocol failure. Terminal.
 *  - `Connected` / `Disconnected` — session lifecycle.
 *  - `Done` — the agentic loop finished (no more tool calls).
 */
sealed class McpEvent {
    abstract val providerId: String

    data class Connected(
        override val providerId: String,
        val tools: List<McpTool>,
    ) : McpEvent()

    data class Disconnected(
        override val providerId: String,
        val reason: String? = null,
    ) : McpEvent()

    data class Thought(
        override val providerId: String,
        val text: String,
        val isFinal: Boolean = false,
    ) : McpEvent()

    data class ToolCall(
        override val providerId: String,
        val callId: String,
        val name: String,
        val args: kotlinx.serialization.json.JsonObject,
    ) : McpEvent()

    data class ToolResult(
        override val providerId: String,
        val callId: String,
        val ok: Boolean,
        val body: String,
    ) : McpEvent()

    data class Error(
        override val providerId: String,
        val message: String,
        val terminal: Boolean = false,
    ) : McpEvent()

    /** Loop finished cleanly — no more events incoming. */
    data class Done(override val providerId: String) : McpEvent()
}

/**
 * Tool descriptor surfaced to the LLM. Mirrors the OpenAI
 * `tools[].function` shape minus the JSON-Schema strict mode flag
 * — the MCP transports we target today don't honor it.
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: kotlinx.serialization.json.JsonObject,
    val providerId: String,
)
