package com.meshlit.feature.ghosty

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.meshlit.core.common.logger
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the Ghosty floating overlay.
 *
 * Why a foreground service: Android 12+ blocks background
 * activities from drawing over other apps. The bubble must keep
 * running while the user moves between apps, so the service uses
 * the `specialUse` foreground-service type with subtype
 * `floating_chat` declared in the manifest. The system has
 * stable rules for this kind of overlay.
 *
 * The service owns:
 *  - a [WindowManager.LayoutParams] for the bubble surface
 *  - a [ComposeView] that renders [GhostyBubble]
 *  - the lifecycle plumbing so the ComposeView's collect
 *    coroutines don't leak
 */
class GhostyOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val log = logger("GhostyOverlayService")

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var bubbleView: ComposeView? = null
    private val expanded = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (bubbleView == null) {
            attachBubble()
        }
        registry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    override fun onDestroy() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val channel = ensureChannel()
        val notif: Notification = Notification.Builder(this, channel)
            .setContentTitle("Ghosty overlay")
            .setContentText("Floating chat is on.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun ensureChannel(): String {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return CHANNEL_ID
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ghosty overlay",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the floating chat bubble alive."
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
        return CHANNEL_ID
    }

    private fun attachBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            y = 240
        }
        val view = ComposeView(this).apply {
            setContent {
                GhostyOverlayRoot(
                    onTap = { expanded.value = true },
                )
            }
        }
        bubbleView = view
        runCatching { windowManager.addView(view, params) }
            .onFailure { log.error("ghosty.attach.fail", "addView threw", it) }
        // When expanded, swap the bubble for the full-screen chat.
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { expanded.value }.collect { isExpanded ->
                if (isExpanded) {
                    attachExpanded()
                } else {
                    removeExpanded()
                }
            }
        }
    }

    private var expandedView: ComposeView? = null
    private fun attachExpanded() {
        if (expandedView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val view = ComposeView(this).apply {
            setContent {
                GhostyExpandedScreen(
                    accent = Color(0xFFFF7A1A),
                    accentDim = Color(0xFFCC5F0F),
                    onClose = { expanded.value = false },
                )
            }
        }
        expandedView = view
        runCatching { windowManager.addView(view, params) }
            .onFailure { log.error("ghosty.expanded.attach.fail", "addView threw", it) }
    }

    private fun removeExpanded() {
        val view = expandedView ?: return
        runCatching { windowManager.removeView(view) }
        expandedView = null
    }

    companion object {
        const val CHANNEL_ID = "meshlit_ghosty_overlay"
        const val NOTIF_ID = 0x6753

        fun start(context: Context) {
            val intent = Intent(context, GhostyOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GhostyOverlayService::class.java))
        }
    }
}

@Composable
private fun GhostyOverlayRoot(onTap: () -> Unit) {
    GhostyBubble(
        accent = Color(0xFFFF7A1A),
        opacity = 0.85f,
        onTap = onTap,
    )
}