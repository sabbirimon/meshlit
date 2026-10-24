package com.meshlit.di

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.AgentPromptRunner
import com.meshlit.DeviceInfo
import com.meshlit.LocalPeerCapabilitiesResolver
import com.meshlit.core.cloudmcp.CloudMcpCoordinator
import com.meshlit.core.cloudmcp.llm.NaraRouterClient
import com.meshlit.core.cloudmcp.rag.LocalRagStore
import com.meshlit.core.cloudmcp.rag.RagBackendSelectionPolicy
import com.meshlit.core.cloudmcp.rag.RemoteRagStore
import com.meshlit.core.bootstrap.BootstrapCoordinator
import com.meshlit.core.config.ConfigRepository
import com.meshlit.core.flags.FeatureFlagRegistry
import com.meshlit.core.lifecycle.ManagedService
import com.meshlit.core.lifecycle.ServiceLifecycleController
import com.meshlit.core.probe.BatteryProfiler
import com.meshlit.core.probe.CpuProfiler
import com.meshlit.core.probe.HardwareProfiler
import com.meshlit.core.probe.HardwareProfilerRegistry
import com.meshlit.core.probe.MemoryProfiler
import com.meshlit.core.probe.NetworkProfiler
import com.meshlit.core.probe.NpuProfiler
import com.meshlit.core.probe.ThermalProfiler
import com.meshlit.core.registry.LocalServiceRegistry
import com.meshlit.core.registry.ServiceRegistry
import com.meshlit.core.role.RoleManager
import com.meshlit.registry.McpServerStub
import com.meshlit.core.discovery.DiscoveryCoordinator
import com.meshlit.core.discovery.NsdDiscoveryTransport
import com.meshlit.core.firewall.MeshlitFirewall
import com.meshlit.core.mcp.McpClientPool
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.MeshlitServerController
import com.meshlit.core.mcp.UserMcpServerStore
import com.meshlit.core.trust.CloudCredentialStore
import com.meshlit.core.trust.FileBackedTrustStore
import com.meshlit.core.trust.LocalTrustPolicy
import com.meshlit.core.trust.TrustStore
import com.meshlit.core.inference.BundledModelInstaller
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereStructuredEngine
import com.meshlit.core.inference.RunAnywhereVisionEngine
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.core.observability.TracingController
import com.meshlit.core.observability.TracerHolder
import com.meshlit.core.observability.TraceSink
import com.meshlit.core.observability.LogSource
import com.meshlit.diagnostics.AndroidEGpuProbe
import com.meshlit.diagnostics.AndroidHostOSProbe
import com.meshlit.diagnostics.AndroidOemDetector
import com.meshlit.diagnostics.AndroidPeripheralProbe
import com.meshlit.diagnostics.AndroidSystemProbe
import com.meshlit.inference.ClusterDispatch
import com.meshlit.inference.MetricsRegistry
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.PeerRegistry
import com.meshlit.mcp.DataStoreUserMcpServerPersistence
import com.meshlit.network.termux.AndroidTermuxBridge
import com.meshlit.network.termux.InMemoryResultBus
import com.meshlit.network.termux.TermuxBridge
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.observability.LogBuffer
import com.meshlit.bootstrap.BootstrapSnapshotProvider
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.power.BatteryOptimizationHelper
import com.meshlit.scripts.ScriptLibrary
import com.meshlit.settings.DeviceProfileRepository
import com.meshlit.settings.SettingsRepository
import com.meshlit.setup.FirstRunSetupRepository
import com.meshlit.capability.CapabilityTier
import com.meshlit.capability.currentCapabilityTier
import com.meshlit.config.DataStoreConfigRepository
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.flags.DataStoreFeatureFlagRegistry
import com.meshlit.agent.AgentCapabilityRegistryHolder
import com.meshlit.agent.AgentCapabilityDispatchers
import com.meshlit.agent.AgentCapabilityRegistrar
import com.meshlit.agent.AuditSink
import com.meshlit.agent.ApprovalSink
import com.meshlit.agent.DenyByDefaultApprovalSink
import com.meshlit.agent.TermuxAuditSink
import com.meshlit.agent.hooks.HookAuditSink
import com.meshlit.agent.hooks.HookEngine
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookTrigger
import com.meshlit.scripts.ConfigScriptRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Koin bindings for the `:core-*` singletons owned by the app
 * process. Each `single { ... }` here corresponds to a `by lazy`
 * field that used to live on `MeshlitApplication`.
 *
 * The `androidContext()` extension (registered in MeshlitApplication
 * before `startKoin { ... }` runs) provides the `Application`
 * instance to factory-style providers that take a `Context`.
 *
 * Volatile refs (`bundledModelPath`, `activePeerHealthCache`,
 * `stableNodeId`) are wrapped in a @Volatile holder (`RefHolder`)
 * so Koin can hand the same singleton to every consumer without
 * losing the write side.
 */
