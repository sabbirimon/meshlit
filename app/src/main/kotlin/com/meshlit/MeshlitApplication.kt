package com.meshlit

import android.os.Build
import com.meshlit.core.cloudmcp.CloudMcpCoordinator
import com.meshlit.core.inference.ContextProvider
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereStructuredEngine
import com.meshlit.core.inference.RunAnywhereVisionEngine
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.mcp.McpClientPool
import com.meshlit.core.mcp.MeshlitServerController
import com.meshlit.core.mcp.UserMcpServerStore
import com.meshlit.core.observability.TracingController
import com.meshlit.core.observability.TracingMode
import com.meshlit.core.trust.CloudCredentialStore
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.TrustStore
import com.meshlit.core.trust.TrustTier
import com.meshlit.core.trust.LocalTrustPolicy
import com.meshlit.capability.CapabilityTier
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.OemDetectionResult
import com.meshlit.core.common.MeshlitResult
import com.meshlit.agent.AgentCapabilityRegistrar
import com.meshlit.agent.AgentCapabilityDispatchers
import com.meshlit.agent.AgentCapabilityRegistryHolder
import com.meshlit.core.firewall.MeshlitFirewall
import com.meshlit.core.discovery.DiscoveryCoordinator
import com.meshlit.inference.ClusterDispatch
import com.meshlit.inference.ClusterStorageInstaller
import com.meshlit.inference.MetricsRegistry
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.PeerRegistry
import com.meshlit.inference.RunAnywhereCatalog
import com.meshlit.diagnostics.AndroidEGpuProbe
import com.meshlit.diagnostics.AndroidPeripheralProbe
import com.meshlit.diagnostics.AndroidSystemProbe
import com.meshlit.di.RefHolder
import com.meshlit.di.appModule
import com.meshlit.di.coreModule
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.observability.LogBuffer
import com.meshlit.power.BatteryOptimizationHelper
import com.meshlit.scripts.ScriptLibrary
import com.meshlit.settings.DeviceProfileRepository
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.parseOtelHeaders
import com.meshlit.setup.FirstRunSetupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

/**
 * App entry point. Phase 0.3 — owns the Koin container and
 * delegates every singleton to it. The class is still typed as
 * `MeshlitApplication` so existing call sites that do
 * `applicationContext as MeshlitApplication` keep working; the
 * properties they read are thin wrappers around
 * `getKoin().get<T>()`, not `by lazy { ... }` delegates.
 *
 * Volatile refs survive the migration as `RefHolder` singletons.
 */
class MeshlitApplication : android.app.Application() {

    private val log = AppLoggerFactory.appLogger("MeshlitApplication")

    // -------------------- lazy Koin-backed properties --------------------
    val appScope: CoroutineScope get() = get()
    val capabilityTier: CapabilityTier get() = get()
    val notificationPreferences: NotificationPreferences get() = get()
    val notificationCenter: NotificationCenter get() = get()
    val settingsRepository: SettingsRepository get() = get()
    val deviceProfileRepository: DeviceProfileRepository get() = get()
    val firstRunSetupRepository: FirstRunSetupRepository get() = get()
    val batteryOptimizationHelper: BatteryOptimizationHelper get() = get()
    val peerRegistry: PeerRegistry get() = get()
    val clusterDispatch: ClusterDispatch get() = get()
    val discoveryCoordinator: DiscoveryCoordinator get() = get()
    val meshlitFirewall: MeshlitFirewall get() = get()
    val agentCapabilities: AgentCapabilityRegistryHolder get() = get()
    val agentDispatchers: AgentCapabilityDispatchers get() = get()
    val inferenceCoordinator: InferenceCoordinator get() = get()
    val voiceEngine: RunAnywhereVoiceEngine get() = get()
    val structuredEngine: RunAnywhereStructuredEngine get() = get()
    val visionEngine: RunAnywhereVisionEngine get() = get()
    val catalogEngine: RunAnywhereCatalogEngine get() = get()
    val metricsRegistry: MetricsRegistry get() = get()
    val logBuffer: LogBuffer get() = get()
    val tracingController: TracingController get() = get()
    val scriptLibrary: ScriptLibrary get() = get()
    val systemProbe: AndroidSystemProbe get() = get()
    val peripheralProbe: AndroidPeripheralProbe get() = get()
    val egpuProbe: AndroidEGpuProbe get() = get()
    val hostOSDetection: HostOSDetection get() = get()
    val hostOS: HostOS get() = get()
    val oemDetection: OemDetectionResult get() = get()
    val mcpToolRegistry get() = get<com.meshlit.core.mcp.McpToolRegistry>()
    val mcpClientPool: McpClientPool get() = get()
    val userMcpServerStore: UserMcpServerStore get() = get()
    val meshlitServerController: MeshlitServerController get() = get()
    val agentCapabilitiesRegistrar: AgentCapabilityRegistrar get() = get()
    val cloudCredentialStore: CloudCredentialStore get() = get()
    val cloudHttpClient: okhttp3.OkHttpClient get() = get()
    val cloudCoordinator: CloudMcpCoordinator get() = get()
    val trustStore: TrustStore get() = get()
    val bundledModelInstaller: com.meshlit.core.inference.BundledModelInstaller get() = get()
    val deviceInfo: DeviceInfo get() = get()

