package com.meshlit.core.cloudmcp.android

/**
 * Sealed class of every Android automation tool call. The
 * agent loop dispatches a single [AutomationRequest] to
 * [MeshlitAccessibilityService] per tool call. Each variant
 * carries the exact arguments the LLM passed.
 *
 * The wire is in-process (LocalBinder), so these are plain
 * data classes — no JSON-RPC serialization. The agent loop
 * mapping is direct: `app_click(text="...")` → [ClickRequest]
 * with `text = "..."`.
 *
 * The [targetPackage] is the foreground package at the time
 * of the call. The agent loop populates this from the latest
 * [AndroidSnapshot] before dispatching; the
 * [AndroidAutomationPermission] policy uses it to consult
 * the user's allowlist.
 */
sealed class AutomationRequest {
    abstract val targetPackage: String

    /** List installed packages (filterable). */
    data class ListApps(
        override val targetPackage: String,
        val query: String?,
        val includeSystem: Boolean,
    ) : AutomationRequest()

    /** Launch an app via `Intent.ACTION_MAIN`. */
    data class OpenApp(
        override val targetPackage: String,
        val packageName: String,
    ) : AutomationRequest()

    /** Capture the current foreground accessibility tree. */
    data class Snapshot(
        override val targetPackage: String,
    ) : AutomationRequest()

    /** Tap a node by one of three descriptors. */
    data class ClickRequest(
        override val targetPackage: String,
        val text: String? = null,
        val contentDescription: String? = null,
        val resourceId: String? = null,
    ) : AutomationRequest()

    /** Inject text into the currently focused EditText. */
    data class TypeRequest(
        override val targetPackage: String,
        val text: String,
        val isPasswordField: Boolean = false,
    ) : AutomationRequest()

    /** Back key. */
    data class BackRequest(override val targetPackage: String) : AutomationRequest()

    /** Home key. */
    data class HomeRequest(override val targetPackage: String) : AutomationRequest()

    /** Poll until a node with the given descriptor appears. */
    data class WaitForRequest(
        override val targetPackage: String,
        val text: String? = null,
        val resourceId: String? = null,
        val timeoutMs: Long,
    ) : AutomationRequest()

    /** Capture a PNG of the foreground. */
    data class ScreenshotRequest(override val targetPackage: String) : AutomationRequest()
}

/**
 * One automation tool call's result. The agent loop wraps the
 * [body] into an [McpEvent.ToolResult.body] JSON string.
 */
sealed class AutomationResponse {
    abstract val ok: Boolean
    abstract val error: String?

    data class SnapshotResponse(
        val snapshot: AndroidSnapshot,
        override val ok: Boolean = true,
        override val error: String? = null,
    ) : AutomationResponse()

    data class ScreenshotResponse(
        val mime: String,
        val base64Data: String,
        override val ok: Boolean = true,
        override val error: String? = null,
    ) : AutomationResponse()

    data class ListAppsResponse(
        val packages: List<String>,
        override val ok: Boolean = true,
        override val error: String? = null,
    ) : AutomationResponse()

    data class UnitResponse(
        override val ok: Boolean = true,
        override val error: String? = null,
    ) : AutomationResponse()
}