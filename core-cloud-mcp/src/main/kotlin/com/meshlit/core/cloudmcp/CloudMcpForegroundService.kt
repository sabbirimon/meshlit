package com.meshlit.core.cloudmcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Long-lived foreground service that holds the SSE stream open
 * while the user is interacting with the Agent Terminal or has
 * the Cloud Hub on screen. Mirrors
 * [com.meshlit.inference.InferenceForegroundService] —
 * `SupervisorJob() + Dispatchers.Default`, `LocalBinder`, and a
 * `WakeLock` that the system can reclaim.
 *
 * The service is **stateless** — the [CloudMcpCoordinator] lives
 * in [com.meshlit.MeshlitApplication] for the entire process
 * lifetime; the foreground service exists only to (a) keep the
 * SSE stream alive when the user backgrounds the app, and (b)
 * surface a notification while a session is active.
 *
 * Required manifest entry (mirrors InferenceForegroundService at
 * `AndroidManifest.xml:100-103`):
 *
 *     <service
 *         android:name="com.meshlit.core.cloudmcp.CloudMcpForegroundService"
 *         android:foregroundServiceType="dataSync"
 *         android:exported="false" />
 */
class CloudMcpForegroundService : Service() {

    private val log = logger("CloudMcpFGS")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationJob: Job? = null

    inner class LocalBinder : Binder() {
        fun service(): CloudMcpForegroundService = this@CloudMcpForegroundService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Cloud MCP ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                log.info("fgs.stop", "stop requested")
                stopSelf()
            }
            else -> Unit
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        // AGP 9.x — the system calls this on FGS timeout. Mirror
        // InferenceForegroundService: log + release the wake lock.
        log.warn(
            "fgs.timeout",
            "fgs timeout",
            mapOf("startId" to startId, "type" to foregroundServiceType),
        )
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * Acquire a wake lock so the SSE stream survives a brief
     * screen-off. The lock is bounded by [stopActiveSession].
     */
    fun startActiveSession() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "meshlit:cloud-mcp-session",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_SESSION_DURATION_MS)
        }
        updateNotification("Cloud MCP session active")
    }

    fun stopActiveSession() {
        releaseWakeLock()
        updateNotification("Cloud MCP ready")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cloud MCP",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Long-lived cloud MCP sessions"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Meshlit Cloud")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    companion object {
        private const val CHANNEL_ID = "cloud_mcp"
        private const val NOTIFICATION_ID = 7300
        private const val MAX_SESSION_DURATION_MS = 30 * 60 * 1000L // 30 min
        const val ACTION_STOP = "com.meshlit.cloudmcp.STOP"

        fun start(context: Context) {
            val intent = Intent(context, CloudMcpForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CloudMcpForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

/** Suppress unused-import warning on the legacy Notification.Builder path. */
@Suppress("unused")
private fun placeholder() = Unit