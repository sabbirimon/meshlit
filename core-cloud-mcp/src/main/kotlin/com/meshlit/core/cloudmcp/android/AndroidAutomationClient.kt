package com.meshlit.core.cloudmcp.android

import com.meshlit.core.cloudmcp.McpTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The agent-loop side of the Android in-app automation wire.
 * Registers the nine `app_*` tools in the project's
 * [com.meshlit.core.cloudmcp.ToolRegistry] and forwards
 * invocations to the bound [MeshlitAccessibilityService].
 *
 * The wire is in-process (LocalBinder), so the client is a
 * thin wrapper that:
 *   1. Consults [AndroidAutomationPermission] for the action.
 *   2. Maps the LLM's `args` to an [AutomationRequest].
 *   3. Forwards to the service.
 *   4. Wraps the [AutomationResponse] in
 *      [com.meshlit.core.cloudmcp.McpEvent.ToolResult].
 *
 * The nine tools:
 *   - `app_list`        — list installed packages (filterable).
 *   - `app_open`        — launch an app.
 *   - `app_snapshot`    — capture the current foreground tree.
 *   - `app_click`       — tap a node by descriptor.
 *   - `app_type`        — type into the focused EditText.
 *   - `app_back`        — back key.
 *   - `app_home`        — home key.
 *   - `app_wait_for`    — poll until a node appears.
 *   - `app_screenshot`  — capture a PNG of the foreground.
 *
 * Behind the `feature.cloud.android_automation` flag.
 */
