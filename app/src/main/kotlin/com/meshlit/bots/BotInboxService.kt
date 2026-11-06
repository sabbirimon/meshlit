package com.meshlit.bots

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import org.koin.core.context.GlobalContext

class BotInboxService : Service() {
    private val inbox get() = GlobalContext.get().get<BotInbox>()

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("meshlit-bot-inbox", "Bot inbox", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 0, Intent(this, BotInboxService::class.java).setAction("stop"), flags)
        val notice = NotificationCompat.Builder(this, "meshlit-bot-inbox")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Meshlit bot inbox")
            .setContentText("Receiving signed messages for your review")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(709, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(709, notice)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") stopSelfResult(startId)
        else try { inbox.open() } catch (_: Exception) { stopSelfResult(startId) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        inbox.closed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
