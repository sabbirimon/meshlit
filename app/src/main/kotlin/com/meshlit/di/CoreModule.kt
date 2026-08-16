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
import com.meshlit.registry.AgentRuntimeStub
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
import com.meshlit.observability.AppLoggerFactory
import com.meshlit.observability.LogBuffer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
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
    single { PeerRegistry(get()) }
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
            transports = listOf(NsdDiscoveryTransport(androidContext())),
        )
    }

    // -----------------------------------------------------------------
    // Firewall
    // -----------------------------------------------------------------
    single<MeshlitFirewall> { MeshlitFirewall.Starter }

    // -----------------------------------------------------------------
    // Agent capability subscriptions
    // -----------------------------------------------------------------
    single { AgentCapabilityRegistryHolder(androidContext(), get()) }
    single { AgentCapabilityDispatchers(androidContext(), get<AgentCapabilityRegistryHolder>().registry, get()) }
    // The registrar pulls the cloud tool registry from the cloud
    // coordinator and the agent registry holder, so that capability
    // toggles push the matching `agent_*` tools into the merged
    // tool registry that the agent loop reads.
    single { AgentCapabilityRegistrar(get(), get<CloudMcpCoordinator>().toolRegistry, get()) }

    // -----------------------------------------------------------------
    // Inference coordinator
    // -----------------------------------------------------------------
    single { InferenceCoordinator() }

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
    single { MetricsRegistry() }
    single<LogBuffer> {
        AppLoggerFactory.install()
        AppLoggerFactory.buffer
    }
    single<TracingController> {
        TracingController(object : TraceSink {
            override fun onSpan(name: String, attributes: Map<String, String>) {
                get<LogBuffer>().info(
                    LogSource.SYSTEM,
                    "trace",
                    "span=$name",
                    attributes,
                )
            }
        }).also { TracerHolder.bind(it) }
    }

    // -----------------------------------------------------------------
    // Bundled model installer + volatile path ref
    // -----------------------------------------------------------------
    single { BundledModelInstaller() }
    single { RefHolder<File?>(initial = null) } // bundledModelPath

    // -----------------------------------------------------------------
    // Script library
    // -----------------------------------------------------------------
    single { ScriptLibrary() }

    // -----------------------------------------------------------------
    // FGS-shared mutable refs
    // -----------------------------------------------------------------
    single { RefHolder<PeerHealthCache?>(initial = null) } // activePeerHealthCache
    single { RefHolder<String>(initial = "") } // stableNodeId

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
    single { AgentRuntimeStub() }

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
        listOf(
            CpuProfiler { com.meshlit.core.common.MeshlitResult.Success(
                com.meshlit.core.probe.ProfileSample(
                    score = 0.8f,
                    rawValue = "arm64-v8a",
                ),
            ) },
            MemoryProfiler {
                val ramMb = try {
                    val mi = android.os.Debug.MemoryInfo()
                    android.os.Debug.getMemoryInfo(mi)
                    mi.totalMem / (1024 * 1024)
                } catch (t: Throwable) { 0L }
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = (ramMb.toFloat() / (12f * 1024f)).coerceIn(0f, 1f),
                        rawValue = ramMb.toString(),
                    ),
                )
            },
            ThermalProfiler {
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(score = 0.9f, rawValue = "0"),
                )
            },
            BatteryProfiler {
                val bm = androidContext().getSystemService(android.content.Context.BATTERY_SERVICE)
                    as android.os.BatteryManager?
                val pct = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = (pct.coerceAtLeast(0).toFloat() / 100f),
                        rawValue = pct.toString(),
                    ),
                )
            },
            NetworkProfiler {
                val cm = androidContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager?
                val active = cm?.activeNetworkInfo
                val reachable = active?.isConnected ?: false
                com.meshlit.core.common.MeshlitResult.Success(
                    com.meshlit.core.probe.ProfileSample(
                        score = if (reachable) 1.0f else 0.0f,
                        rawValue = if (reachable) "reachable" else "offline",
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
    single { RoleManager(get()) }

    // BootstrapCoordinator needs the registry, lifecycle, the
    // stubs, the profiler, and the role manager. We resolve them
    // at call time via Koin.
    single {
        BootstrapCoordinator(
            config = get(),
            flags = get(),
            registry = get(),
            lifecycle = get(),
            services = listOf(
                get<McpServerStub>(),
                get<AgentRuntimeStub>(),
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
    single { McpToolRegistry() }
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
    single { AgentPromptRunner(get(), get(), get(), get(), get()) }
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
