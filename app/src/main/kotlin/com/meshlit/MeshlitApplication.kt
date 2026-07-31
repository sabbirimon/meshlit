package com.meshlit

import android.app.Application
import android.os.Build
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.logger
import com.meshlit.diagnostics.AndroidHostOSProbe
import com.meshlit.diagnostics.AndroidOemDetector
import com.meshlit.diagnostics.AndroidSystemProbe
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * App entry point. Owns the long-lived singletons that the rest of
 * the app pulls from: notification preferences, notification
 * dispatcher, settings repository, system probe, host-OS probe.
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

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    /** Detected host OS (Linux x86 / ChromeOS ARC / Waydroid / stock
     *  Android). Cached after the first probe; refresh on next launch. */
    val hostOSDetection: HostOSDetection by lazy {
        AndroidHostOSProbe().probe()
    }

    /** Convenience: the detected host family without the full evidence. */
    val hostOS: HostOS get() = hostOSDetection.hostOS

    /** Detected OEM (Samsung / Xiaomi / Pixel / HarmonyOS NEXT / ...). */
    val oemDetection by lazy { AndroidOemDetector(this).detect() }

    /** Lazy system probe so first-run setup can read device identity. */
    val systemProbe: AndroidSystemProbe by lazy { AndroidSystemProbe(this) }

    override fun onCreate() {
        super.onCreate()
        log.info(
            "app.start", "Meshlit application starting",
            mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "sdkInt" to Build.VERSION.SDK_INT,
                "hostOS" to hostOS.tag,
                "hostAbi" to hostOSDetection.abi,
                "hostKernel" to hostOSDetection.kernelVersion,
                "oem" to oemDetection.profile.tag,
            ),
        )
        // Touching notificationCenter triggers lazy initialization,
        // which pre-creates the foreground-service channel. Do it
        // eagerly so the FGS can post without a race.
        notificationCenter.toString()
    }
}