    // -------------------- volatile refs --------------------
    private val bundledModelPathRef: RefHolder<File?> get() = get()
    private val activePeerHealthCacheRef: RefHolder<PeerHealthCache?> get() = get()
    private val stableNodeIdRef: RefHolder<String> get() = get()

    fun bundledModelPath(): File? = bundledModelPathRef.get()
    fun setBundledModelPath(file: File?) { bundledModelPathRef.set(file) }
    fun setActivePeerHealthCache(cache: PeerHealthCache?) { activePeerHealthCacheRef.set(cache) }
    fun activePeerHealthCache(): PeerHealthCache? = activePeerHealthCacheRef.get()
    val nodeIdHex: String get() = stableNodeIdRef.get()

    fun setStableNodeId(id: String) {
        stableNodeIdRef.set(id)
        LocalTrustPolicy.set(
            DeviceTrustPolicy(
                nodeId = id,
                trustTier = TrustTier.LOCAL_TRUSTED,
                allowedRoles = setOf("brain", "tool", "monitor"),
                tokenExpiryMs = null,
                publicKeyFingerprint = null,
            ),
        )
    }

    val httpServerPort: Int get() = 8080

    /** Human-readable name for this device — used in pairing QR codes. */
    val displayName: String get() = deviceInfo.displayName

    /** Best-effort local IPv4 address. */
    val localIpAddress: String get() = deviceInfo.localIpAddress

    // -------------------- thin delegating façade --------------------
    /**
     * Run a single agent prompt against the user's chosen LLM
     * endpoint. Pure façade — the work happens on `appScope` and
     * the events flow through `cloudCoordinator.events`.
     */
    fun runAgentPrompt(providerId: String?, prompt: String) {
        get<AgentPromptRunner>().run(providerId, prompt)
    }

    /**
     * Snapshot the *self* peer's cluster-relevant state — what's
     * free on disk, what's free in RAM, and which shards we
     * already host.
     */
    fun selfCapabilities(): PeerCapabilities = get<LocalPeerCapabilitiesResolver>().resolve()

