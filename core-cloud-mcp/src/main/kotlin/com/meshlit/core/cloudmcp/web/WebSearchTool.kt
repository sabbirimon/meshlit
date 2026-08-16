package com.meshlit.core.cloudmcp.web

import com.meshlit.core.cloudmcp.McpTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The LLM-facing `web_search` [McpTool]. The tool is registered
 * in [com.meshlit.core.cloudmcp.ToolRegistry] under
 * `providerId = "web-search"` regardless of which underlying
 * vendor (Bing / Brave / Serper / Tavily / Google CSE) is
 * configured by the user. The dispatcher is responsible for
 * routing [invoke] to the right vendor.
 *
 * Input schema (OpenAI-style JSON Schema):
 * ```
 * {
 *   "type": "object",
 *   "properties": {
 *     "query": { "type": "string", "description": "Search query" },
 *     "k":     { "type": "integer", "minimum": 1, "maximum": 20,
 *                "description": "Max number of results (default 5)" }
 *   },
 *   "required": ["query"]
 * }
 * ```
 *
 * The output is a list of `{title, url, snippet, score?}`
 * rows the LLM can summarise or pipe into a follow-up
 * `browser_open(url)` / `http_tool(url)` call.
 */
object WebSearchTool {

    /** Stable providerId used in the registry. */
    const val PROVIDER_ID = "web-search"

    /** Stable tool name; never ties to vendor. */
    const val TOOL_NAME = "web_search"

    /**
     * Build the [McpTool] descriptor the agent loop surfaces to
     * the LLM. Pass the result to
     * [com.meshlit.core.cloudmcp.ToolRegistry.put].
     */
    fun toolDescriptor(): McpTool = McpTool(
        name = TOOL_NAME,
        description = "Search the public web for the given query and return up to `k` " +
            "(default 5, max 20) ranked `{title, url, snippet, score?}` results.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Search query")
                })
                put("k", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 20)
                    put("default", 5)
                    put("description", "Max number of results (default 5)")
                })
            })
            put("required", JsonArray(listOf(
                kotlinx.serialization.json.JsonPrimitive("query"),
            )))
        },
        providerId = PROVIDER_ID,
    )

    /**
     * Invoke [provider] (dispatching through [dispatcher]) and
     * return the LLM-friendly JSON body. Returns `{ ok: false,
     * error: "..." }` on transport failure so the LLM sees a
     * plain error string instead of an exception.
     */
    suspend fun invoke(
        provider: WebSearchProvider,
        dispatcher: WebSearchDispatcher,
        args: JsonObject,
    ): InvokeResult {
        val query = args["query"]?.takeIf { it.toString() != "null" }
            ?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            return InvokeResult(ok = false, error = "missing required arg: query")
        }
        val k = (args["k"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 20)
        val rows = dispatcher.search(provider, query, k)
        return InvokeResult(
            ok = true,
            results = rows.map { row ->
                buildJsonObject {
                    put("title", row.title)
                    put("url", row.url)
                    put("snippet", row.snippet)
                    row.score?.let { put("score", it) }
                }
            },
        )
    }

    data class InvokeResult(
        val ok: Boolean,
        val results: List<JsonObject> = emptyList(),
        val error: String? = null,
    ) {
        /** Serialise to the JSON body that fills the [com.meshlit.core.cloudmcp.McpEvent.ToolResult]. */
        fun toBody(): String = when {
            ok -> kotlinx.serialization.json.Json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("ok", true)
                    put("results", JsonArray(results))
                },
            )
            else -> kotlinx.serialization.json.Json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("ok", false)
                    put("error", error.orEmpty())
                },
            )
        }
    }
}
