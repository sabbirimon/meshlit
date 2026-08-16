package com.meshlit

import android.os.Build
import com.meshlit.core.bootstrap.BootstrapCoordinator
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.OemDetectionResult
import com.meshlit.core.inference.ContextProvider
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereStructuredEngine
import com.meshlit.core.inference.RunAnywhereVisionEngine
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.observability.TracingController
import com.meshlit.core.observability.TracingMode
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.LocalTrustPolicy
import com.meshlit.core.trust.TrustTier
import com.meshlit.di.RefHolder
import com.meshlit.di.appModule
import com.meshlit.di.coreModule
import com.meshlit.inference.ClusterStorageInstaller
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.RunAnywhereCatalog
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.parseOtelHeaders
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

/**
 * App entry point. Phase 0.3 — owns the Koin container, registers
 * the application instance as a singleton, and bootstraps the long-
 * running subsystems (engine init, MCP boot, system probe, tracing
 * cache).
 *
 * The class is intentionally small; every process-wide singleton is
 * resolved through Koin and lives in `coreModule` / `appModule`.
 * Callers reach those singletons via `koinInject<T>()` rather than
 * casting `applicationContext as MeshlitApplication` (the only cast
 * that survives lives in `AppModule.kt`, the DI definition site).
 */
class MeshlitApplication : android.app.Application() {

    private val log = AppLoggerFactory.appLogger("MeshlitApplication")

    // ---- explicit Koin-backed accessors that pre-date Phase 0.3 ----
    // These are short-hand getters around `koinInject<T>()` for
    // call sites that already hold a `MeshlitApplication` reference
    // (terminal, agent session, FGS, view models, screen helpers).
    val hostOSDetection: HostOSDetection get() = get()
    val hostOS: HostOS get() = get()
    val oemDetection: OemDetectionResult get() = get()
    val displayName: String get() = get<DeviceInfo>().displayName
    val localIpAddress: String get() = get<DeviceInfo>().localIpAddress
    val httpServerPort: Int get() = 8080
    val nodeIdHex: String get() = stableNodeIdRef.get()
    val capabilityTier: com.meshlit.capability.CapabilityTier get() = get()
    val appScope: kotlinx.coroutines.CoroutineScope get() = get()
    val settingsRepository: SettingsRepository get() = get()
    val deviceProfileRepository: com.meshlit.settings.DeviceProfileRepository get() = get()
    val firstRunSetupRepository: com.meshlit.setup.FirstRunSetupRepository get() = get()
    val notificationCenter: com.meshlit.notifications.NotificationCenter get() = get()
    val notificationPreferences: com.meshlit.notifications.NotificationPreferences get() = get()
    val peerRegistry: com.meshlit.inference.PeerRegistry get() = get()
    val clusterDispatch: com.meshlit.inference.ClusterDispatch get() = get()
    val discoveryCoordinator: com.meshlit.core.discovery.DiscoveryCoordinator get() = get()
    val meshlitFirewall: com.meshlit.core.firewall.MeshlitFirewall get() = get()
    val inferenceCoordinator: InferenceCoordinator get() = get()
    val voiceEngine: RunAnywhereVoiceEngine get() = get()
    val structuredEngine: RunAnywhereStructuredEngine get() = get()
    val visionEngine: RunAnywhereVisionEngine get() = get()
    val catalogEngine: RunAnywhereCatalogEngine get() = get()
    val metricsRegistry: com.meshlit.inference.MetricsRegistry get() = get()
    val logBuffer: com.meshlit.observability.LogBuffer get() = get()
    val tracingController: TracingController get() = get()
    val scriptLibrary: com.meshlit.scripts.ScriptLibrary get() = get()
    val systemProbe: com.meshlit.diagnostics.AndroidSystemProbe get() = get()
    val peripheralProbe: com.meshlit.diagnostics.AndroidPeripheralProbe get() = get()
    val egpuProbe: com.meshlit.diagnostics.AndroidEGpuProbe get() = get()
    val batteryOptimizationHelper: com.meshlit.power.BatteryOptimizationHelper get() = get()
    val agentCapabilities: com.meshlit.agent.AgentCapabilityRegistryHolder get() = get()
    val agentCapabilitiesRegistrar: com.meshlit.agent.AgentCapabilityRegistrar get() = get()
    val agentDispatchers: com.meshlit.agent.AgentCapabilityDispatchers get() = get()
    val mcpToolRegistry: com.meshlit.core.mcp.McpToolRegistry get() = get()
    val mcpClientPool: com.meshlit.core.mcp.McpClientPool get() = get()
    val userMcpServerStore: com.meshlit.core.mcp.UserMcpServerStore get() = get()
    val meshlitServerController: com.meshlit.core.mcp.MeshlitServerController get() = get()
    val cloudCredentialStore: com.meshlit.core.trust.CloudCredentialStore get() = get()
    val cloudHttpClient: okhttp3.OkHttpClient get() = get()
    val cloudCoordinator: com.meshlit.core.cloudmcp.CloudMcpCoordinator get() = get()
    val trustStore: com.meshlit.core.trust.TrustStore get() = get()
    val bundledModelInstaller: com.meshlit.core.inference.BundledModelInstaller get() = get()
    val deviceInfo: DeviceInfo get() = get()
    val bootstrapCoordinator: com.meshlit.core.bootstrap.BootstrapCoordinator get() = get()
    val roleManager: com.meshlit.core.role.RoleManager get() = get()

