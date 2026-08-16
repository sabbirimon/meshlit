package com.meshlit.core.cloudmcp.browser

import com.meshlit.core.cloudmcp.McpTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin client over a Playwright-MCP server (the upstream
 * `microsoft/playwright-mcp` reference). The server is a
 * standalone process exposing a JSON-RPC over SSE bridge with
 * the four tools:
 *   - `browser_navigate` — navigate to a URL
 *   - `browser_click` — click a CSS selector
 *   - `browser_type` — type into a CSS selector
 *   - `browser_screenshot` — return a base64 PNG of the page
 *
 * Meshlit runs the Playwright-MCP server externally (locally or
 * a hosted instance). The endpoint is the SSE URL exposed by
 * the server — e.g. `http://localhost:8931/sse` for a local
 * install. The client is a stateless bridge: every call is one
 * HTTP POST to `{baseUrl}/tools/call` with the JSON-RPC
 * envelope `{"name": "...", "arguments": {...}}`.
 *
 * The four [McpTool]s are registered under `providerId =
 * "browser"` so the agent loop can route calls back here. The
 * [BrowserActionPermission] policy checks every call before
 * it's dispatched.
 *
 * Behind the `feature.cloud.browser` flag — when the flag is
 * off, the agent loop never registers these tools.
 */
class BrowserAutomationClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Register the four browser tools on [registry]. Idempotent
     * — repeat calls with the same providerId replace the
     * existing entries.
     */
    fun register(registry: com.meshlit.core.cloudmcp.ToolRegistry) {
        registry.putAll(PROVIDER_ID, listOf(
            browserNavigateTool(),
            browserClickTool(),
            browserTypeTool(),
            browserScreenshotTool(),
        ))
    }

    /**
     * Dispatch a single tool invocation. [toolName] is one of
     * the four `browser_*` names; [args] is the JSON body the
     * LLM sent. Returns a [BrowserResult] that the agent loop
     * wraps into an [McpEvent.ToolResult].
     */
    suspend fun invoke(
        toolName: String,
        args: JsonObject,
    ): BrowserResult {
        val callArgs = when (toolName) {
            TOOL_NAVIGATE -> buildJsonObject { put("url", args["url"] ?: JsonPrimitive("")) }
            TOOL_CLICK -> buildJsonObject { put("selector", args["selector"] ?: JsonPrimitive("")) }
            TOOL_TYPE -> buildJsonObject {
                put("selector", args["selector"] ?: JsonPrimitive(""))
                put("text", args["text"] ?: JsonPrimitive(""))
            }
            TOOL_SCREENSHOT -> buildJsonObject {}
            else -> return BrowserResult(ok = false, error = "unknown tool: $toolName")
        }
        val envelope = buildJsonObject {
            put("name", toolName)
            put("arguments", callArgs)
        }
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/tools/call")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .post(json.encodeToString(JsonObject.serializer(), envelope).toRequestBody(jsonMediaType))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return BrowserResult(
                        ok = false,
                        error = "browser HTTP ${response.code}: ${response.message}",
                    )
                }
                val body = response.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(body).jsonObject }
                    .getOrDefault(buildJsonObject {})
                BrowserResult(ok = true, body = parsed)
            }
        } catch (e: IOException) {
            BrowserResult(ok = false, error = e.message ?: "network error")
        }
    }

    private fun browserNavigateTool() = McpTool(
        name = TOOL_NAVIGATE,
        description = "Navigate the browser to the given URL. Returns the page title and final URL.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "Absolute URL to navigate to (https://...)")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("url"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun browserClickTool() = McpTool(
        name = TOOL_CLICK,
        description = "Click the element matching the CSS selector. The selector may be a CSS path, " +
            "an `aria-role[name=...]` token, or a text-content selector like `text=Submit`.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("selector", buildJsonObject {
                    put("type", "string")
                    put("description", "CSS / text / aria selector")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("selector"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun browserTypeTool() = McpTool(
        name = TOOL_TYPE,
        description = "Type the given text into the element matching the CSS selector. The field is " +
            "cleared first; the focus is moved to the field before typing.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("selector", buildJsonObject {
                    put("type", "string")
                    put("description", "CSS selector of the editable element")
                })
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to type")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("selector"), JsonPrimitive("text"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun browserScreenshotTool() = McpTool(
        name = TOOL_SCREENSHOT,
        description = "Capture a PNG screenshot of the current page. Returns a JSON object with " +
            "`{mime: \"image/png\", data: \"<base64>\"}`.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
        providerId = PROVIDER_ID,
    )

    companion object {
        const val PROVIDER_ID = "browser"
        const val TOOL_NAVIGATE = "browser_navigate"
        const val TOOL_CLICK = "browser_click"
        const val TOOL_TYPE = "browser_type"
        const val TOOL_SCREENSHOT = "browser_screenshot"
    }
}

/**
 * One browser tool call's result. The agent loop wraps this
 * into an [McpEvent.ToolResult.body] JSON string.
 */
data class BrowserResult(
    val ok: Boolean,
    val error: String? = null,
    val body: JsonObject = buildJsonObject {},
) {
    fun toBodyString(): String = Json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("ok", ok)
            error?.let { put("error", it) }
            put("body", body)
        },
    )
}
