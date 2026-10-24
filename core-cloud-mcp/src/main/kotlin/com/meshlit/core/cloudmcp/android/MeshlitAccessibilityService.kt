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
        instance = null
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
        fun result(ok:Boolean,error:String)=AutomationResponse.UnitResponse(ok,if(ok) null else error)
        if(request !is AutomationRequest.OpenApp && request !is AutomationRequest.ListApps &&
            request.targetPackage.isNotBlank() && request.targetPackage!=foregroundPackage())
            return result(false,"Foreground target changed; take a fresh snapshot")
        return when(request) {
            is AutomationRequest.Snapshot -> {
                val root=rootInActiveWindow ?: return result(false,"no active window")
                try { AutomationResponse.SnapshotResponse(AndroidSnapshot(root.packageName?.toString().orEmpty(),
                    root.className?.toString().orEmpty(),System.currentTimeMillis(),listOf(root.toAndroidNode()))) }
                finally {root.recycle()}
            }
            is AutomationRequest.ClickRequest -> {
                if(listOfNotNull(request.text,request.contentDescription,request.resourceId).size!=1)
                    return result(false,"Choose exactly one descriptor")
                val node=findActive { (request.text!=null && it.text?.toString()==request.text) ||
                    (request.contentDescription!=null && it.contentDescription?.toString()==request.contentDescription) ||
                    (request.resourceId!=null && it.viewIdResourceName==request.resourceId) }
                try {result(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Node unavailable or not clickable")}
                finally {node?.recycle()}
            }
            is AutomationRequest.TypeRequest -> {
                val root=rootInActiveWindow
                val node=root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                root?.recycle()
                try {
                    if(node?.isPassword==true) return result(false,"Password input requires a separate human workflow")
                    val args=android.os.Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,request.text.take(8192))}
                    result(node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)==true,"No editable focused node")
                } finally {node?.recycle()}
            }
            is AutomationRequest.BackRequest -> result(performGlobalAction(GLOBAL_ACTION_BACK),"Back unavailable")
            is AutomationRequest.HomeRequest -> result(performGlobalAction(GLOBAL_ACTION_HOME),"Home unavailable")
            is AutomationRequest.OpenApp -> {
                val intent=packageManager.getLaunchIntentForPackage(request.packageName)
                if(intent==null) result(false,"No launcher activity")
                else {startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));result(true,"")}
            }
            is AutomationRequest.ListApps -> AutomationResponse.ListAppsResponse(packageManager.getInstalledApplications(0)
                .filter {request.includeSystem || it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM==0}
                .filter {request.query.isNullOrBlank() || it.packageName.contains(request.query,true) || packageManager.getApplicationLabel(it).contains(request.query,true)}
                .take(256).map{it.packageName})
            is AutomationRequest.WaitForRequest -> {
                val deadline=android.os.SystemClock.elapsedRealtime()+request.timeoutMs.coerceIn(0,30_000)
                var found=false
                do {
                    val node=findActive { (request.text!=null && it.text?.toString()==request.text) ||
                        (request.resourceId!=null && it.viewIdResourceName==request.resourceId) }
                    found=node!=null;node?.recycle()
                    if(found) break
                    Thread.sleep(100)
                } while(android.os.SystemClock.elapsedRealtime()<deadline)
                result(found,"timeout")
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

    private fun AccessibilityNodeInfo.toAndroidNode(depth:Int=0,budget:IntArray=intArrayOf(512)): AndroidNode {
        budget[0]--
        val bounds = android.graphics.Rect().also { getBoundsInScreen(it) }
        val childList = if(depth>=32 || budget[0]<=0) emptyList() else (0 until childCount.coerceAtMost(128)).mapNotNull { i ->
            if(budget[0]<=0) null else getChild(i)?.let{child -> try{child.toAndroidNode(depth+1,budget)}finally{child.recycle()}}
        }
        return AndroidNode(
            className = className?.toString(),
            text = if(isPassword) null else text?.toString()?.take(2048),
            contentDescription = if(isPassword) null else contentDescription?.toString()?.take(2048),
            resourceId = viewIdResourceName,
            bounds = bounds,
            isClickable = isClickable,
            isEditable = isEditable,
            isPassword = isPassword,
            children = childList,
        )
    }

    /** Production accessibility traversal: bounded, detached result owned by caller. */
    private fun findActive(predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        val root=rootInActiveWindow ?: return null
        var visited=0
        fun visit(node:AccessibilityNodeInfo,depth:Int):AccessibilityNodeInfo? {
            if(++visited>512 || depth>32) return null
            if(predicate(node)) return AccessibilityNodeInfo.obtain(node)
            for(i in 0 until node.childCount.coerceAtMost(128)) {
                val child=node.getChild(i) ?: continue
                val found=try{visit(child,depth+1)}finally{child.recycle()}
                if(found!=null) return found
            }
            return null
        }
        return try{visit(root,0)}finally{root.recycle()}
    }
    fun foregroundPackage():String=rootInActiveWindow?.let{root -> try{root.packageName?.toString().orEmpty()}finally{root.recycle()}}.orEmpty()

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