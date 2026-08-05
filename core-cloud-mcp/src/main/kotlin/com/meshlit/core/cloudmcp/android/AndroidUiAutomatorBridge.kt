package com.meshlit.core.cloudmcp.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * Thin wrapper around Google's official Android UI Automator
 * library (`androidx.test.uiautomator`). The bridge is the
 * production engine behind every `app_*` tool —
 * [MeshlitAccessibilityService] forwards each tool invocation
 * to the matching [execute] method.
 *
 * UI Automator is the same library the Espresso / Compose-test
 * UI Automator framework uses. Production code binds against
 * `UiDevice` / `By` / `UiObject2` exactly the same way an
 * instrumented test would.
 *
 * The bridge holds no state — every method is a one-shot call
 * against the [UiDevice] singleton. The service is responsible
 * for capturing the [AndroidSnapshot] before dispatching.
 */
class AndroidUiAutomatorBridge(
    private val context: Context,
    private val device: UiDevice,
) {

    /**
     * Find the first node matching [text] / [contentDescription]
     * / [resourceId] (any one of the three) and click it.
     *
     * @return true if a node was found and clicked.
     */
    fun click(
        text: String? = null,
        contentDescription: String? = null,
        resourceId: String? = null,
    ): Boolean {
        val node = findNode(text, contentDescription, resourceId) ?: return false
        node.click()
        return true
    }

    /**
     * Inject [text] into the currently focused EditText. The
     * caller is responsible for ensuring the target field is
     * focused (typically via [click] on the field first).
     */
    fun typeText(text: String): Boolean {
        val focused = device.findObject(By.focused(true)) ?: return false
        focused.text = text
        return true
    }

    /** Send the back key. */
    fun back() {
        device.pressBack()
    }

    /** Send the home key. */
    fun home() {
        device.pressHome()
    }

    /**
     * Launch the app identified by [packageName]. Returns
     * true when the launch succeeded.
     */
    fun openApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    /**
     * List installed packages. When [query] is non-blank, only
     * packages whose label or name contains the query (case-
     * insensitive) are returned.
     */
    fun listApps(query: String?, includeSystem: Boolean): List<String> {
        val pm = context.packageManager
        val all = pm.getInstalledApplications(0)
        val filtered = if (query.isNullOrBlank()) all
            else all.filter { app ->
                val label = pm.getApplicationLabel(app).toString()
                val name = app.packageName
                label.contains(query, ignoreCase = true) ||
                    name.contains(query, ignoreCase = true)
            }
        val filteredAndTrimmed = if (includeSystem) filtered
            else filtered.filter { isUserApp(it, pm) }
        return filteredAndTrimmed.map { it.packageName }
    }

    private fun isUserApp(app: ApplicationInfo, pm: PackageManager): Boolean {
        return (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
            (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * Poll until a node matching [text] / [resourceId] appears,
     * or [timeoutMs] elapses. Returns the matching node or
     * null.
     */
    fun waitFor(
        text: String? = null,
        resourceId: String? = null,
        timeoutMs: Long,
    ): UiObject2? {
        val selector = when {
            text != null -> By.text(text)
            resourceId != null -> By.res(resourceId)
            else -> return null
        }
        device.wait(Until.hasObject(selector), timeoutMs)
        return device.findObject(selector)
    }

    /**
     * Find a node by any of the three descriptors. Returns
     * the first matching node.
     */
    private fun findNode(
        text: String?,
        contentDescription: String?,
        resourceId: String?,
    ): UiObject2? {
        when {
            text != null -> device.findObject(By.text(text))?.let { return it }
            contentDescription != null -> device.findObject(By.desc(contentDescription))?.let { return it }
            resourceId != null -> device.findObject(By.res(resourceId))?.let { return it }
        }
        return null
    }

    /**
     * Convert a [UiObject2] to an [AndroidNode] for the snapshot
     * tree. The bridge stops at one level of children because
     * the LLM's prompt budget is bounded.
     */
    fun toNode(node: UiObject2): AndroidNode {
        val children = node.children.map { toNode(it) }
        val isPwd = (node.className?.contains("EditText", ignoreCase = true) == true) &&
            // UiObject2 has no isPassword directly; we treat all
            // editable text fields as potentially password
            // fields and let the agent loop override via
            // isPasswordField=true on the tool call.
            false
        return AndroidNode(
            className = node.className,
            text = node.text,
            contentDescription = node.contentDescription,
            resourceId = (node as Any).run {
                // UiObject2 exposes resourceId only via reflection
                // in the public API. For Compose-Android-style
                // resource ids the service's `viewIdResourceName`
                // is the source of truth — the bridge falls back
                // to a searchById pattern when no reflection
                // handle is available.
                null
            },
            bounds = node.visibleBounds,
            isClickable = node.isClickable,
            isEditable = node.isLongClickable && node.text?.isEmpty() == false,
            isPassword = isPwd,
            children = children,
        )
    }
}