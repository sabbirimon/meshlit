package com.meshlit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.meshlit.capability.CapabilityTier
import com.meshlit.core.common.logger
import com.meshlit.power.BatteryOptimizationHelper
import com.meshlit.settings.SettingsRepository

/**
 * App entry point. Creates the cluster-node notification channel up front
 * so the foreground service can post its persistent notification without
 * racing the user opening the app.
 *
 * Also hosts a few process-wide singletons that the rest of the app
 * reaches for without dragging a DI framework through this small code
 * base:
 *
 *  - [capabilityTier] — derived once from [Build.VERSION.SDK_INT]
 *  - [settingsRepository] — DataStore-backed settings (theme, custom GGUF path, …)
 *  - [batteryOptimizationHelper] — wraps the system whitelist + OEM battery screens
 */
class MeshlitApplication : Application() {

    private val log = logger("MeshlitApplication")

    /** Computed once per process. Cheap but not free. */
    val capabilityTier: CapabilityTier by lazy { CapabilityTier.current() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val batteryOptimizationHelper: BatteryOptimizationHelper by lazy { BatteryOptimizationHelper(this) }

    override fun onCreate() {
        super.onCreate()
        log.info(
            "app.start",
            "Meshlit application starting",
            mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "capabilityTier" to capabilityTier.name,
            ),
        )
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