class AndroidAutomationClient(
    private val snapshotStore: AndroidSnapshotStore,
    private val bridgeProvider: () -> AndroidUiAutomatorBridge?,
    private val invoke: suspend (AutomationRequest) -> AutomationResponse,
) {
    private val bridge: AndroidUiAutomatorBridge? get() = bridgeProvider()

    /**
     * Register the nine `app_*` tools in [registry]. Idempotent
     * — repeat calls with the same providerId replace the
     * existing entries.
     */
    fun register(registry: com.meshlit.core.cloudmcp.ToolRegistry) {
        registry.putAll(PROVIDER_ID, listOf(
            appListTool(),
            appOpenTool(),
            appSnapshotTool(),
            appClickTool(),
            appTypeTool(),
            appBackTool(),
            appHomeTool(),
            appWaitForTool(),
            appScreenshotTool(),
        ))
    }

    /**
     * Dispatch a single tool invocation. [toolName] is one of
     * the nine `app_*` names; [args] is the JSON body the LLM
     * sent. Returns the [AutomationResponse] from the service.
     */
    suspend fun invoke(toolName: String, args: JsonObject): AutomationResponse {
        val targetPackage = args["targetPackage"]?.asString.orEmpty()
        return when (toolName) {
            TOOL_LIST -> invoke(AutomationRequest.ListApps(
                targetPackage = targetPackage,
                query = args["query"]?.asString,
                includeSystem = args["includeSystem"]?.asBoolean ?: false,
            ))
            TOOL_OPEN -> invoke(AutomationRequest.OpenApp(
                targetPackage = targetPackage,
                packageName = args["packageName"]?.asString.orEmpty(),
            ))
            TOOL_SNAPSHOT -> invoke(AutomationRequest.Snapshot(targetPackage = targetPackage))
            TOOL_CLICK -> invoke(AutomationRequest.ClickRequest(
                targetPackage = targetPackage,
                text = args["text"]?.asString,
                contentDescription = args["contentDescription"]?.asString,
                resourceId = args["resourceId"]?.asString,
            ))
            TOOL_TYPE -> invoke(AutomationRequest.TypeRequest(
                targetPackage = targetPackage,
                text = args["text"]?.asString.orEmpty(),
                isPasswordField = args["isPasswordField"]?.asBoolean ?: false,
            ))
            TOOL_BACK -> invoke(AutomationRequest.BackRequest(targetPackage = targetPackage))
            TOOL_HOME -> invoke(AutomationRequest.HomeRequest(targetPackage = targetPackage))
            TOOL_WAIT_FOR -> invoke(AutomationRequest.WaitForRequest(
                targetPackage = targetPackage,
                text = args["text"]?.asString,
                resourceId = args["resourceId"]?.asString,
                timeoutMs = (args["timeoutMs"]?.asLong ?: 5_000L)
                    .coerceIn(0L, 30_000L),
            ))
            TOOL_SCREENSHOT -> invoke(AutomationRequest.ScreenshotRequest(targetPackage = targetPackage))
            else -> AutomationResponse.UnitResponse(ok = false, error = "unknown tool: $toolName")
        }
    }

    /** Extract a string from a JSON value, returning null for
     *  null/missing values. */
    private val kotlinx.serialization.json.JsonElement?.asString: String?
        get() = (this as? JsonPrimitive)?.content
    private val kotlinx.serialization.json.JsonElement?.asBoolean: Boolean
        get() = (this as? JsonPrimitive)?.content?.toBoolean() ?: false
    private val kotlinx.serialization.json.JsonElement?.asLong: Long
        get() = (this as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private fun appListTool() = McpTool(
        name = TOOL_LIST,
        description = "List installed packages on the device. Filterable by `query` (matches " +
            "label or package name). Set `includeSystem=true` to include system packages.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional filter — matches label or packageName")
                })
                put("includeSystem", buildJsonObject {
                    put("type", "boolean")
                    put("default", false)
                    put("description", "Include system packages")
                })
            })
        },
        providerId = PROVIDER_ID,
    )

    private fun appOpenTool() = McpTool(
        name = TOOL_OPEN,
        description = "Launch an app by `packageName` (e.g. `com.google.android.gm`).",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("packageName", buildJsonObject {
                    put("type", "string")
                    put("description", "Android package name")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("packageName"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun appSnapshotTool() = McpTool(
        name = TOOL_SNAPSHOT,
        description = "Capture the current foreground window's accessibility tree as a JSON list of " +
            "nodes `{className, text, contentDescription, resourceId, bounds, isClickable, " +
            "isEditable, isPassword, children[]}`.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
        providerId = PROVIDER_ID,
    )

    private fun appClickTool() = McpTool(
        name = TOOL_CLICK,
        description = "Tap the node matching one of `text` / `contentDescription` / `resourceId`. " +
            "Exactly one descriptor must be supplied.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Visible text content to match")
                })
                put("contentDescription", buildJsonObject {
                    put("type", "string")
                    put("description", "Accessibility content-description to match")
                })
                put("resourceId", buildJsonObject {
                    put("type", "string")
                    put("description", "Fully-qualified resource id (e.g. com.google.android.gm:id/subject)")
                })
            })
        },
        providerId = PROVIDER_ID,
    )

    private fun appTypeTool() = McpTool(
        name = TOOL_TYPE,
        description = "Inject text into the currently focused EditText. The target field must be " +
            "focused first (typically via `app_click`). The agent loop flags `isPasswordField=true` " +
            "for password inputs so the permission policy can require explicit confirmation.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to type")
                })
                put("isPasswordField", buildJsonObject {
                    put("type", "boolean")
                    put("default", false)
                    put("description", "Set to true if the target is a password field. Forces confirmation")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("text"))))
        },
        providerId = PROVIDER_ID,
    )

    private fun appBackTool() = McpTool(
        name = TOOL_BACK,
        description = "Send the back key.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
        providerId = PROVIDER_ID,
    )

    private fun appHomeTool() = McpTool(
        name = TOOL_HOME,
        description = "Send the home key.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
        providerId = PROVIDER_ID,
    )

    private fun appWaitForTool() = McpTool(
        name = TOOL_WAIT_FOR,
        description = "Poll until a node matching `text` or `resourceId` appears, or `timeoutMs` " +
            "elapses. Returns the matching node, or null on timeout.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
                put("resourceId", buildJsonObject { put("type", "string") })
                put("timeoutMs", buildJsonObject {
                    put("type", "integer")
                    put("minimum", 0)
                    put("maximum", 30_000)
                    put("default", 5_000)
                    put("description", "Max poll duration in ms (default 5000, max 30000)")
                })
            })
        },
        providerId = PROVIDER_ID,
    )

    private fun appScreenshotTool() = McpTool(
        name = TOOL_SCREENSHOT,
        description = "Capture a PNG screenshot of the current foreground. Returns a JSON object " +
            "with `{mime: \"image/png\", data: \"<base64>\"}`.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        },
        providerId = PROVIDER_ID,
    )

    companion object {
        const val PROVIDER_ID = "android-automation"
        const val TOOL_LIST = "app_list"
        const val TOOL_OPEN = "app_open"
        const val TOOL_SNAPSHOT = "app_snapshot"
        const val TOOL_CLICK = "app_click"
        const val TOOL_TYPE = "app_type"
        const val TOOL_BACK = "app_back"
        const val TOOL_HOME = "app_home"
        const val TOOL_WAIT_FOR = "app_wait_for"
        const val TOOL_SCREENSHOT = "app_screenshot"
    }
}