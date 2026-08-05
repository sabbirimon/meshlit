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
import com.meshlit.core.mcp.McpClientPool
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.MeshlitServerController
import com.meshlit.core.mcp.MeshlitServerState
import com.meshlit.core.mcp.UserMcpServerStore
import com.meshlit.mcp.DataStoreUserMcpServerPersistence
import com.meshlit.core.inference.BundledModelInstaller
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.inference.ContextProvider
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereStructuredEngine
import com.meshlit.core.inference.RunAnywhereVisionEngine
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.core.trust.CloudCredentialStore
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.FileBackedTrustStore
import com.meshlit.core.trust.LocalTrustPolicy
import com.meshlit.core.trust.TrustStore
import com.meshlit.core.trust.TrustTier
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
import kotlinx.coroutines.flow.first
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
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    // -------------------------------------------------------------------
    // Phase 2.x — full SDK surface (voice, vision, structured, catalog).
    //
    // Each of these wrappers is a thin facade over the RunAnywhere SDK's
    // extension-function namespaces (STT/TTS/VAD, structured output +
    // tool calling, model registry, VLM). The screen composables read
    // them straight from this singleton; the FGS doesn't need them
    // because the LLM-side work goes through `inferenceCoordinator`.
    // -------------------------------------------------------------------

    /** Voice (STT + TTS + VAD) engine. Owns its own `AudioRecord` /
     *  `AudioTrack` lifecycles. The Voice screen is its only consumer. */
    val voiceEngine: RunAnywhereVoiceEngine by lazy {
        RunAnywhereVoiceEngine.get()
    }

    /** Structured-output + tool-calling engine. The Structured screen
     *  uses it for JSON-schema generation and Meshlit's MCP tools. */
    val structuredEngine: RunAnywhereStructuredEngine by lazy {
        RunAnywhereStructuredEngine.get()
    }

    /** Vision (VLM) engine. The Vision screen wires the image picker
     *  and prompt field against this; the screen renders a
     *  `BackendMissing` card until the VLM native AAR lands. */
    val visionEngine: RunAnywhereVisionEngine by lazy {
        RunAnywhereVisionEngine.get()
    }

    /** Dynamic catalog engine. Reads the SDK's live model registry
     *  with a fallback to [com.meshlit.inference.RunAnywhereCatalog.all]
     *  when the registry is unreachable. */
    val catalogEngine: RunAnywhereCatalogEngine by lazy {
        RunAnywhereCatalogEngine.get()
    }

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

    /**
     * Snapshot the *self* peer's cluster-relevant state — what's free on
     * disk, what's free in RAM, and which shards we already host.
     * Called by both `ClusterStorageInstaller` (for the planner's self
     * assignment) and `InferenceForegroundService` (so the embedded
     * `ShardServer` can answer `/v1/capabilities` correctly).
     *
     * Cheap: reads from `filesDir.usableSpace` and the runtime's
     * memory counters; iterates a single directory for hosted shards.
     * Called once per `/v1/capabilities` request and once per planner
     * pass (≤ 1/s under normal conditions).
     */
    fun selfCapabilities(): PeerCapabilities {
        val freeDiskMb = filesDir.usableSpace / (1024L * 1024L)
        val rt = Runtime.getRuntime()
        val freeRamMb = (rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / (1024L * 1024L)
        val hosted = mutableSetOf<String>()
        val root = java.io.File(filesDir, "shards")
        if (root.isDirectory) {
            root.listFiles()?.forEach { modelDir ->
                if (!modelDir.isDirectory) return@forEach
                val modelId = modelDir.name
                modelDir.listFiles { f -> f.isFile && f.extension == "shard" }?.forEach { shard ->
                    hosted += "$modelId/${shard.nameWithoutExtension}"
                }
            }
        }
        return PeerCapabilities(
            peerId = "self",
            capabilityTier = capabilityTier,
            freeRamMb = freeRamMb,
            freeDiskMb = freeDiskMb,
            hostedShardIds = hosted,
            lastSeenMs = Long.MAX_VALUE,
            // Phase 3 — surface the local node's trust tier so peers
            // see the same value in `/v1/capabilities` that
            // `LocalTrustPolicy` consults. Default to LOCAL_TRUSTED
            // when the FGS hasn't populated the stable id yet
            // (first launch before any peer has paired).
            tier = LocalTrustPolicy.currentTierOr(LocalTrustTierFallback),
        )
    }

    /** When the stable node id hasn't been assigned yet, the local
     *  trust policy is null. Until pairing completes we report
     *  `LOCAL_TRUSTED` for backward compatibility — the firewall
     *  still consults the IP allowlist first. */
    private val LocalTrustTierFallback: TrustTier = TrustTier.LOCAL_TRUSTED

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
        // Phase 3 — keep LocalTrustPolicy in lock-step with the
        // stable node id. We treat the local node as LOCAL_TRUSTED
        // at start (it owns its own device); subsequent handshake
        // responses from peers will adjust via the global TrustStore.
        LocalTrustPolicy.set(
            DeviceTrustPolicy(
                nodeId = id,
                trustTier = TrustTier.LOCAL_TRUSTED,
                allowedRoles = setOf("brain", "tool", "monitor"),
                tokenExpiryMs = null,
                publicKeyFingerprint = null,
            )
        )
    }

    /**
     * App-wide [TrustStore]. Backed by a JSON file under
     * `filesDir/trust/trust_store.json` so the table survives
     * process death and FGS restarts without pulling in
     * androidx.datastore. Lazy because the file backend only exists
     * once `filesDir` is safe to access.
     */
    val trustStore: TrustStore by lazy { FileBackedTrustStore(java.io.File(filesDir, "trust")) }

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

    // -------------------------------------------------------------------
    // MCP server bootstrap (Phase Advanced).
    //
    // The server-side MCP path used to be dead code: `MeshlitServerAdapter`
    // existed but no application code path instantiated it. Settings → MCP
    // now owns a toggle backed by [MeshlitServerController]; the
    // [UserMcpServerStore] persists user-added MCP server entries into
    // a JSON blob under the standard DataStore preferences namespace and
    // the [mcpClientPool] rehydrates them on first launch.
    //
    // Both controllers bind to 127.0.0.1 by default so the embedded HTTP
    // server is reachable only on-device; the Settings → MCP screen can
    // opt into `0.0.0.0` after a confirmation dialog.
    // -------------------------------------------------------------------

    /** Process-wide MCP tool registry. Lazy because tests inject a
     *  different registry. */
    val mcpToolRegistry: McpToolRegistry by lazy { McpToolRegistry() }

    /** Process-wide pool of external MCP servers (user-added). Rehydrates
     *  from [userMcpServerStore] on construction. */
    val mcpClientPool: McpClientPool by lazy {
        McpClientPool(registry = mcpToolRegistry, store = userMcpServerStore)
    }

    /** DataStore-backed CRUD for user-added MCP server entries. */
    val userMcpServerStore: UserMcpServerStore by lazy {
        UserMcpServerStore(persistence = DataStoreUserMcpServerPersistence(this))
    }

    /** Embedded MCP HTTP server controller. Toggleable from Settings → MCP. */
    val meshlitServerController: MeshlitServerController by lazy {
        MeshlitServerController(
            registryProvider = { mcpToolRegistry },
            poolProvider = { mcpClientPool },
        )
    }

    // -------------------------------------------------------------------
    // Cloud MCP (Phase Cloud).
    //
    // The cloud coordinator owns one SSE session per connected provider
    // and the merged ToolRegistry the agent loop pulls from. The
    // credential store lives in :core-trust and is backed by the
    // Android Keystore (AES256/GCM) via EncryptedCredentialStore.
    //
    // NaraRouter is the OpenAI-compatible LLM gateway the agent loop
    // uses by default. The API key is stored under the `nara-llm`
    // providerId so it shows up in the Cloud Hub UI alongside the
    // other providers.
    // -------------------------------------------------------------------

    /** Encrypted (Keystore-backed) credential store for cloud-MCP
        *  providers. Tokens never hit plain DataStore. */
    val cloudCredentialStore: CloudCredentialStore by lazy {
        CloudCredentialStore(this)
    }

    /** Process-wide HTTP client used by the cloud-MCP transport and
        *  the NaraRouter LLM client. The 30-second read timeout matches
        *  the SSE keep-alive cadence so a stalled stream is surfaced
        *  before the user has time to give up. */
    val cloudHttpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Coordinator. Owns SSE transports + tool registry. */
    val cloudCoordinator: com.meshlit.core.cloudmcp.CloudMcpCoordinator by lazy {
        com.meshlit.core.cloudmcp.CloudMcpCoordinator(
            httpClient = cloudHttpClient,
            credentialStore = cloudCredentialStore,
        )
    }

    /** User-configurable LLM client. Reads the user's chosen
     *  endpoint + model + API-key-provider from
     *  [SettingsRepository] on each [runAgentPrompt] call so
     *  swapping backends doesn't require an app restart.
     *  Falls back to the legacy NaraRouter client for callers
     *  that haven't opted into the user-supplied endpoint yet. */
    val naraRouterClient: com.meshlit.core.cloudmcp.llm.NaraRouterClient by lazy {
        com.meshlit.core.cloudmcp.llm.NaraRouterClient(
            httpClient = cloudHttpClient,
            apiKey = cloudCredentialStore.get("nara-llm", "token") ?: "",
        )
    }

    /** Process-wide RAG selection policy. The agent loop calls
        *  `resolve()` before every retrieval. */
    val ragSelectionPolicy: com.meshlit.core.cloudmcp.rag.RagBackendSelectionPolicy by lazy {
        com.meshlit.core.cloudmcp.rag.RagBackendSelectionPolicy()
    }

    /** Local RAG store (in-memory stub for v1; will be replaced by
        *  Room in the persistence follow-up). */
    val localRagStore: com.meshlit.core.cloudmcp.rag.LocalRagStore by lazy {
        com.meshlit.core.cloudmcp.rag.LocalRagStore(this)
    }

    /** Remote RAG store. Hits the provider's MCP server for
        *  embeddings + similarity. Provider URL + credential
        *  resolution are pushed in at connect time; the store
        *  itself only owns the HTTP client. */
    val remoteRagStore: com.meshlit.core.cloudmcp.rag.RemoteRagStore by lazy {
        com.meshlit.core.cloudmcp.rag.RemoteRagStore(
            httpClient = cloudHttpClient,
            credentialProvider = { providerBaseUrl, credential ->
                cloudCredentialStore.get(credential ?: "")
            },
        )
    }

    /**
     * Run a single agent prompt against the user's chosen LLM
     * endpoint using the merged tool registry. Emits events into
     * the [com.meshlit.core.cloudmcp.CloudMcpCoordinator.events]
     * flow the Agent Terminal UI consumes.
     *
     * Provider-scoped prompts route tool calls to that provider's
     * session; a null `providerId` runs a global prompt.
     *
     * The LLM endpoint is resolved on every call from
     * [SettingsRepository] (baseUrl, model, credentialProviderId)
     * so the user can swap the backend without restarting the
     * app. The API key is pulled from
     * [cloudCredentialStore] under the resolved
     * `credentialProviderId`.
     */
    fun runAgentPrompt(
        providerId: String?,
        prompt: String,
    ) {
        val messages = listOf(
            com.meshlit.core.cloudmcp.llm.OpenAIMessage(
                role = "user",
                content = prompt,
            ),
        )
        val tools = cloudCoordinator.toolRegistry.ordered()
        appScope.launch {
            val endpoint = resolveLlmEndpoint()
            val client = endpoint.buildClient(httpClient = cloudHttpClient)
            client.chatCompletions(
                providerId = providerId ?: "user-llm",
                messages = messages,
                tools = tools,
            ).collect { chunk ->
                when (chunk) {
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Text ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Thought(
                                providerId = chunk.providerId,
                                text = chunk.delta,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.ToolCall ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.ToolCall(
                                providerId = chunk.providerId,
                                callId = chunk.callId,
                                name = chunk.name,
                                args = chunk.args,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Error ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Error(
                                providerId = chunk.providerId,
                                message = chunk.message,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Done ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Done(providerId = chunk.providerId),
                        )
                }
            }
        }
    }

    /**
     * Resolve the user's active LLM endpoint from
     * [SettingsRepository]. Reads three flows synchronously
     * (baseUrl, model, credentialProviderId) and pulls the
     * matching API key from [cloudCredentialStore].
     */
    private suspend fun resolveLlmEndpoint(): com.meshlit.core.cloudmcp.llm.LlmEndpointConfig {
        val baseUrl = settingsRepository.llmEndpointFlow.first()
        val model = settingsRepository.llmModelFlow.first()
        val credentialProviderId = settingsRepository.llmApiKeyProviderIdFlow.first()
        val apiKey = cloudCredentialStore.get(credentialProviderId, "token") ?: ""
        return com.meshlit.core.cloudmcp.llm.LlmEndpointConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            credentialProviderId = credentialProviderId,
        )
    }

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

        // Register every catalog entry with the SDK so
        // `RunAnywhere.downloadModel(model)` can plan against a
        // known URL. This mirrors the upstream SDK's documented
        // pattern (see vendored/upstream/sdk/runanywhere-kotlin/docs/
        // Documentation.md §"Model Registration"). The register call
        // is idempotent — a re-register of the same id just
        // refreshes the metadata.
        val runAnywhereEngine = inferenceCoordinator.runAnywhereEngine()
        com.meshlit.inference.RunAnywhereCatalog.all.forEach { entry ->
            if (entry.url.isNotBlank()) {
                runAnywhereEngine.setCatalogDownloadUrl(entry.id, entry.url)
            }
        }

        // Phase 2.x — install the four wrapper engines on the
        // application context. Each `install()` is a one-shot CAS so
        // repeated calls (e.g. on a configuration change) are no-ops.
        // The Voice engine needs `ContextProvider.install()` first so
        // its permission check can grab the application context.
        ContextProvider.install(this)
        RunAnywhereVoiceEngine.install()
        RunAnywhereStructuredEngine.install()
        RunAnywhereVisionEngine.install()
        // Cluster-shard model storage incubator. Resolves model-id →
        // URL via `ModelCatalog`, distributes shards across peers, and
        // falls back to whole-model download when the cluster can't
        // host the model. Idempotent — safe to call here even if a
        // previous onCreate already installed it.
        com.meshlit.inference.ClusterStorageInstaller.install(this)
        RunAnywhereCatalogEngine.install(offlineFallback = {
            com.meshlit.inference.RunAnywhereCatalog.all.map { entry ->
                RunAnywhereCatalogEngine.Entry(
                    id = entry.id,
                    displayName = entry.displayName,
                    origin = entry.origin,
                    license = entry.license,
                    family = entry.family,
                    approxSizeMb = entry.approxSizeMb,
                    language = entry.language,
                    strengths = entry.strengths,
                    architecture = entry.architecture,
                    quant = entry.quant,
                    sizeClass = entry.sizeClass,
                    bundled = entry.bundled,
                )
            }
        })

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

        // MCP bootstrap — rehydrate persisted user-added MCP server
        // entries, push them into the pool, then start the embedded
        // MCP HTTP server. The server defaults to 127.0.0.1 so it
        // does not silently expose on the LAN.
        appScope.launch {
            bootMcp()
        }
    }

    /**
     * Rehydrate the [userMcpServerStore], apply it to [mcpClientPool],
     * then start [meshlitServerController]. Idempotent: safe to call
     * multiple times (e.g. on configuration change). Logs failures
     * without crashing — the rest of the app does not need MCP to
     * function.
     */
    private suspend fun bootMcp() {
        try {
            userMcpServerStore.rehydrate()
            userMcpServerStore.applyTo(mcpClientPool)
            val res = meshlitServerController.start()
            val finalState = meshlitServerController.state.value
            log.info(
                "app.mcp.boot",
                "MCP subsystem ready",
                mapOf(
                    "serverState" to finalState::class.java.simpleName,
                    "userServers" to userMcpServerStore.all.size,
                    "startOk" to (res is com.meshlit.core.common.MeshlitResult.Success),
                ),
            )
        } catch (t: Throwable) {
            log.error("app.mcp.boot.fail", "MCP bootstrap failed", t)
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