    // -------------------- onCreate --------------------
    override fun onCreate() {
        super.onCreate()
        AppLoggerFactory.install()
        startKoin {
            androidContext(this@MeshlitApplication)
            modules(coreModule, appModule)
        }
        log.info(
            "app.start", "Meshlit application starting",
            mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "sdkInt" to Build.VERSION.SDK_INT,
                "capabilityTier" to capabilityTier.name,
                "hostOS" to hostOS.tag,
                "hostAbi" to hostOSDetection.abi,
                "hostKernel" to hostOSDetection.kernelVersion,
                "oem" to "Android",
            ),
        )
        notificationCenter.toString()
        inferenceCoordinator.markStarting()
        appScope.launch {
            try {
                inferenceCoordinator.runAnywhereEngine().initialize(this@MeshlitApplication)
            } finally {
                inferenceCoordinator.markInitialized()
            }
        }
        agentCapabilitiesRegistrar.start()
        ContextProvider.install(this)
        RunAnywhereVoiceEngine.install()
        RunAnywhereStructuredEngine.install()
        RunAnywhereVisionEngine.install()
        ClusterStorageInstaller.install(this)
        RunAnywhereCatalogEngine.install(offlineFallback = {
            RunAnywhereCatalog.all.map { entry ->
                RunAnywhereCatalogEngine.Entry(
                    id = entry.id, displayName = entry.displayName,
                    origin = entry.origin, license = entry.license,
                    family = entry.family, approxSizeMb = entry.approxSizeMb,
                    language = entry.language, strengths = entry.strengths,
                    architecture = entry.architecture, quant = entry.quant,
                    sizeClass = entry.sizeClass, bundled = entry.bundled,
                )
            }
        })
        val runAnywhereEngine = inferenceCoordinator.runAnywhereEngine()
        RunAnywhereCatalog.all.forEach { entry ->
            if (entry.url.isNotBlank()) {
                runAnywhereEngine.setCatalogDownloadUrl(entry.id, entry.url)
            }
        }
        appScope.launch { runSystemProbe() }
        appScope.launch { extractBundledModel() }
        appScope.launch { bootMcp() }
        // Phase 0.2 — single tracing source of truth. The cache
        // fields populated by `startTracingCache()` give us a
        // synchronous read of the persisted values without
        // `runBlocking` on the main thread, and the `combine`
        // collector handles every subsequent change from a single
        // subscription. The `combine` flow emits the current
        // values of all three upstream flows on first collection,
        // so the initial reconfigure lands on the collector thread
        // instead of duplicating it on the main thread.
        settingsRepository.startTracingCache(appScope)
        appScope.launch {
            combine(
                settingsRepository.tracingModeFlow,
                settingsRepository.tracingOtelEndpointFlow,
                settingsRepository.tracingOtelHeadersFlow,
            ) { mode, endpoint, headers -> Triple(mode, endpoint, headers) }
                .collect { (mode, endpoint, headers) ->
                    tracingController.reconfigure(
                        mode = mode.toCoreTracingMode(),
                        otlpEndpoint = endpoint,
                        otlpHeaders = parseOtelHeaders(headers),
                    )
                }
        }
    }

    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }

    private fun com.meshlit.settings.TracingMode.toCoreTracingMode(): TracingMode = when (this) {
        com.meshlit.settings.TracingMode.Off -> TracingMode.Off
        com.meshlit.settings.TracingMode.Local -> TracingMode.Local
        com.meshlit.settings.TracingMode.Otel -> TracingMode.Otel
    }

    private suspend fun bootMcp() {
        try {
            userMcpServerStore.rehydrate()
            userMcpServerStore.applyTo(mcpClientPool)
            val res = meshlitServerController.start()
            log.info(
                "app.mcp.boot",
                "MCP subsystem ready",
                mapOf(
                    "serverState" to meshlitServerController.state.value::class.java.simpleName,
                    "userServers" to userMcpServerStore.all.size,
                    "startOk" to (res is MeshlitResult.Success),
                ),
            )
        } catch (t: Throwable) {
            log.error("app.mcp.boot.fail", "MCP bootstrap failed", t)
        }
    }

    private suspend fun extractBundledModel() {
        try {
            val file = get<com.meshlit.core.inference.BundledModelInstaller>().ensureInstalled(this)
            setBundledModelPath(file)
            if (file != null) {
                log.info(
                    "app.bundled.ready", "bundled model ready for FGS auto-load",
                    mapOf("path" to file.absolutePath, "bytes" to file.length()),
                )
            }
        } catch (t: Throwable) {
            log.error("app.bundled.fail", "bundled model extraction failed", t)
            setBundledModelPath(null)
        }
    }

    fun runSystemProbePublic() {
        appScope.launch { runSystemProbe() }
    }

    private suspend fun runSystemProbe() {
        try {
            val detection = when (val r = systemProbe.detect()) {
                is MeshlitResult.Success -> r.value
                is MeshlitResult.Failure -> {
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
                "app.probe.ok", "system probe done",
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