    // ---- volatile refs (FGS-shared mutable state) ----
    private val bundledModelPathRef: RefHolder<File?> get() = get()
    private val activePeerHealthCacheRef: RefHolder<PeerHealthCache?> get() = get()
    private val stableNodeIdRef: RefHolder<String> get() = get()

    fun bundledModelPath(): File? = bundledModelPathRef.get()
    fun setBundledModelPath(file: File?) { bundledModelPathRef.set(file) }
    fun setActivePeerHealthCache(cache: PeerHealthCache?) { activePeerHealthCacheRef.set(cache) }
    fun activePeerHealthCache(): PeerHealthCache? = activePeerHealthCacheRef.get()

    fun setStableNodeId(id: String) {
        stableNodeIdRef.set(id)
        LocalTrustPolicy.set(
            DeviceTrustPolicy(
                nodeId = id, trustTier = TrustTier.LOCAL_TRUSTED,
                allowedRoles = setOf("brain", "tool", "monitor"),
                tokenExpiryMs = null, publicKeyFingerprint = null,
            ),
        )
    }

    /** Façade over [AgentPromptRunner]. */
    fun runAgentPrompt(providerId: String?, prompt: String) =
        get<AgentPromptRunner>().run(providerId, prompt)

    /** Self-peer cluster snapshot — disk/RAM/shard list. */
    fun selfCapabilities(): PeerCapabilities = get<LocalPeerCapabilitiesResolver>().resolve()

    fun runSystemProbePublic() {
        get<kotlinx.coroutines.CoroutineScope>().launch { runSystemProbe() }
    }

