package com.meshlit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.meshlit.core.common.logger

/**
 * App entry point. Creates the cluster-node notification channel up front
 * so the foreground service can post its persistent notification without
 * racing the user opening the app.
 */
class MeshlitApplication : Application() {

    private val log = logger("MeshlitApplication")

    override fun onCreate() {
        super.onCreate()
        log.info("app.start", "Meshlit application starting", mapOf("versionName" to BuildConfig.VERSION_NAME))
        createClusterNotificationChannel()
    }

    private fun createClusterNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_CLUSTER,
            getString(R.string.notif_channel_cluster),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_cluster_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_CLUSTER = "cluster_node"
    }
}