val coreModule = module {

    // -----------------------------------------------------------------
    // Process-wide Scope
    // -----------------------------------------------------------------
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // -----------------------------------------------------------------
    // Capability + OS detection (cheap, computed once per process)
    // -----------------------------------------------------------------
    single<CapabilityTier> { currentCapabilityTier() }
    single { AndroidHostOSProbe().probe() }
    single { AndroidOemDetector(androidContext()).detect() }
    single<HostOS> { get<HostOSDetection>().hostOS }

    // -----------------------------------------------------------------
    // Backing DataStore for the forwarding-peer registry
    //
    // `preferencesDataStore(...)` is a `Context` extension
    // property factory; we materialise it once on the application
    // context and Koin caches the resolved `DataStore<Preferences>`
    // so every `get()` call returns the same singleton.
    // -----------------------------------------------------------------
    single<DataStore<Preferences>> { androidContext().peerDataStore }
    single { PeerRegistry(get(), get<TrustStore>()) }
    single { ClusterDispatch(get()) }

    // -----------------------------------------------------------------
    // Proxy for the `peerDataStore` extension property — Koin sees
    // `Context.peerDataStore` via the top-level extension below.
    // -----------------------------------------------------------------
    single<OkHttpClient> {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // -----------------------------------------------------------------
    // Settings / device / first-run repositories
    // -----------------------------------------------------------------
    single { com.meshlit.peering.DeviceIdentity(androidContext()) }
    single { com.meshlit.disco.DiscoveryModeStore(androidContext()) }
    single<com.meshlit.core.users.UserStore> {
        com.meshlit.core.users.FileBackedUserStore(java.io.File(androidContext().filesDir, "users")).also { store ->
            if (store.current().createdAtMs == 0L) store.add("Owner")
        }
    }
    single { com.meshlit.peering.PeerSessionStore(androidContext()) }
    single { com.meshlit.peering.PairingHistoryStore(androidContext(), get(), get<CoroutineScope>()) }
    single { com.meshlit.core.trust.PairingAuthority({ get<com.meshlit.peering.DeviceIdentity>().get(get<com.meshlit.MeshlitApplication>().nodeIdHex) },
        get<TrustStore>(), onEvent = { get<com.meshlit.peering.PairingHistoryStore>().record(it) }) }
    single { com.meshlit.peering.ContributionStore(androidContext(), get()) { get<com.meshlit.MeshlitApplication>().nodeIdHex } }
    single { com.meshlit.peering.HostLocationStore(androidContext()) }
    single { com.meshlit.peering.RelayManager(get(), get(), get(), get(), get()) }
    single { SettingsRepository(androidContext()) }
    single { DeviceProfileRepository(androidContext()) }
    single { FirstRunSetupRepository(androidContext()) }
    single { BatteryOptimizationHelper(androidContext()) }

    // -----------------------------------------------------------------
    // Notification subsystem
    // -----------------------------------------------------------------
    single { NotificationPreferences(androidContext()) }
    single { NotificationCenter(androidContext(), get(), get()) }

    // -----------------------------------------------------------------
    // Discovery
    // -----------------------------------------------------------------
    single {
        DiscoveryCoordinator(
            initialTransports = listOf(
                NsdDiscoveryTransport(androidContext()),
                com.meshlit.core.discovery.BluetoothLeDiscoveryTransport(androidContext()),
            ),
        )
    }
    // The v2 peer-discovery repository. Wraps the coordinator
    // with classification (LOCAL/CLUSTER/GROUP/INTERNET) + a
    // StateFlow that the v2 Scan screen reads via
    // `collectAsStateWithLifecycle`. Started lazily from
    // MeshlitApplication.onCreate via [PeerRepository.start].
    single {
        com.meshlit.disco.PeerRepository(
            coordinator = get(),
            localIPPrefixProvider = { com.meshlit.disco.collectLocalIPv4Prefixes() },
            clusterFingerprintsProvider = { get<com.meshlit.core.trust.TrustStore>().list().filter { !it.isTokenExpired() }.mapNotNull { it.publicKeyFingerprint }.toSet() },
            trustStore = get<com.meshlit.core.trust.TrustStore>(),
            usbTetherProvider = {
                (androidContext() as com.meshlit.MeshlitApplication)
                    .usbTetherActive
                    .value
            },
        )
    }
    // v2 Scan screen wires the mDNS capture row + live packet stream
    // through this singleton. The manager owns the multicast listener
    // + .pcap recorder; the screen drives start/stop via the public
    // methods below. Never starts automatically.
    single { com.meshlit.pcap.PacketCaptureManager(androidContext()) }

    // -----------------------------------------------------------------
    // Firewall
    // -----------------------------------------------------------------
    single<MeshlitFirewall> {
        val firewall=MeshlitFirewall(com.meshlit.core.firewall.FirewallPolicy.Default,
            MeshlitFirewall.Starter.portLayer)
        val repository=get<SettingsRepository>()
        get<CoroutineScope>().launch{repository.firewallFlow.collect{firewall.portLayer=it}}
        firewall
    }

    // -----------------------------------------------------------------
    // Agent capability subscriptions
    // -----------------------------------------------------------------
    single { AgentCapabilityRegistryHolder(androidContext(), get()) }
    // Termux bridge — wired so `agent_termux_run_command` and the
    // Settings → Integrations → Termux screen can dispatch into the
    // real Termux app via the official RUN_COMMAND plugin API. The
    // result bus singleton is shared with the manifest-registered
    // `TermuxResultReceiver` so broadcast results reach the
    // awaiting `runCommand` future.
    single { InMemoryResultBus() }
    single { AndroidTermuxBridge(androidContext(), resultBus = get()) }
    single<TermuxBridge> { get<AndroidTermuxBridge>() }
    // The static `TermuxResultReceiver` (declared in
    // AndroidManifest.xml with permission=com.termux.permission.RUN_COMMAND)
    // forwards its broadcast into `TermuxReceiverRegistry.bus`.
    // We wire that field from a separate factory so the bridge and
    // the static receiver share the SAME bus instance.
    single {
        val bus = get<com.meshlit.network.termux.ResultBus>()
        com.meshlit.network.termux.TermuxReceiverRegistry.bus = bus
        bus
    }
    // The agent dispatcher writes every termux invocation to a
    // JSONL ledger under filesDir/agent/termux-audit.jsonl. The
    // approval sink denies any non-allowlisted command by default;
    // an in-app approval sheet can replace it once Phase 5.5 ships.
    single<AuditSink> { TermuxAuditSink(androidContext()) }
    single<ApprovalSink> { DenyByDefaultApprovalSink() }
    single {
        AgentCapabilityDispatchers(
            appContext = androidContext(),
            registry = get<AgentCapabilityRegistryHolder>().registry,
            settings = get(),
            termuxBridge = get<TermuxBridge>(),
            termuxApprovals = get<ApprovalSink>(),
            termuxAudit = get<AuditSink>(),
            hookEngine = get(),
        )
    }
    // The registrar pulls the cloud tool registry from the cloud
    // coordinator and the agent registry holder, so that capability
    // toggles push the matching `agent_*` tools into the merged
    // tool registry that the agent loop reads.
    single { AgentCapabilityRegistrar(get(), get<CloudMcpCoordinator>().toolRegistry, get()) }

    // -----------------------------------------------------------------
    // Inference coordinator
    // -----------------------------------------------------------------
    single { com.meshlit.models.LocalBehaviorSettings(androidContext()) }
    single { val behavior=get<com.meshlit.models.LocalBehaviorSettings>(); InferenceCoordinator(operationGate=com.meshlit.operations.OperationsControl.get(androidContext()).gate, localBehavior={behavior.state.value}) }

    // -----------------------------------------------------------------
    // RunAnywhere SDK wrappers
    // -----------------------------------------------------------------
    single { RunAnywhereVoiceEngine.get() }
    single { RunAnywhereStructuredEngine.get() }
    single { RunAnywhereVisionEngine.get() }
    single { RunAnywhereCatalogEngine.get() }

    // -----------------------------------------------------------------
    // Observability
    // -----------------------------------------------------------------
    single { com.meshlit.observability.AuditTelemetry(androidContext(),get(),get(),get(),get(),get(),get(),get(),get()) }
    single { MetricsRegistry() }
    single<LogBuffer> {
        AppLoggerFactory.install()
        AppLoggerFactory.buffer
    }
    single<TracingController> {
        val telemetryPrefs=androidContext().getSharedPreferences("telemetry-instance",0)
        val instanceId=telemetryPrefs.getString("id",null) ?: java.util.UUID.randomUUID().toString().also{ telemetryPrefs.edit().putString("id",it).apply() }
        TracingController(object : TraceSink {
            override fun onSpan(name: String, attributes: Map<String, String>) {
                get<LogBuffer>().info(
                    LogSource.SYSTEM,
                    "trace",
                    "span=$name",
                    attributes,
                )
            }
        }, instanceId).also { TracerHolder.bind(it) }
    }

    // -----------------------------------------------------------------
    // Bundled model installer + volatile path ref
    // -----------------------------------------------------------------
    single { BundledModelInstaller() }

    // -----------------------------------------------------------------
    // Script library
    // -----------------------------------------------------------------
    single { ScriptLibrary() }

    // -----------------------------------------------------------------
    // Phase 8 — Hooks subsystem
    //
    // Three singletons:
    //  - `HookAuditSink` — JSONL append-only under filesDir/agent/.
    //  - `MutableStateFlow<List<HookDefinition>>` — hot mirror of the
    //    persisted registry; `MeshlitApplication.onCreate` feeds it
    //    from `settingsRepository.hooksRegistryFlow` so the engine
    //    and the UI read from the same source.
    //  - `HookEngine` — the dispatcher, takes both above plus the
    //    `ConfigScriptRunner` it uses to execute each hook.
    //
    // The `ConfigScriptRunner` is a per-engine instance so its
    //  `events: StateFlow<ScriptEvent?>` reflects the last hook
    //  run and not the user's manual Scripts-screen runs.
    // -----------------------------------------------------------------
    single { HookAuditSink(androidContext().filesDir.absolutePath) }
    single {
        // Seeded empty; MeshlitApplication.onCreate replaces the value
        // on every emission of `settingsRepository.hooksRegistryFlow`.
        MutableStateFlow<List<HookDefinition>>(emptyList())
    }
    single {
        HookEngine(
            hooksFlow = get(),
            masterEnabledFlow = get(named("hooksEnabled")),
            scriptLibrary = get(),
            runner = ConfigScriptRunner(get(), get()),
            auditSink = get(),
        )
    }
    // Master-toggle mirror — `MeshlitApplication.onCreate` feeds
    // it from `settingsRepository.hooksEnabledFlow`. The engine
    // short-circuits on every `fire` / `firePre` call when this is
    // false.
    single(named("hooksEnabled")) {
        kotlinx.coroutines.flow.MutableStateFlow(true)
    }

    // -----------------------------------------------------------------
    // FGS-shared mutable refs
    // -----------------------------------------------------------------
    // `RefHolder<T>` is a generic class, but Koin's `single` keys
    // are resolved at runtime where the type parameter is erased
    // — three separate `single { RefHolder<...>(...) }` calls all
    // register under the same raw `RefHolder` key, so the FGS-shared
    // mutable refs collide. Without a `named(...)` qualifier the
    // first registered instance wins and `set()` calls on one ref
    // silently mutate the others — e.g. `stableNodeIdRef.set("…")`
    // writes a String into the holder that `bundledModelPathRef.get()`
    // casts back to File, crashing the FGS at startup.
    single(named("bundledModelPath")) { RefHolder<File?>(initial = null) }
    single(named("activePeerHealthCache")) { RefHolder<PeerHealthCache?>(initial = null) }
    single(named("stableNodeId")) { RefHolder<String>(initial = "") }

    // -----------------------------------------------------------------
    // Probes — these take an `Application` rather than a generic
    // `Context`, so cast through `androidContext() as Application`.
    // -----------------------------------------------------------------
    single { AndroidSystemProbe(androidContext() as Application) }
    single { AndroidPeripheralProbe(androidContext() as Application) }
    single { AndroidEGpuProbe(androidContext() as Application) }

    // -----------------------------------------------------------------
    // Trust store
    // -----------------------------------------------------------------
    single<TrustStore> { FileBackedTrustStore(File(androidContext().filesDir, "trust")) }

    // -----------------------------------------------------------------
    // Dynamic-foundation Phase 0.1 — config + feature flags
    // -----------------------------------------------------------------
    single<ConfigRepository> { DataStoreConfigRepository(androidContext()) }
    single<FeatureFlagRegistry> { DataStoreFeatureFlagRegistry(androidContext()) }

    // -----------------------------------------------------------------
    // Dynamic-foundation Phase 0.2 — registry + lifecycle + stubs
    // -----------------------------------------------------------------
    single<ServiceRegistry> { LocalServiceRegistry() }
    single { ServiceLifecycleController(
        registry = get(),
        ownerNodeId = { get<BootstrapSnapshotProvider>().nodeIdOrEmpty() },
        flagEnabled = { name -> get<FeatureFlagRegistry>().get(name) },
    ) }
    single { McpServerStub(get()) }
    // AgentSession factory — the v2 `AgentViewModel` resolves the
    // session via `koinInject()`. Without this binding, tapping
    // the Agent tab in the bottom bar crashes the app with
    // `NoDefinitionFoundException` at ViewModel construction
    // time. Each factory call returns a new session so concurrent
    // ViewModels don't share state; `appScope` keeps the
    // session's generation Job tied to the application lifetime.
    factory {
        com.meshlit.agent.AgentSession(
            context = androidContext(),
            app = get(),
            scope = get(),
        )
    }

    // -----------------------------------------------------------------
    // Dynamic-foundation Phase 0.3 — probe + role
    //
    // The production profilers are Android-backed — they call
    // PowerManager, BatteryManager, ActivityManager, ConnectivityManager
    // through the existing `app/.../diagnostics/` impls. We adapt
    // them to the HardwareProfiler interface here so the rest of the
    // dynamic-foundation stays JVM-testable.
    // -----------------------------------------------------------------
    single<List<HardwareProfiler>> {
        // Delta-tracking holder. Reads that need a previous sample
        // (CPU%, network throughput) keep their previous tick in
        // this map so each poll produces a rate, not a counter.
        // The lifetime is the singleton's lifetime — i.e. the
        // process. Re-creating the holder on app restart simply
        // resets the baselines.
        val cpuBaseline = com.meshlit.core.probe.CpuUsageBaseline()
        val netBaseline = com.meshlit.core.probe.NetworkThroughputBaseline()
        listOf(
            CpuProfiler {
                // App-process CPU% sampled by reading /proc/self/stat
                // twice (utime+stime vs starttime) and taking the
                // delta. /proc/stat is SELinux-denied so we measure
                // own-process CPU%, which is the more useful signal
                // for an inference app anyway. Returns 0f on the
                // first tick (no baseline yet) and a fresh
                // percentage thereafter. The raw string already
                // carries the percent + ABI on tick 1 and a clean
                // "<pct>%" on every subsequent tick.
                val (pct, raw) = cpuBaseline.sample()
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = pct / 100f,
                        rawValue = raw,
                    ),
                )
            },
            MemoryProfiler {
                // Real-time RAM% from /proc/meminfo (MemTotal vs
                // MemAvailable). Returns "live" deltas whenever the
                // user backs out a tab or a foreground service
                // frees a buffer.
                val (pct, raw) = com.meshlit.core.probe.readMemoryUsage()
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = pct / 100f,
                        rawValue = raw,
                    ),
                )
            },
            ThermalProfiler {
                // Real device temperature from
                // /sys/class/thermal/thermal_zone*/temp. Picks the
                // first zone with a non-zero reading. The score is
                // clamped to 1.0 at 80°C so the sparkline stays
                // informative on a hot device.
                val (pct, raw) = com.meshlit.core.probe.readThermal()
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = (pct / 100f).coerceIn(0f, 1f),
                        rawValue = raw,
                    ),
                )
            },
            BatteryProfiler {
                // BatteryManager.BATTERY_PROPERTY_CAPACITY is the
                // cheapest fresh read — the framework updates it
                // whenever the broadcast fires (charging,
                // discharging, low battery). Numeric percent
                // directly maps to the sparkline.
                val bm = androidContext().getSystemService(android.content.Context.BATTERY_SERVICE)
                    as android.os.BatteryManager?
                val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = (pct.coerceAtLeast(0).toFloat() / 100f),
                        rawValue = "${pct}%",
                    ),
                )
            },
            NetworkProfiler {
                // Sums `rx_bytes + tx_bytes` deltas across wlan0
                // and rmnet0 (radio) and divides by the elapsed
                // seconds. Returns 0f when offline, the % of a
                // ~10MB/s budget when active.
                val (pct, raw) = netBaseline.sample()
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = (pct / 100f).coerceIn(0f, 1f),
                        rawValue = raw,
                    ),
                )
            },
            NpuProfiler {
                // We don't have a portable NPU detection API on
                // Android — fall back to the SoC family reported by
                // `DeviceProfile.hasNpu`. The full impl lands with
                // Phase 1 inference engine selection.
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(score = 0.5f, rawValue = "unknown"),
                )
            },
        )
    }
    single { HardwareProfilerRegistry(profilers = get()) }
    single { com.meshlit.core.probe.LiveHardwareMonitor(profiler = get()) }
    single { RoleManager(get()) }

    // BootstrapCoordinator needs the registry, lifecycle, the
    // real MCP lifecycle adapter, profiler, and role manager. We resolve them
    // at call time via Koin.
    single {
        BootstrapCoordinator(
            config = get(),
            flags = get(),
            registry = get(),
            lifecycle = get(),
            services = listOf(
                get<McpServerStub>(),
            ),
            profiler = get(),
            roleManager = get(),
        )
    }
    single { BootstrapSnapshotProvider() }

    // -----------------------------------------------------------------
    // Local trust policy — wired once the stable node id is set
    // -----------------------------------------------------------------
    single { LocalTrustPolicy }

    // -----------------------------------------------------------------
    // MCP (server-side, controllers)
    // -----------------------------------------------------------------
    single { com.meshlit.control.WebBridgeHost(androidContext(),get(),get(),get()) }
    single { com.meshlit.core.mcp.control.CodeWorkspace(java.io.File(androidContext().filesDir,"code-workspace")) }
    single { com.meshlit.core.mcp.control.TaskBoard(object:com.meshlit.core.mcp.control.TaskBoardStore {
        private val store=com.meshlit.core.trust.EncryptedCredentialStore(androidContext(),"task-board")
        override suspend fun load():List<com.meshlit.core.mcp.control.ManagedTask> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){store.get("tasks")?.let{kotlinx.serialization.json.Json.decodeFromString(it)} ?: emptyList()}
        override suspend fun save(tasks:List<com.meshlit.core.mcp.control.ManagedTask>)=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){store.putCommitted("tasks",kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.meshlit.core.mcp.control.ManagedTask.serializer()),tasks));Unit}
    },get()) }
    single { com.meshlit.browser.BrowserSessionBroker(androidContext()) }
    single { com.meshlit.control.AgentBackend(androidContext(),get(),get(),get(),get(),get(),get(),get(),get(),get(),get()) }
    single { com.meshlit.openclaw.OpenClawHost(androidContext(),get()) }
    single { com.meshlit.openclaw.OpenClawNode(androidContext(),get(),get(),get()) }
    single { com.meshlit.openclaw.AndroidControl(androidContext(),get()) }
    single { com.meshlit.openclaw.PhoneAdministration(androidContext(),get(),get(),com.meshlit.operations.OperationsControl.get(androidContext()).gate) }
    single { com.meshlit.pipeline.PipelineHost(androidContext(), get(), get()) }
    single { com.meshlit.resources.ResourceSnapshotReader(androidContext(), get(), get(), get()) }
    single { com.meshlit.ssh.SshConnections(androidContext(),get()) }
    if(android.os.Build.VERSION.SDK_INT>=26) single { com.meshlit.ssh.NodeSshHost(androidContext(),get(),get()) }
    single { com.meshlit.training.TrainingHost(androidContext(),get()) }
    single { com.meshlit.security.LabPackages(androidContext(),get()) }
    single { com.meshlit.security.SecurityLab(androidContext(),get()) }
    single { com.meshlit.gateway.GatewayHost(androidContext(),get(),get(),get(),{get()}) }
    single { com.meshlit.recovery.ReplicaHost(androidContext()) }
    single { com.meshlit.operations.OperationsControl.get(androidContext()) }
    single { com.meshlit.operations.ClusterControls.get(androidContext()) }
    single { com.meshlit.operations.StopCoordinator(androidContext()) }
    single { com.meshlit.operations.OperationTools(get(),get(),get(),get()) }
    single { com.meshlit.colibri.ColibriBackend(androidContext()) }
    single { com.meshlit.crypto.CryptoHost(androidContext()) }
    single { com.meshlit.gibberlink.GibberLinkHost(androidContext()) }
    single { com.meshlit.remote.RemoteCommandHost(androidContext(),get(),get()) }
    single { com.meshlit.p2p.PeerChatHost(androidContext(),get(),get(),get(),get()) }
    single { com.meshlit.providers.OnlineProviders(androidContext(),get()) }
    single { com.meshlit.cloud.CloudManagement(androidContext(),get()) }
    single { com.meshlit.configuration.ConfigurationTransfer(androidContext(),get(),get(),get()) }
    single { com.meshlit.media.MediaGeneration(androidContext(),get()) }
    single { com.meshlit.routing.ModelRoutes(androidContext(),get(),get(),get()) }
    single { com.meshlit.chat.VoiceModels(androidContext()) }
    single { com.meshlit.chat.PersonalMemory(androidContext()) }
    single { com.meshlit.knowledge.KnowledgeLibrary(androidContext()) }
    single { com.meshlit.chat.LocalChatTools(get(),com.meshlit.operations.OperationsControl.get(androidContext()).gate,{get()},{get()},get()) }
    single { com.meshlit.search.AppSearchService(androidContext(),get(),{get<com.meshlit.chat.ChatController>().state.value},{get<com.meshlit.models.ModelLibrary>().models.value},{get<com.meshlit.control.WebBridgeHost>().directory.read()},get<com.meshlit.operations.OperationsControl>().gate,{get<com.meshlit.settings.SettingsRepository>()},{get<com.meshlit.control.AgentBackend>().delegated(com.meshlit.control.AgentBackend.Scope.SETTINGS)},{get<com.meshlit.gateway.GatewayHost>().remoteRoutes}) }
    single { com.meshlit.chat.ChatController(androidContext(), get(), get(),get(),get(),get(),get(),get(),get(),get(),get()) }
    single { com.meshlit.models.ModelLibrary(androidContext(), get(), get(), get()) }
    single { com.meshlit.sandbox.RuntimeHost(androidContext()) }
    single { com.meshlit.sandbox.RuntimeTerminal(androidContext(), get()) }
    single { com.meshlit.core.mcp.builtin.CrawlSettingsStore(androidContext()) }
    single {
        val crawler: com.meshlit.core.mcp.builtin.CrawlSettingsStore = get()
        McpToolRegistry(com.meshlit.operations.OperationsControl.get(androidContext()).gate).apply {
            registerAll(get<com.meshlit.crypto.CryptoHost>().specs())
            registerAll(get<com.meshlit.gibberlink.GibberLinkHost>().specs())
            registerAll(get<com.meshlit.remote.RemoteCommandHost>().specs())
            registerAll(get<com.meshlit.p2p.PeerChatHost>().specs())
            registerAll(get<com.meshlit.chat.PersonalMemory>().specs())
            registerAll(get<com.meshlit.knowledge.KnowledgeLibrary>().specs())
            registerAll(com.meshlit.security.SecuritySkillTools(get<com.meshlit.operations.OperationsControl>().gate).specs())
            registerAll(get<com.meshlit.search.AppSearchService>().specs())
            registerAll(get<com.meshlit.operations.OperationTools>().specs())
            registerAll(com.meshlit.gateway.GatewayRoutingTools { get<com.meshlit.gateway.GatewayHost>() }.specs())
            registerAll(get<com.meshlit.control.AgentBackend>().specs())
            registerAll(com.meshlit.control.EnvironmentTools(androidContext(),get(),get(),get(),get(),get(),get(),get()).specs())
            registerAll(com.meshlit.routing.RouterTools(get(),get()).specs())
            registerAll(com.meshlit.colibri.ColibriTools({ get<com.meshlit.chat.ChatController>() },get()).specs())
            registerAll(get<com.meshlit.openclaw.AndroidControl>().specs())
            registerAll(get<com.meshlit.openclaw.PhoneAdministration>().specs())
            registerAll(com.meshlit.pipeline.PipelineMcpTools(get(),get()).specs())
            registerAll(com.meshlit.sandbox.RuntimeMcpTools(get()).specs())
            registerAll(com.meshlit.ssh.SshMcpTools(get()).specs())
            registerAll(com.meshlit.core.mcp.builtin.CrawlMcpTools(
                settings = crawler::load,
            ).specs())
        }
    }
    single { McpClientPool(registry = get(), store = get()) }
    single { UserMcpServerStore(DataStoreUserMcpServerPersistence(androidContext())) }
    single { MeshlitServerController({ get() }, { get() }) }

    // -----------------------------------------------------------------
    // Cloud MCP + NaraRouter LLM
    // -----------------------------------------------------------------
    single { CloudCredentialStore(androidContext()) }
    single { CloudMcpCoordinator(get(), get()) }
    single { NaraRouterClient(get(), get<CloudCredentialStore>().get("nara-llm", "token") ?: "") }
    single { RagBackendSelectionPolicy() }
    single { LocalRagStore(androidContext()) }
    single { RemoteRagStore(get(), { _, credential -> get<CloudCredentialStore>().get(credential ?: "") }) }

    // -----------------------------------------------------------------
    // Runtime helpers — extracted from MeshlitApplication so the
    // Application class itself can stay focused on Koin + onCreate.
    // -----------------------------------------------------------------
    single { LocalPeerCapabilitiesResolver(androidContext().filesDir, { get() }) }
    single { AgentPromptRunner(get(), get(), get(), get(), get(), get(), get<com.meshlit.operations.OperationsControl>().gate) }
    single { DeviceInfo() }
}

/**
 * Resolved `DataStore<Preferences>` for the forwarding-peer
 * registry. `preferencesDataStore(...)` is a `Context` extension
 * property factory; invoking it once on the application context
 * yields the singleton every Koin consumer of `DataStore<Preferences>`
 * will receive.
 */
private val Context.peerDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_forward_peers")

/**
 * Volatile single-value holder. Used for the FGS-shared mutable
 * refs (`bundledModelPath`, `activePeerHealthCache`, `stableNodeId`)
 * that previously had `@Volatile private var ... = ...` fields with
 * `set()` / `get()` accessors on `MeshlitApplication`.
 *
 * Reads and writes are linearisable; the underlying write is
 * `@Volatile` so changes propagate across threads without a
 * coroutine context.
 */
class RefHolder<T>(initial: T) {
    @Volatile private var value: T = initial
    fun get(): T = value
    fun set(newValue: T) {
        value = newValue
    }
}
