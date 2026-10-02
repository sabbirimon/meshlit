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
import com.meshlit.ui.v2.di.v2CoreModule
import com.meshlit.inference.ClusterStorageInstaller
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.RunAnywhereCatalog
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.parseOtelHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
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

    // -----------------------------------------------------------------
    // USB tether state
    // -----------------------------------------------------------------
    /**
     * Mutable state-flow holding the current USB-NCM / RNDIS tether's
     * identity, or `null` if no USB tether is up. Updated by the
     * `ConnectivityManager` callback wired up in `onCreate()`.
     *
     * Why a `StateFlow` here and not a property in the network
     * monitor screen: the Scan screen also reads it so the user
     * sees a coral "USB tether" row at the top of the peer list
     * while a wired peer is reachable.
     */
    val usbTetherActive: kotlinx.coroutines.flow.MutableStateFlow<com.meshlit.disco.UsbTetherInfo?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    // ---- explicit Koin-backed accessors that pre-date Phase 0.3 ----
    // These are short-hand getters around `koinInject<T>()` for
    // call sites that already hold a `MeshlitApplication` reference
    // (terminal, agent session, FGS, view models, screen helpers).
    val hostOSDetection: HostOSDetection get() = get()
    val hostOS: HostOS get() = get()
    val oemDetection: OemDetectionResult get() = get()
    /**
     * User-overridable display name. Reads the persisted override
     * from `SettingsRepository` and falls back to the auto-derived
     * `DeviceInfo.displayName` when no override is set. The Device
     * Info screen writes back through `SettingsRepository.setDisplayName(...)`.
     *
     * The accessor is a plain (non-suspend) `get()` because the
     * existing `displayName` field was sync; we read the current
     * DataStore value synchronously via `runBlocking { first() }`.
     * Callers that need a hot flow should bind to
     * `settingsRepository.displayNameFlow` instead.
     */
    val displayName: String
        get() = settingsRepository.displayNameFlowNow()
            .ifBlank { get<DeviceInfo>().displayName }
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
    val hookEngine: com.meshlit.agent.hooks.HookEngine get() = get()
    val hookAuditSink: com.meshlit.agent.hooks.HookAuditSink get() = get()
    private val hooksRegistryFlowRef: kotlinx.coroutines.flow.MutableStateFlow<List<com.meshlit.core.common.HookDefinition>>
        get() = get()
    private val hooksEnabledFlowRef: kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        get() = get(named("hooksEnabled"))
    val deviceInfo: DeviceInfo get() = get()
    val bootstrapCoordinator: com.meshlit.core.bootstrap.BootstrapCoordinator get() = get()
    val roleManager: com.meshlit.core.role.RoleManager get() = get()

    // ---- volatile refs (FGS-shared mutable state) ----
    // Each RefHolder binding in [com.meshlit.di.CoreModule] is
    // registered with a unique `named(...)` qualifier because the
    // raw `RefHolder` type collides under JVM generic erasure
    // — three separate `single { RefHolder<...>(...) }` calls
    // would otherwise resolve to the same physical instance.
    private val bundledModelPathRef: RefHolder<File?> get() =
        get(named("bundledModelPath"))
    private val activePeerHealthCacheRef: RefHolder<PeerHealthCache?> get() =
        get(named("activePeerHealthCache"))
    private val stableNodeIdRef: RefHolder<String> get() =
        get(named("stableNodeId"))

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
            modules(coreModule, appModule, v2CoreModule)
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

        // ------------------------------------------------------------------
        // Phase 0.4 — move every synchronous install + every appScope.launch
        // out of the main-thread critical path so MainActivity.onCreate
        // runs immediately and Compose renders a first frame within the
        // 16 ms budget. Previously Application.onCreate did 20+
        // blocking steps inline (Koin resolution of every singleton, all
        // four RunAnywhere engine installs, ClusterStorageInstaller,
        // ContextProvider, etc.); under real launch this took >5 s on
        // cold start, WindowManager gave up after ~3 s with
        //   Choreographer: Skipped 210 frames
        //   OpenGLRenderer: Davey! duration=3545ms
        // and tore down the surface (mViewVisibility=0x8 / surface=0,0).
        //
        // We now do the bare minimum on the calling thread:
        //   1. install the three RunAnywhere engine singletons (cheap,
        //      required before any UI can resolve them through Koin),
        //   2. install the RunAnywhereCatalogEngine with its offline
        //      fallback,
        //   3. install ClusterStorageInstaller,
        //   4. install ContextProvider.
        // Every other step — the RunAnywhere SDK initialize() call
        // (which talks to the native side and can take seconds),
        // AgentCapabilityRegistrar.start(), the system probe, the
        // bundled model extraction, MCP boot, the bootstrap, the peer
        // repository, USB tether callback, the tracing config flow —
        // happens inside a single appScope.launch block below.
        // ------------------------------------------------------------------
        ContextProvider.install(this)
        RunAnywhereVoiceEngine.install()
        RunAnywhereStructuredEngine.install()
        RunAnywhereVisionEngine.install()
        ClusterStorageInstaller.install(this)
        RunAnywhereCatalogEngine.install(offlineFallback = ::catalogFallback)

        val appScope: kotlinx.coroutines.CoroutineScope = get()
        val inferenceCoordinator: InferenceCoordinator = get()
        val notificationCenter: com.meshlit.notifications.NotificationCenter = get()
        notificationCenter.toString()
        inferenceCoordinator.markStarting()

        val runAnywhereEngine = inferenceCoordinator.runAnywhereEngine()
        RunAnywhereCatalog.all.forEach { entry ->
            if (entry.url.isNotBlank()) runAnywhereEngine.setCatalogDownloadUrl(entry.id, entry.url)
        }

        val settingsRepository: SettingsRepository = get()
        val tracingController: TracingController = get()
        settingsRepository.startTracingCache(appScope)
        startHooksFeed(appScope, settingsRepository)

        // Single background launch owning every remaining bootstrap step.
        // Order matters where there's a dependency; otherwise the steps
        // are independent and the launch{} block runs them concurrently
        // with `awaitAll` after the sequential prefix.
        appScope.launch {
            try {
                get<com.meshlit.agent.AgentCapabilityRegistrar>().start()
            } catch (t: Throwable) {
                log.error("app.agent_reg.fail", "AgentCapabilityRegistrar.start failed", t)
            }
            try {
                inferenceCoordinator.runAnywhereEngine().initialize(this@MeshlitApplication)
            } catch (t: Throwable) {
                log.error("app.runanywhere.init.fail", "RunAnywhere initialize failed", t)
            } finally {
                inferenceCoordinator.markInitialized()
            }

            // Independent subsystems run concurrently after the prefix.
            coroutineScope {
                val jobs = listOf(
                    async { runSystemProbe() },
                    async { extractBundledModel() },
                    async { bootMcp() },
                    async { runBootstrap() },
                    async { registerUsbTetherCallback() },
                )
                jobs.joinAll()
            }

            // PeerRepository needs the bootstrap-completed nodeId.
            try {
                val repo: com.meshlit.disco.PeerRepository = get()
                while (nodeIdHex.isBlank()) kotlinx.coroutines.delay(50L)
                val self = com.meshlit.core.discovery.LocalPeerDescriptor(
                    nodeId = nodeIdHex,
                    host = localIpAddress,
                    port = httpServerPort,
                    tierTag = capabilityTier.name,
                    fingerprint = "",
                )
                repo.start(this, self)
            } catch (t: Throwable) {
                log.error("app.peer_repo.fail", "PeerRepository start failed", t)
            }

            // Tracing config flow — stays subscribed for the process lifetime.
            try {
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
            } catch (t: Throwable) {
                log.error("app.tracing.flow.fail", "Tracing reconfigure flow crashed", t)
            }
        }
    }

    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }

    /**
     * Register a [android.net.ConnectivityManager.NetworkCallback] that
     * watches for a USB-NCM / RNDIS interface. When one comes up we
     * flip [usbTetherActive] to a populated [UsbTetherInfo]; when it
     * goes away we flip it back to null.
     *
     * The callback is registered inline so we don't accidentally
     * surface the wiring in the v2 Network monitor screen until
     * the Network monitor milestone. The Scan screen reads
     * [usbTetherActive] directly via [com.meshlit.disco.PeerRepository]'s
     * `usbTetherProvider`.
     */
    private fun registerUsbTetherCallback() {
        val connectivity = getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return
        // On older Android (pre-15) TRANSPORT_USB has the value 8
        // but `addTransportType` rejects it as "Invalid
        // TransportType 8" because the platform's
        // `checkValidTransportType` predates the constant. We
        // tolerate that by wrapping the whole register path in a
        // broad catch so the USB-tether feature gracefully
        // degrades on devices that don't support it instead of
        // crashing the app on every launch.
        try {
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_USB)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build()
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val caps = connectivity.getNetworkCapabilities(network) ?: return
                    val mtu = try {
                        connectivity.getLinkProperties(network)?.mtu ?: 1500
                    } catch (t: Throwable) {
                        1500
                    }
                    val linkMbps = when {
                        caps.linkUpstreamBandwidthKbps > 0 &&
                            caps.linkDownstreamBandwidthKbps > 0 ->
                            (caps.linkDownstreamBandwidthKbps + 999) / 1000
                        else -> 480
                    }
                    usbTetherActive.value = com.meshlit.disco.UsbTetherInfo(
                        host = "usb-net",
                        mtu = mtu,
                        linkMbps = linkMbps,
                    )
                }

                override fun onLost(network: android.net.Network) {
                    usbTetherActive.value = null
                }

                override fun onUnavailable() {
                    usbTetherActive.value = null
                }
            }
            connectivity.registerNetworkCallback(request, callback)
        } catch (t: Throwable) {
            log.warn(
                "app.usb.callback.fail",
                "registerNetworkCallback for TRANSPORT_USB failed: ${t.message}",
            )
        }
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

    /**
     * Phase 8 — wire the persisted hooks registry + master kill
     * switch into the in-process Koin singletons the [HookEngine]
     * and the UI read from. Two collectors run for the process
     * lifetime; the engine's `hooks` and master `enabled` flags are
     * therefore always in sync with DataStore.
     *
     * Called once from `onCreate` on `appScope` so the very first
     * `hookEngine.fire(...)` after process start reflects whatever
     * the user previously saved — without making `onCreate` block
     * on DataStore I/O.
     */
    private fun startHooksFeed(scope: CoroutineScope, settingsRepository: SettingsRepository) {
        scope.launch {
            settingsRepository.hooksRegistryFlow.collect { hooks ->
                hooksRegistryFlowRef.value = hooks
            }
        }
        scope.launch {
            settingsRepository.hooksEnabledFlow.collect { enabled ->
                hooksEnabledFlowRef.value = enabled
            }
        }
    }
}