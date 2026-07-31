package com.meshlit

import android.app.Application
import android.os.Build
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.logger
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.diagnostics.AndroidEGpuProbe
import com.meshlit.diagnostics.AndroidHostOSProbe
import com.meshlit.diagnostics.AndroidOemDetector
import com.meshlit.diagnostics.AndroidPeripheralProbe
import com.meshlit.diagnostics.AndroidSystemProbe
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.settings.DeviceProfileRepository
import com.meshlit.settings.SettingsRepository
import com.meshlit.setup.FirstRunSetupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * App entry point. Owns the long-lived singletons that the rest of
 * the app pulls from: notification preferences, notification
 * dispatcher, settings repository, device profile repository,
 * system probe, host-OS probe.
 *
 * Why singletons on the Application: avoids Hilt setup for Phase 0.5
 * while still letting background services and Compose screens share
 * state. Phase 3 introduces a proper DI container and moves these
 * to @Singleton bindings.
 *
 * onCreate kicks off the system + peripheral probes on the app scope
 * so they're done by the time the user opens Settings → Device.
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

    val deviceProfileRepository: DeviceProfileRepository by lazy {
        DeviceProfileRepository(this)
    }

    val firstRunSetupRepository: FirstRunSetupRepository by lazy {
        FirstRunSetupRepository(this)
    }

    /**
     * Singleton inference coordinator. The foreground service picks
     * this up via the binder; the Jobs screen reads its state via the
     * service binder. We expose it here so future deep-linking flows
     * (e.g. "test prompt" from the Models screen) can fire without
     * starting the service first.
     */
    val inferenceCoordinator: InferenceCoordinator by lazy { InferenceCoordinator() }

    val systemProbe: AndroidSystemProbe by lazy { AndroidSystemProbe(this) }

    val peripheralProbe: AndroidPeripheralProbe by lazy { AndroidPeripheralProbe(this) }

    val egpuProbe: AndroidEGpuProbe by lazy { AndroidEGpuProbe(this) }

    /** Detected host OS (Linux x86 / ChromeOS ARC / Waydroid / stock
     *  Android). Cached after the first probe; refresh on next launch. */
    val hostOSDetection: HostOSDetection by lazy {
        AndroidHostOSProbe().probe()
    }

    /** Convenience: the detected host family without the full evidence. */
    val hostOS: HostOS get() = hostOSDetection.hostOS

    /** Detected OEM (Samsung / Xiaomi / Pixel / HarmonyOS NEXT / ...). */
    val oemDetection by lazy { AndroidOemDetector(this).detect() }

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

        // Kick off the system probe on the app scope. The result lands
        // in the DeviceProfileRepository via updateDetection().
        appScope.launch {
            runSystemProbe()
        }
    }

    /** Public entry point for the device screen's refresh button.
     *  Re-runs the system + peripheral + eGPU probes and updates the
     *  [DeviceProfileRepository] so the UI re-emits. */
    fun runSystemProbePublic() {
        appScope.launch { runSystemProbe() }
    }

    private suspend fun runSystemProbe() {
        try {
            val detection = when (val r = systemProbe.detect()) {
                is com.meshlit.core.common.MeshlitResult.Success -> r.value
                is com.meshlit.core.common.MeshlitResult.Failure -> {
                    log.warn("app.probe.fail", "system probe failed: ${r.error.tag}")
                    return
                }
            }
            val egpus = egpuProbe.probe()
            val usb = peripheralProbe.probeUsb()
            val bt = peripheralProbe.probeBluetooth()
            val peripherals = usb + bt
            val detectionWithGpu = detection.copy(detectedExternalGpu = egpus.firstOrNull())
            deviceProfileRepository.updateDetection(detectionWithGpu, peripherals)
            log.info(
                "app.probe.ok",
                "system probe done",
                mapOf(
                    "model" to detection.model,
                    "soc" to detection.socFamily.tag,
                    "ramMb" to detection.totalRamMb,
                    "egpus" to egpus.size,
                    "peripherals" to peripherals.size,
                ),
            )
        } catch (t: Throwable) {
            log.error("app.probe.crash", "system probe crashed", t)
        }
    }
}