    // ---- lifecycle ----
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
                "capabilityTier" to get<com.meshlit.capability.CapabilityTier>().name,
                "hostOS" to get<HostOS>().tag,
                "hostAbi" to hostOSDetection.abi,
                "hostKernel" to hostOSDetection.kernelVersion,
                "oem" to "Android",
            ),
        )
        val appScope: kotlinx.coroutines.CoroutineScope = get()
        val inferenceCoordinator: InferenceCoordinator = get()
        val notificationCenter: com.meshlit.notifications.NotificationCenter = get()
        notificationCenter.toString()
        inferenceCoordinator.markStarting()
        appScope.launch {
            try { inferenceCoordinator.runAnywhereEngine().initialize(this@MeshlitApplication) }
            finally { inferenceCoordinator.markInitialized() }
        }
        get<com.meshlit.agent.AgentCapabilityRegistrar>().start()
        ContextProvider.install(this)
        RunAnywhereVoiceEngine.install()
        RunAnywhereStructuredEngine.install()
        RunAnywhereVisionEngine.install()
        ClusterStorageInstaller.install(this)
        RunAnywhereCatalogEngine.install(offlineFallback = ::catalogFallback)
        val runAnywhereEngine = inferenceCoordinator.runAnywhereEngine()
        RunAnywhereCatalog.all.forEach { entry ->
            if (entry.url.isNotBlank()) runAnywhereEngine.setCatalogDownloadUrl(entry.id, entry.url)
        }
        appScope.launch { runSystemProbe() }
        appScope.launch { extractBundledModel() }
        appScope.launch { bootMcp() }
        // Phase 0.1 — resolve the stable node id + hot-load feature
        // flags. Persists the node id immediately on first boot (Fix 4).
        appScope.launch { runBootstrap() }
        val settingsRepository: SettingsRepository = get()
        val tracingController: TracingController = get()
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

    // ---- private helpers ----
    private fun catalogFallback(): List<RunAnywhereCatalogEngine.Entry> =
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

    private fun com.meshlit.settings.TracingMode.toCoreTracingMode(): TracingMode = when (this) {
        com.meshlit.settings.TracingMode.Off -> TracingMode.Off
        com.meshlit.settings.TracingMode.Local -> TracingMode.Local
        com.meshlit.settings.TracingMode.Otel -> TracingMode.Otel
    }

    private suspend fun runSystemProbe() {
        try {
            val systemProbe: com.meshlit.diagnostics.AndroidSystemProbe = get()
            val egpuProbe: com.meshlit.diagnostics.AndroidEGpuProbe = get()
            val peripheralProbe: com.meshlit.diagnostics.AndroidPeripheralProbe = get()
            val deviceProfileRepository: com.meshlit.settings.DeviceProfileRepository = get()
            val detection = when (val r = systemProbe.detect()) {
                is MeshlitResult.Success -> r.value
                is MeshlitResult.Failure -> {
                    log.warn("app.probe.fail", "system probe failed: ${r.error.tag}"); return
                }
            }
            val egpus = egpuProbe.probe()
            val peripherals = peripheralProbe.probeUsb() + peripheralProbe.probeBluetooth()
            val detectionWithGpu = detection.copy(detectedExternalGpu = egpus.firstOrNull())
            deviceProfileRepository.updateDetection(detectionWithGpu, peripherals)
            log.info("app.probe.ok", "system probe done", mapOf(
                "model" to detection.model, "soc" to detection.socFamily.tag,
                "ramMb" to detection.totalRamMb,
                "egpus" to egpus.size, "peripherals" to peripherals.size,
            ))
        } catch (t: Throwable) {
            log.error("app.probe.crash", "system probe crashed", t)
        }
    }

    private suspend fun extractBundledModel() {
        try {
            val file = get<com.meshlit.core.inference.BundledModelInstaller>().ensureInstalled(this)
            setBundledModelPath(file)
            if (file != null) log.info("app.bundled.ready", "bundled model ready", mapOf(
                "path" to file.absolutePath, "bytes" to file.length()))
        } catch (t: Throwable) {
            log.error("app.bundled.fail", "bundled model extraction failed", t)
            setBundledModelPath(null)
        }
    }

    private suspend fun bootMcp() {
        try {
            val userMcpServerStore: com.meshlit.core.mcp.UserMcpServerStore = get()
            val mcpClientPool: com.meshlit.core.mcp.McpClientPool = get()
            val meshlitServerController: com.meshlit.core.mcp.MeshlitServerController = get()
            userMcpServerStore.rehydrate()
            userMcpServerStore.applyTo(mcpClientPool)
            val res = meshlitServerController.start()
            log.info("app.mcp.boot", "MCP subsystem ready", mapOf(
                "serverState" to meshlitServerController.state.value::class.java.simpleName,
                "userServers" to userMcpServerStore.all.size,
                "startOk" to (res is MeshlitResult.Success),
            ))
        } catch (t: Throwable) {
            log.error("app.mcp.boot.fail", "MCP bootstrap failed", t)
        }
    }

    /**
     * Phase 0.1 bootstrap. Resolves the stable node id and hot-loads
     * feature flags. The id is written to DataStore before being
     * exposed anywhere (Fix 4), so the local trust policy + gossip
     * membership see the same identity across restarts.
     */
    private suspend fun runBootstrap() {
        try {
            val coordinator: BootstrapCoordinator = get()
            val snapshotHolder: com.meshlit.bootstrap.BootstrapSnapshotProvider = get()
            when (val res = coordinator.boot()) {
                is MeshlitResult.Success -> {
                    val snap = res.value
                    snapshotHolder.publish(snap)
                    setStableNodeId(snap.nodeId)
                    log.info(
                        "app.bootstrap.ok",
                        "dynamic foundation bootstrap complete",
                        mapOf(
                            "nodeId" to snap.nodeId,
                            "phases" to snap.report.entries.joinToString { e ->
                                "${e.phase}=${e.outcome}"
                            },
                            "flagCount" to snap.flags.size,
                        ),
                    )
                }
                is MeshlitResult.Failure ->
                    log.error("app.bootstrap.fail", "dynamic foundation bootstrap failed: ${res.error.tag}")
            }
        } catch (t: Throwable) {
            log.error("app.bootstrap.crash", "dynamic foundation bootstrap crashed", t)
        }
    }
}