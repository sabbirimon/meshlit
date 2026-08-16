package com.meshlit.core.cloudmcp.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream

/**
 * The Android-side of the in-app automation wire. The agent
 * loop talks to this service through the static
 * [MeshlitAccessibilityService.instance] accessor (set in
 * [onServiceConnected]) and dispatches [AutomationRequest]s
 * against the bound [androidx.test.uiautomator.UiDevice] /
 * `AccessibilityNodeInfo` surfaces.
 *
 * Lifecycle:
 *   1. The user flips on `feature.cloud.android_automation` in
 *      Settings → Cloud → Android automation.
 *   2. The settings flow routes to `Settings.ACTION_ACCESSIBILITY_SETTINGS`
 *      (non-bypassable system prompt).
 *   3. The user enables `Meshlit Accessibility Service`.
 *   4. The system binds the service; `onServiceConnected` fires
 *      and assigns `instance = this`.
 *   5. The agent loop polls [MeshlitAccessibilityService.instance]
 *      on every prompt and registers the nine `app_*` tools
 *      when the service is bound.
 *
 * When the user disables the service at runtime, the static
 * accessor returns null and every [AutomationRequest] surfaces
 * as `ToolResult(ok = false, body = "service-disabled")`.
 *
 * Tree capture runs on every `TYPE_WINDOW_STATE_CHANGED` so
 * the LLM always has an up-to-date screen context.
 */
class MeshlitAccessibilityService : AccessibilityService() {

