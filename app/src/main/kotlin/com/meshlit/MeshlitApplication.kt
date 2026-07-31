package com.meshlit

import android.app.Application
import android.os.Build
import com.meshlit.core.common.logger
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App entry point. Owns the long-lived singletons that the rest of
 * the app pulls from: notification preferences, notification
 * dispatcher, and (later, in Phase 3) the cluster coordinator.
 *
 * Why singletons on the Application: avoids Hilt setup for Phase 0.5
 * while still letting background services and Compose screens share
 * state. Phase 3 introduces a proper DI container and moves these
 * to @Singleton bindings.
 */
class MeshlitApplication : Application() {

    private val log = logger("MeshlitApplication")

    /** Long-lived scope for IO-bound app-level work (preference writes, channel syncs). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val notificationPreferences: NotificationPreferences by lazy {
        NotificationPreferences(this)
    }

    val notificationCenter: NotificationCenter by lazy {
        NotificationCenter(this, notificationPreferences, appScope)
    }

    override fun onCreate() {
        super.onCreate()
        log.info(
            "app.start", "Meshlit application starting",
            mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "sdkInt" to Build.VERSION.SDK_INT,
            ),
        )
        // Touching notificationCenter triggers lazy initialization,
        // which pre-creates the foreground-service channel. Do it
        // eagerly so the FGS can post without a race.
        notificationCenter.toString()
    }
}