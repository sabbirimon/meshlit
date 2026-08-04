package com.meshlit

import android.app.Application
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.capability.CapabilityTier
import com.meshlit.capability.currentCapabilityTier
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.logger
import com.meshlit.core.inference.BundledModelInstaller
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.diagnostics.AndroidEGpuProbe
import com.meshlit.diagnostics.AndroidHostOSProbe
import com.meshlit.diagnostics.AndroidOemDetector
import com.meshlit.inference.MetricsRegistry
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.observability.LogBuffer
import com.meshlit.diagnostics.AndroidPeripheralProbe
import com.meshlit.diagnostics.AndroidSystemProbe
import com.meshlit.inference.PeerRegistry
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.power.BatteryOptimizationHelper
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
 * system probe, host-OS probe, capability tier, battery helper.
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

    private val log = AppLoggerFactory.appLogger("MeshlitApplication")

    /** Long-lived scope for IO-bound app-level work (preference writes, channel syncs). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Computed once per process. Cheap but not free. */
    val capabilityTier: CapabilityTier by lazy { currentCapabilityTier() }

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

    val batteryOptimizationHelper: BatteryOptimizationHelper by lazy {
        BatteryOptimizationHelper(this)
    }

    /** Backing DataStore for the forwarding-peer registry. Shared by
     *  the FGS (writer + reader on the network path) and the Settings
     *  → Network → Forwarding peers screen (user edits).
     *
     *  The `preferencesDataStore(...)` factory returns a `ReadOnlyProperty`
     *  delegate that's designed to be used with Kotlin's `by` syntax on
     *  a receiver property. To invoke it directly we wrap it through a
     *  private delegated field; the public [peerDataStore] surfaces the
     *  resolved `DataStore` instance to the rest of the app. */
    /** Resolved DataStore<Preferences> for the forwarding-peer registry.
     *
     *  `preferencesDataStore(...)` returns a `ReadOnlyProperty` delegate
     *  intended for use with Kotlin's `by` syntax on a receiver property.
     *  We hold it on a private top-level-style field via `by`, then
     *  expose the resolved instance. */
    private val peerDataStoreDelegate: DataStore<Preferences> by preferencesDataStore(name = "meshlit_forward_peers")

    /** Public accessor for the resolved [DataStore]. */
    val peerDataStore: DataStore<Preferences> get() = peerDataStoreDelegate

    /** Peer registry singleton. Bound to [peerDataStore]. */
    val peerRegistry: PeerRegistry by lazy { PeerRegistry(peerDataStore) }

    /**
     * Singleton inference coordinator. The foreground service picks
     * this up via the binder; the Jobs screen reads its state via the
     * service binder. We expose it here so future deep-linking flows
     * (e.g. "test prompt" from the Models screen) can fire without
     * starting the service first.
     */
    val inferenceCoordinator: InferenceCoordinator by lazy { InferenceCoordinator() }

    /**
     * Process-wide metrics counters. Phase M — read by the
     * `/v1/health` enricher and the `MetricsScreen` UI.
     */
    val metricsRegistry: MetricsRegistry by lazy { MetricsRegistry() }

    /**
     * Process-wide in-memory log ring buffer. Backed by SLF4J +
     * logback via [com.meshlit.observability.LogBufferAppender].
     * Read by `LogScreen`; exportable to file via SAF.
     */
    val logBuffer: com.meshlit.observability.LogBuffer by lazy {
        com.meshlit.observability.AppLoggerFactory.install()
        com.meshlit.observability.AppLoggerFactory.buffer
    }

    /**
     * One-shot installer that streams the bundled GGUF out of the APK
     * assets into `filesDir/bundled-models/`. Exposed as a singleton
     * so both the [InferenceForegroundService] (auto-load path) and
     * `ModelsScreen` (manual re-extract button) share the same
     * sentinel-aware logic.
     */
    val bundledModelInstaller: BundledModelInstaller by lazy { BundledModelInstaller() }

    /**
     * Resolved path to the bundled GGUF once extraction completes.
     * `null` until `ensureInstalled()` finishes (or fails). The FGS
     * reads this when deciding whether to auto-load on startup.
     */
    @Volatile private var bundledModelPathRef: java.io.File? = null

    fun bundledModelPath(): java.io.File? = bundledModelPathRef

    /** Called by the extraction job after a successful ensure. */
    fun setBundledModelPath(file: java.io.File?) {
        bundledModelPathRef = file
    }

    /**
     * Script library — saved `ConfigScript` snapshots the user can
     * run, edit, and remote-dispatch. Stored in memory for v1; a
     * DataStore-backed persistence layer will arrive in Phase C.4.
     */
    val scriptLibrary: com.meshlit.scripts.ScriptLibrary by lazy {
        com.meshlit.scripts.ScriptLibrary()
    }

    /**
     * PeerHealthCache live reference. Set by the FGS as it boots;
     * null before the FGS has come up (no peers to cache yet).
     * Read by `MetricsScreen` so the screen reflects the current
     * cluster regardless of which FGS instance owns the cache.
     */
    @Volatile private var activePeerHealthCacheRef: com.meshlit.inference.PeerHealthCache? = null

    fun setActivePeerHealthCache(cache: com.meshlit.inference.PeerHealthCache?) {
        activePeerHealthCacheRef = cache
    }

    fun activePeerHealthCache(): com.meshlit.inference.PeerHealthCache? = activePeerHealthCacheRef

    val systemProbe: AndroidSystemProbe by lazy { AndroidSystemProbe(this) }

    val peripheralProbe: AndroidPeripheralProbe by lazy { AndroidPeripheralProbe(this) }

    val egpuProbe: AndroidEGpuProbe by lazy { AndroidEGpuProbe(this) }

    // -------------------------------------------------------------------
    // Network-scope feature (Phase 2.6)
    //
    // The Devices screen reads these to render this phone's own pairing
    // QR code and the "use this address" hint. They are deliberately
    // cheap to compute and tolerate being unset (we just default to
    // empty strings; the user can still paste the address manually).
    // -------------------------------------------------------------------

    /** Human-readable name for this device — used in pairing QR codes
     *  and the Devices list. Falls back to the device model. */
    val displayName: String by lazy {
        val model = runCatching {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.Build.MODEL ?: "Meshlit"
            } else {
                android.os.Build.MODEL ?: "Meshlit"
            }
        }.getOrDefault("Meshlit")
        "Meshlit/$model"
    }

    /** Best-effort local IPv4 address. Returns empty string when the
     *  device is offline or only has IPv6 — the QR sheet then shows a
     *  placeholder and the user can paste the address manually. */
    val localIpAddress: String by lazy { resolveLocalIpv4() }

    /** Port the FGS opens its HTTP/SSE server on. Defaults to 8080. */
    val httpServerPort: Int get() = 8080

    /** Stable 64-bit hex node id. Generated once per install (saved
     *  to DataStore by FGS so it survives reboots). Empty until the
     *  FGS has finished the first-run probe. */
    val nodeIdHex: String get() = stableNodeId

    @Volatile private var stableNodeId: String = ""

    fun setStableNodeId(id: String) {
        stableNodeId = id
    }

    private fun resolveLocalIpv4(): String {
        return runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = iface.inetAddresses?.toList().orEmpty()
                for (addr in addrs) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            ""
        }.getOrDefault("")
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

    override fun onCreate() {
        super.onCreate()
        // Install the in-memory log buffer first so any log line we
        // emit below also lands in it for `LogScreen`.
        AppLoggerFactory.install()
        log.info(
            "app.start", "Meshlit application starting",
            mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "sdkInt" to Build.VERSION.SDK_INT,
                "capabilityTier" to capabilityTier.name,
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

        // Phase 2.x — Register the RunAnywhere SDK at process start
        // so the Jobs screen can route GGUF loads to a real on-device
        // LLM runtime rather than the placeholder stub. The init call
        // is idempotent and synchronous; safe to run inline in onCreate
        // because it just plugs a handful of jars + native libs into
        // the SDK's static state and does not yet touch the network.
        inferenceCoordinator.runAnywhereEngine().initialize(this)

        // Kick off the system probe + bundled-model extraction on the
        // app scope. Both run in parallel; the FGS reads the cached
        // model path once the FGS binds. We don't block app start on
        // the 940 MB extraction — that would freeze the launcher for
        // 5–15 seconds on a fresh install.
        appScope.launch {
            runSystemProbe()
        }
        appScope.launch {
            extractBundledModel()
        }
    }

    /**
     * Stream the bundled GGUF out of `assets/models/` into
     * `filesDir/bundled-models/`. Idempotent (sentinel-checked);
     * cheap on subsequent launches. Result is cached on the
     * application singleton so the FGS can auto-load on first
     * generation.
     */
    private suspend fun extractBundledModel() {
        try {
            val file = bundledModelInstaller.ensureInstalled(this)
            setBundledModelPath(file)
            if (file != null) {
                log.info(
                    "app.bundled.ready",
                    "bundled model ready for FGS auto-load",
                    mapOf(
                        "path" to file.absolutePath,
                        "bytes" to file.length(),
                    ),
                )
            }
        } catch (t: Throwable) {
            log.error("app.bundled.fail", "bundled model extraction failed", t)
            setBundledModelPath(null)
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