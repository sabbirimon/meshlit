package com.meshlit.sandbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshlit.core.common.logger
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

/** Starts only for an explicit user/allowed-agent VM session; never boot-started. */
class RuntimeForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val host: RuntimeHost get() = GlobalContext.get().get()
    private val log = logger("RuntimeForegroundService")
    private var watcher: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Optional Linux VM", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(this, 1,
            Intent(this, RuntimeForegroundService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Meshlit Linux VM")
            .setContentText("Optional VM active; 30-minute maximum session")
            .setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop VM", stop).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        log.info("runtime.vm.service.start", "VM session service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch { host.stopVm(); stopSelf() }
        } else if (watcher == null) {
            watcher = scope.launch {
                try {
                    while (isActive) {
                        delay(1000)
                        if (!host.maintainVm()) { stopSelf(); break }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    log.warn("runtime.vm.service.failure", "VM supervisor failed")
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        host.onServiceDestroyed()
        log.info("runtime.vm.service.stop", "VM session service stopped")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "meshlit_optional_vm"
        private const val ID = 7705
        private const val STOP = "com.meshlit.runtime.STOP"
    }
}