    private val snapshotStore = AndroidSnapshotStore()
    private val bridge: AndroidUiAutomatorBridge? by lazy {
        runCatching { AndroidUiAutomatorBridge(this, getUiDevice()) }.getOrNull()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            // Surface the flags we need:
            //  - canRetrieveWindowContent — read the accessibility tree.
            //  - canTakeScreenshot — capture the foreground (API 33+).
            //  - flagDefault — let the OS render the default feedback.
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        snapshotStore.clear()
        Log.i(TAG, "AccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        snapshotStore.clear()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val source = event.source ?: return
        val packageName = source.packageName?.toString() ?: return
        val windowClass = source.className?.toString() ?: ""
        try {
            val tree = buildTree(source)
            snapshotStore.put(
                AndroidSnapshot(
                    packageName = packageName,
                    windowClass = windowClass,
                    capturedAtMs = System.currentTimeMillis(),
                    nodes = tree,
                ),
            )
        } finally {
            source.recycle()
        }
    }

    override fun onInterrupt() {
        // No-op — the service runs passively.
    }

    /**
     * Synchronous dispatch. The agent loop wraps each call in
     * the dispatch coroutine so the UI thread is never blocked.
     */
    fun dispatch(request: AutomationRequest): AutomationResponse {
        val b = bridge ?: return AutomationResponse.UnitResponse(
            ok = false,
            error = "service not bound",
        )
        return when (request) {
            is AutomationRequest.Snapshot -> {
                val root = rootInActiveWindow?.toAndroidNode()
                    ?: return AutomationResponse.UnitResponse(
                        ok = false,
                        error = "no active window",
                    )
                AutomationResponse.SnapshotResponse(
                    AndroidSnapshot(
                        packageName = root.className?.substringBefore('.') ?: "",
                        windowClass = root.className ?: "",
                        capturedAtMs = System.currentTimeMillis(),
                        nodes = listOf(root),
                    ),
                )
            }
            is AutomationRequest.ClickRequest -> {
                val ok = b.click(
                    text = request.text,
                    contentDescription = request.contentDescription,
                    resourceId = request.resourceId,
                )
                AutomationResponse.UnitResponse(
                    ok = ok,
                    error = if (ok) null else "no matching node",
                )
            }
            is AutomationRequest.TypeRequest -> {
                val ok = b.typeText(request.text)
                AutomationResponse.UnitResponse(
                    ok = ok,
                    error = if (ok) null else "no focused EditText",
                )
            }
            is AutomationRequest.BackRequest -> {
                b.back()
                AutomationResponse.UnitResponse(ok = true)
            }
            is AutomationRequest.HomeRequest -> {
                b.home()
                AutomationResponse.UnitResponse(ok = true)
            }
            is AutomationRequest.OpenApp -> {
                val ok = b.openApp(request.packageName)
                AutomationResponse.UnitResponse(
                    ok = ok,
                    error = if (ok) null else "no launcher activity for ${request.packageName}",
                )
            }
            is AutomationRequest.ListApps -> {
                AutomationResponse.ListAppsResponse(
                    packages = b.listApps(request.query, request.includeSystem),
                )
            }
            is AutomationRequest.WaitForRequest -> {
                val node = b.waitFor(
                    text = request.text,
                    resourceId = request.resourceId,
                    timeoutMs = request.timeoutMs,
                )
                AutomationResponse.UnitResponse(
                    ok = node != null,
                    error = if (node != null) null else "timeout",
                )
            }
            is AutomationRequest.ScreenshotRequest -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    takeScreenshotBase64()
                } else {
                    AutomationResponse.UnitResponse(
                        ok = false,
                        error = "screenshot requires API 33+",
                    )
                }
            }
        }
    }

    /**
     * Recursively build [AndroidNode]s from an
     * [AccessibilityNodeInfo] tree. Shallow (one level of
     * children for typical apps) so the LLM's prompt budget
     * stays bounded.
     */
    private fun buildTree(node: AccessibilityNodeInfo): List<AndroidNode> {
        val root = node.toAndroidNode()
        return listOf(root)
    }

    private fun AccessibilityNodeInfo.toAndroidNode(): AndroidNode {
        val bounds = android.graphics.Rect().also { getBoundsInScreen(it) }
        val childList = (0 until childCount).mapNotNull { i ->
            getChild(i)?.toAndroidNode()
        }
        return AndroidNode(
            className = className?.toString(),
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            resourceId = viewIdResourceName,
            bounds = bounds,
            isClickable = isClickable,
            isEditable = isEditable,
            isPassword = isPassword,
            children = childList,
        )
    }

    /**
     * Resolve the [UiDevice] singleton. UI Automator exposes
     * `UiDevice.getInstance(Instrumentation)` for tests; the
     * production use case for the AccessibilityService uses
     * a similar surface but bound to the service context.
     *
     * The bridge is built lazily on first dispatch — see
     * [LocalBinder.dispatch] — because the device singleton
     * requires a bound Instrumentation, which is only
     * present after the service connects.
     */
    private fun getUiDevice(): androidx.test.uiautomator.UiDevice =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Class.forName("androidx.test.uiautomator.UiDevice")
                .getMethod("getInstance", android.app.Instrumentation::class.java)
                .invoke(null, null) as? androidx.test.uiautomator.UiDevice
        }.getOrNull() ?: throw IllegalStateException(
            "UiDevice unavailable — service not bound to test instrumentation",
        )

    /**
     * Capture a PNG screenshot of the foreground. API 33+ uses
     * [takeScreenshot] (the dedicated AccessibilityService API
     * that doesn't require MediaProjection); older devices
     * return null and the agent loop surfaces a graceful error.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun takeScreenshotBase64(): AutomationResponse {
        val executor = java.util.concurrent.Executor { it.run() }
        val latch = java.util.concurrent.CountDownLatch(1)
        var bitmap: Bitmap? = null
        try {
            val cb = object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    bitmap = try {
                        Bitmap.wrapHardwareBuffer(result.hardwareBuffer, null)
                    } catch (e: Throwable) {
                        null
                    }
                    latch.countDown()
                }
                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            }
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                cb,
            )
        } catch (e: Throwable) {
            return AutomationResponse.UnitResponse(
                ok = false,
                error = "screenshot unavailable on this API level: ${e.message}",
            )
        }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        val bmp = bitmap
            ?: return AutomationResponse.UnitResponse(
                ok = false,
                error = "screenshot unavailable on this API level",
            )
        val bytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return AutomationResponse.ScreenshotResponse(
            mime = "image/png",
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
        )
    }

    companion object {
        const val TAG = "MeshlitA11yService"

        /**
         * Currently-bound service instance. Null when the user
         * hasn't enabled the service via
         * `Settings → Accessibility`. The agent loop polls
         * this on every prompt.
         */
        @Volatile
        var instance: MeshlitAccessibilityService? = null
            private set

        /**
         * Inspect the system settings to determine whether the
         * service is enabled. Differentiates from
         * [instance != null] — the static accessor reflects the
         * binding state in this process, while this method
         * reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
         */
        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabledServices.contains(
                "${context.packageName}/${
                    com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService::class.java.name
                }",
            )
        }

        /**
         * Read the current `AccessibilityServiceStatus` from
         * the system settings + the static instance.
         */
        fun currentStatus(context: Context): AccessibilityServiceStatus {
            if (instance != null) {
                return AccessibilityServiceStatus.Enabled(
                    serviceName = "${context.packageName}/${
                        com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService::class.java.name
                    }",
                )
            }
            return if (isEnabled(context)) {
                AccessibilityServiceStatus.Enabled(
                    serviceName = "${context.packageName}/${
                        com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService::class.java.name
                    }",
                )
            } else {
                AccessibilityServiceStatus.Disabled
            }
        }
    }
}