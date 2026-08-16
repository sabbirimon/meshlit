package com.meshlit.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.core.common.NetworkScope
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.core.cloudmcp.rag.RagMode
import com.meshlit.core.firewall.MeshlitExposedPort
import com.meshlit.core.firewall.MeshlitFirewall
import com.meshlit.core.firewall.PortLayerPolicy
import com.meshlit.core.firewall.PortRule
import com.meshlit.ui.theme.AccentHue
import com.meshlit.ui.theme.BasePalette
import com.meshlit.ui.theme.CustomPalette
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Persistent settings storage. Backed by DataStore (Preferences
 * variant). Each setting has a stable key so a future schema bump
 * can add new fields without breaking older saved data.
 *
 * Migration policy: when we add a new setting, its key returns
 * the default value when read from older stores. We never delete
 * keys; we deprecate them and ignore. That way a user who upgrades
 * from v0.1 → v0.5 keeps every preference they ever set.
 *
 * The repository is the source of truth for everything in the
 * Settings panel. Other systems (theme, notifications, cluster
 * transports, etc.) read from this and write back through it.
 */
class SettingsRepository(private val context: Context) {

    private val store: DataStore<Preferences> = context.settingsDataStore

    val flow: Flow<MeshlitThemeConfig> = store.data.map { prefs ->
        MeshlitThemeConfig(
            accentHue = AccentHue.entries.firstOrNull { it.name == prefs[Keys.accentHue] }
                ?: MeshlitThemeConfig.Default.accentHue,
            basePalette = BasePalette.entries.firstOrNull { it.name == prefs[Keys.basePalette] }
                ?: MeshlitThemeConfig.Default.basePalette,
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.themeMode] }
                ?: MeshlitThemeConfig.Default.themeMode,
            fontScale = prefs[Keys.fontScale] ?: MeshlitThemeConfig.Default.fontScale,
            densityScale = prefs[Keys.densityScale] ?: MeshlitThemeConfig.Default.densityScale,
            animationsEnabled = prefs[Keys.animationsEnabled] ?: MeshlitThemeConfig.Default.animationsEnabled,
            highContrast = prefs[Keys.highContrast] ?: MeshlitThemeConfig.Default.highContrast,
            customPalette = decodeCustomPalette(prefs[Keys.customPaletteJson]),
        )
    }

    /** Empty string == no override (bundled model is used). */
    val customModelPathFlow: Flow<String> = store.data.map { it[Keys.customModelPath] ?: "" }

    /**
     * Phase 2.x — the version of the runtime registry the user has
     * seen. We bump this every time a runtime is added or promoted
     * (shipped / candidate / apple-only). The Models screen reads
     * it on entry to decide whether to show the "new runtime
     * available" banner.
     */
    val runtimeRegistryVersionFlow: Flow<Int> = store.data.map {
        it[Keys.runtimeRegistryVersion] ?: 0
    }

    suspend fun setRuntimeRegistryVersionSeen(version: Int) {
        store.edit { it[Keys.runtimeRegistryVersion] = version }
    }

    // --- Cloud MCP (Phase Cloud) ---------------------------------------
    //
    // RAG backend selection mode (Local / Remote / Auto / Ask) and the
    // Agent Terminal loop display mode (Live / Step). The flow form
    // is consumed by the Settings → RAG screen; the sync form is
    // read by the Agent Terminal Composable when it enters the
    // composition so the toggle doesn't have to wait for the first
    // emission to render.

    /**
     * Active RAG backend selection mode. Default is [RagMode.Auto]
     * so a fresh install routes local-first when enough docs are
     * on-device, and remote otherwise.
     */
    val ragModeFlow: Flow<RagMode> = store.data.map { prefs ->
        RagMode.entries.firstOrNull { it.name == prefs[Keys.ragMode] }
            ?: RagMode.Auto
    }

    /** Sync accessor for first-frame rendering. */
    fun ragModeFlowNow(): RagMode = runCatching {
        kotlinx.coroutines.runBlocking { ragModeFlow.first() }
    }.getOrDefault(RagMode.Auto)

    suspend fun setRagMode(mode: RagMode) {
        store.edit { it[Keys.ragMode] = mode.name }
    }

    /**
     * Active agent-loop display mode. The terminal Composable
     * reads this on every recomposition.
     */
    val loopModeFlow: Flow<String> = store.data.map { prefs ->
        prefs[Keys.loopMode] ?: "Live"
    }

    /** Sync accessor — the Agent Terminal uses this on first frame. */
    fun loopModeFlowNow(): com.meshlit.ui.screens.cloud.AgentLoopMode = runCatching {
        kotlinx.coroutines.runBlocking { loopModeFlow.first() }
    }.getOrDefault("Live").let { raw ->
        runCatching { com.meshlit.ui.screens.cloud.AgentLoopMode.valueOf(raw) }
            .getOrDefault(com.meshlit.ui.screens.cloud.AgentLoopMode.Live)
    }

    suspend fun setLoopMode(mode: com.meshlit.ui.screens.cloud.AgentLoopMode) {
        store.edit { it[Keys.loopMode] = mode.name }
    }

    // --- Web search ----------------------------------------------------
    //
    // The active web-search vendor (Bing / Brave / Serper / Tavily /
    // Google CSE) + the Google CSE `cx` token. The API key itself
    // lives in `CloudCredentialStore` under `web-search-<vendor>/token`.

    /**
     * Active web-search vendor, or `null` if the user hasn't
     * configured one. The Cloud Hub Tools row uses this to decide
     * whether to render the `web_search` tool to the LLM.
     */
    val webSearchVendorFlow: Flow<com.meshlit.core.cloudmcp.web.WebSearchVendor?> =
        store.data.map { prefs ->
            val raw = prefs[Keys.webSearchVendor] ?: return@map null
            com.meshlit.core.cloudmcp.web.WebSearchVendor.entries
                .firstOrNull { it.name == raw }
        }

    suspend fun setWebSearchVendor(
        vendor: com.meshlit.core.cloudmcp.web.WebSearchVendor?,
    ) {
        store.edit { prefs ->
            if (vendor == null) prefs.remove(Keys.webSearchVendor)
            else prefs[Keys.webSearchVendor] = vendor.name
        }
    }

    /** Google CSE `cx` (Programmable Search Engine ID). */
    val webSearchCxFlow: Flow<String?> =
        store.data.map { it[Keys.webSearchCx] }

    suspend fun setWebSearchCx(cx: String?) {
        store.edit { prefs ->
            if (cx.isNullOrBlank()) prefs.remove(Keys.webSearchCx)
            else prefs[Keys.webSearchCx] = cx.trim()
        }
    }

    // --- User-supplied LLM endpoint -------------------------------------
    //
    // The agent loop reads these flows on every prompt so the user
    // can swap the LLM backend (OpenRouter / Together / Groq /
    // Ollama / LM Studio / vLLM / NaraRouter default) without
    // rebuilding. The API key itself lives in
    // `CloudCredentialStore` under `Keys.llmApiKeyProviderId` +
    // `/token` (default providerId = "user-llm").

    /**
     * Base URL of the OpenAI-compatible /v1/chat/completions
     * endpoint. Default is NaraRouter so existing installs
     * keep working.
     */
    val llmEndpointFlow: Flow<String> = store.data.map {
        it[Keys.llmEndpoint] ?: "https://router.bynara.id"
    }

    suspend fun setLlmEndpoint(url: String) {
        store.edit { it[Keys.llmEndpoint] = url.trim().ifBlank { "https://router.bynara.id" } }
    }

    /** Model slug the agent loop sends on every request. */
    val llmModelFlow: Flow<String> = store.data.map {
        it[Keys.llmModel] ?: "nara/deepseek-v4-flash"
    }

    suspend fun setLlmModel(slug: String) {
        store.edit { it[Keys.llmModel] = slug.trim().ifBlank { "nara/deepseek-v4-flash" } }
    }

    /**
     * ProviderId used to resolve the LLM API key in
     * `CloudCredentialStore`. Default = "user-llm" so a fresh
     * install reads/writes `cloud-mcp/user-llm/token`.
     */
    val llmApiKeyProviderIdFlow: Flow<String> = store.data.map {
        it[Keys.llmApiKeyProviderId] ?: "user-llm"
    }

    suspend fun setLlmApiKeyProviderId(id: String) {
        store.edit { it[Keys.llmApiKeyProviderId] = id.trim().ifBlank { "user-llm" } }
    }

    // --- Feature flags --------------------------------------------------
    //
    // Two independent flags, both default to `false`. The Cloud Hub
    // reads `cloudBrowserEnabledFlow` to decide whether to render
    // the Browser row; the Agent Terminal reads
    // `androidAutomationEnabledFlow` to decide whether to expose
    // the AccessibilityService-backed tools.

    /** When `true`, the Cloud Hub shows the Browser row and the
     *  Agent Terminal exposes `browser_*` tools. Default = false. */
    val cloudBrowserEnabledFlow: Flow<Boolean> = store.data.map {
        it[Keys.cloudBrowserEnabled] ?: false
    }

    suspend fun setCloudBrowserEnabled(enabled: Boolean) {
        store.edit { it[Keys.cloudBrowserEnabled] = enabled }
    }

    /** When `true`, the Cloud Hub shows the Android automation row
     *  and the AccessibilityService tools are exposed. Default =
     *  false. */
    val androidAutomationEnabledFlow: Flow<Boolean> = store.data.map {
        it[Keys.androidAutomationEnabled] ?: false
    }

    suspend fun setAndroidAutomationEnabled(enabled: Boolean) {
        store.edit { it[Keys.androidAutomationEnabled] = enabled }
    }

    // --- Android automation allowlist + high-risk packages -------------
    //
    // Persisted as a JSON list of packageName strings. The
    // `AndroidAutomationPermission` policy reads these on every
    // action to decide Allow / Ask / Deny.

    val androidAutomationAllowlistFlow: Flow<Set<String>> = store.data.map {
        decodeStringSet(it[Keys.androidAutomationAllowlist])
    }

    suspend fun setAndroidAutomationAllowlist(packages: Set<String>) {
        store.edit { it[Keys.androidAutomationAllowlist] = encodeStringSet(packages) }
    }

    val androidAutomationHighRiskPackagesFlow: Flow<Set<String>> = store.data.map {
        // Default: system settings + gms. The user can extend it
        // from Settings → Cloud → Android automation.
        decodeStringSet(it[Keys.androidAutomationHighRiskPackages])
            .ifEmpty { defaultHighRiskPackages }
    }

    suspend fun setAndroidAutomationHighRiskPackages(packages: Set<String>) {
        store.edit { it[Keys.androidAutomationHighRiskPackages] = encodeStringSet(packages) }
    }

    // --- Phase Cloud 2 — on-device agent capabilities ----------------
    //
    // The user can let the agent invoke on-device capabilities
    // (camera, mic, GPS, network state, dialer, SMS, storage).
    // Each capability owns a master toggle and, for the
    // high-risk ones, a per-target allowlist. Read with the
    // agentCapabilityEnabled / agentCapabilityAllowlist accessors;
    // the Agent Terminal + the registry observe the flows.

    /**
     * Master-toggle Flow for one agent capability. Default =
     * `false` so a fresh install starts with zero capabilities
     * exposed.
     */
    fun agentCapabilityEnabledFlow(tag: String): Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.agentCapabilityKey(tag)] ?: false
    }

    /**
     * Sync accessor — the [com.meshlit.MeshlitApplication] checks
     * this on cold start to wire the registry before the first
     * LLM call.
     */
    fun agentCapabilityEnabledNow(tag: String): Boolean = runCatching {
        kotlinx.coroutines.runBlocking { agentCapabilityEnabledFlow(tag).first() }
    }.getOrDefault(false)

    suspend fun setAgentCapabilityEnabled(tag: String, enabled: Boolean) {
        store.edit { it[Keys.agentCapabilityKey(tag)] = enabled }
    }

    /**
     * Per-target allowlist Flow. For `Sms` the entries are E.164
     * phone numbers; for `Storage` the entries are document tree
     * URIs; for capabilities without a target concept the
     * allowlist stays empty.
     */
    fun agentCapabilityAllowlistFlow(tag: String): Flow<Set<String>> = store.data.map { prefs ->
        decodeStringSet(prefs[Keys.agentCapabilityAllowlistKey(tag)])
    }

    fun agentCapabilityAllowlistNow(tag: String): Set<String> = runCatching {
        kotlinx.coroutines.runBlocking { agentCapabilityAllowlistFlow(tag).first() }
    }.getOrDefault(emptySet())

    suspend fun setAgentCapabilityAllowlist(tag: String, allowlist: Set<String>) {
        store.edit {
            it[Keys.agentCapabilityAllowlistKey(tag)] = encodeStringSet(allowlist)
        }
    }

    fun addAgentCapabilityAllowlistEntry(tag: String, entry: String) {
        val current = agentCapabilityAllowlistNow(tag).toMutableSet()
        if (current.add(entry)) {
            kotlinx.coroutines.runBlocking {
                setAgentCapabilityAllowlist(tag, current)
            }
        }
    }

    fun removeAgentCapabilityAllowlistEntry(tag: String, entry: String) {
        val current = agentCapabilityAllowlistNow(tag).toMutableSet()
        if (current.remove(entry)) {
            kotlinx.coroutines.runBlocking {
                setAgentCapabilityAllowlist(tag, current)
            }
        }
    }

    // --- Phase Observability 1 — inference boost -----------------------
    //
    // When the user taps the drawer's Boost quick action we flip
    // this on. The `InferenceBoostController` reads the flow on
    // every job start and (a) bumps the inference thread pool's
    // OS-level priority to -8, (b) selects the NPU/GPU engine
    // preference when `DeviceProfile.hasNpu` is true. The same
    // flow is also surfaced as a Settings row so users can leave
    // it on permanently.

    /** Boost mode state. Default false. */
    val inferenceBoostEnabledFlow: Flow<Boolean> = store.data.map {
        it[Keys.inferenceBoostEnabled] ?: false
    }

    /** Sync accessor — the controller checks this on cold start. */
    fun inferenceBoostEnabledNow(): Boolean = runCatching {
        kotlinx.coroutines.runBlocking { inferenceBoostEnabledFlow.first() }
    }.getOrDefault(false)

    suspend fun setInferenceBoostEnabled(enabled: Boolean) {
        store.edit { it[Keys.inferenceBoostEnabled] = enabled }
    }

    // --- Phase Observability 1 — tracing mode --------------------------
    //
    // The TracingController in :core-observability reads these
    // flows on every change. `TracingMode.Off` (default) means no
    // spans; `Local` writes spans to the in-app LogBuffer;
    // `Otel` boots an OpenTelemetry SDK and forwards spans via
    // OTLP/gRPC to the user's endpoint URL.

    /** Active tracing mode. Default Off. */
    val tracingModeFlow: Flow<TracingMode> = store.data.map { prefs ->
        TracingMode.entries.firstOrNull { it.name == prefs[Keys.tracingMode] }
            ?: TracingMode.Off
    }

    suspend fun setTracingMode(mode: TracingMode) {
        store.edit { it[Keys.tracingMode] = mode.name }
    }

    /**
     * Sample rate for tracing. `1` = every span (default),
     * `10` = 1-in-10, etc. Applied by [TracerHolder] before the
     * span is created.
     */
    val tracingSampleRateFlow: Flow<Int> = store.data.map {
        (it[Keys.tracingSampleRate] ?: 1).coerceAtLeast(1)
    }

    suspend fun setTracingSampleRate(rate: Int) {
        store.edit { it[Keys.tracingSampleRate] = rate.coerceAtLeast(1) }
    }

    val tracingIncludeNetworkFlow: Flow<Boolean> = store.data.map {
        it[Keys.tracingIncludeNetwork] ?: true
    }

    suspend fun setTracingIncludeNetwork(enabled: Boolean) {
        store.edit { it[Keys.tracingIncludeNetwork] = enabled }
    }

    val tracingIncludeInferenceFlow: Flow<Boolean> = store.data.map {
        it[Keys.tracingIncludeInference] ?: true
    }

    suspend fun setTracingIncludeInference(enabled: Boolean) {
        store.edit { it[Keys.tracingIncludeInference] = enabled }
    }

    val tracingIncludeAgentFlow: Flow<Boolean> = store.data.map {
        it[Keys.tracingIncludeAgent] ?: true
    }

    suspend fun setTracingIncludeAgent(enabled: Boolean) {
        store.edit { it[Keys.tracingIncludeAgent] = enabled }
    }

    /**
     * OTLP/gRPC endpoint URL. Empty = OTel remote export disabled.
     * The user pastes this in Settings → Tracing; pointing at
     * `https://otlp-gateway-<region>.grafana.cloud:443` is the
     * canonical Grafana Cloud path.
     */
    val tracingOtelEndpointFlow: Flow<String> = store.data.map {
        it[Keys.tracingOtelEndpoint] ?: ""
    }

    suspend fun setTracingOtelEndpoint(url: String) {
        store.edit { prefs ->
            if (url.isBlank()) prefs.remove(Keys.tracingOtelEndpoint)
            else prefs[Keys.tracingOtelEndpoint] = url.trim()
        }
    }

    /**
     * OTLP exporter headers. Format: `key1=value1\nkey2=value2`.
     * The most common entry is `Authorization=Basic <base64>`
     * (Grafana Cloud instance + API token). Parsed by
     * [com.meshlit.core.observability.OtelBootstrap].
     */
    val tracingOtelHeadersFlow: Flow<String> = store.data.map {
        it[Keys.tracingOtelHeaders] ?: ""
    }

    suspend fun setTracingOtelHeaders(raw: String) {
        store.edit { prefs ->
            if (raw.isBlank()) prefs.remove(Keys.tracingOtelHeaders)
            else prefs[Keys.tracingOtelHeaders] = raw.trim()
        }
    }

    // --- Sync (non-flow) accessors ----------------------------------------
    //
    // MeshlitApplication.onCreate wires the TracingController from
    // the persisted values. We can't `collect` from a flow there
    // because onCreate runs before any coroutine scope is ready —
    // a `runBlocking { first() }` is the simpler choice.

    /** Synchronous accessor for the active tracing mode. */
    fun tracingModeNow(): TracingMode = runCatching {
        kotlinx.coroutines.runBlocking { tracingModeFlow.first() }
    }.getOrDefault(TracingMode.Off)

    /** Synchronous accessor for the OTLP endpoint URL. */
    fun tracingOtelEndpointNow(): String = runCatching {
        kotlinx.coroutines.runBlocking { tracingOtelEndpointFlow.first() }
    }.getOrDefault("")

    /**
     * Synchronous accessor for the parsed OTLP headers. Format
     * `key=value\nkey2=value2` is parsed into a `Map<String, String>`.
     * Empty / blank lines are ignored; the first `=` is the
     * separator so values may contain additional `=` (e.g. base64).
     */
    fun tracingOtelHeadersNow(): Map<String, String> = runCatching {
        kotlinx.coroutines.runBlocking { tracingOtelHeadersFlow.first() }
    }.getOrDefault("").let(::parseOtelHeaders)

    /** Convenience parser so other call sites can reuse the format. */
    fun parseOtelHeadersNow(): Map<String, String> = tracingOtelHeadersNow()

    /**
     * Whether the VpnService-based device capture is currently
     * active. The NetworkMonitorScreen reads this on every
     * recomposition to render the Start / Stop CTA. Setting this
     * to true fires the system "VPN profile" consent dialog the
     * next time the user taps Start.
     */
    val netDeviceCaptureEnabledFlow: Flow<Boolean> = store.data.map {
        it[Keys.netDeviceCaptureEnabled] ?: false
    }

    suspend fun setNetDeviceCaptureEnabled(enabled: Boolean) {
        store.edit { it[Keys.netDeviceCaptureEnabled] = enabled }
    }

    // --- Phase Observability 1 — feedback repo slug --------------------
    //
    // The Feedback screen reads this flow to build the GitHub
    // Issue URL. Default = "meshlit/meshlit-android" so existing
    // installs get a working target out of the box.

    val feedbackRepoSlugFlow: Flow<String> = store.data.map {
        it[Keys.feedbackRepoSlug] ?: "meshlit/meshlit-android"
    }

    suspend fun setFeedbackRepoSlug(slug: String) {
        val sanitized = slug.trim().ifBlank { "meshlit/meshlit-android" }
        store.edit { it[Keys.feedbackRepoSlug] = sanitized }
    }

    // --- Network-scope feature ------------------------------------------
    //
    // The user can flip between five scopes (LOCAL, INTERNET, VPN,
    // GROUP, CUSTOM). We persist the active scope, the list of
    // manually-added endpoints, and which one is currently selected
    // as the "primary" target. The default scope is GROUP so first-
    // run users get a privacy-preserving configuration without
    // doing anything.

    val networkScopeFlow: Flow<NetworkScope> = store.data.map { prefs ->
        NetworkScope.entries.firstOrNull { it.name == prefs[Keys.networkScope] }
            ?: NetworkScope.Default
    }

    val remoteEndpointsFlow: Flow<List<RemoteEndpoint>> = store.data.map { prefs ->
        decodeEndpoints(prefs[Keys.remoteEndpoints])
    }

    val activeEndpointIdFlow: Flow<String> = store.data.map { prefs ->
        prefs[Keys.activeEndpointId] ?: ""
    }

    /**
     * Effective port-layer firewall policy. The default is an empty
     * [PortLayerPolicy] (default-deny for any port the user hasn't
     * explicitly opened) so the first-run user gets the safe
     * zero-trust posture; the [MeshlitFirewall.Starter] already
     * whitelists 8080 at the in-memory composite layer, so the
     * stored port layer can stay empty. Persisted as a JSON-
     * serialized [PortLayerPolicy] under [Keys.firewallPolicy].
     */
    val firewallFlow: Flow<PortLayerPolicy> = store.data.map { prefs ->
        val raw = prefs[Keys.firewallPolicy]
        if (raw.isNullOrBlank()) return@map PortLayerPolicy()
        runCatching { json.decodeFromString(PortLayerPolicy.serializer(), raw) }
            .getOrDefault(PortLayerPolicy())
    }

    /**
     * List of additional ports Meshlit exposes (besides the default
     * SSE / management port 8080). The settings UI lets the user
     * open the agent bridge, the file-sharing surface, the
     * terminal session, and the in-app HTTP-over-cluster gateway —
     * each on its own port. Persisted as a comma-separated string
     * under [Keys.exposedPorts]; empty list = "default port only".
     */
    val exposedPortsFlow: Flow<List<Int>> = store.data.map { prefs ->
        val raw = prefs[Keys.exposedPorts] ?: return@map emptyList()
        raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 }
            .distinct()
    }

    suspend fun setFirewallPolicy(policy: PortLayerPolicy) {
        val encoded = json.encodeToString(PortLayerPolicy.serializer(), policy)
        store.edit { it[Keys.firewallPolicy] = encoded }
    }

    suspend fun setExposedPorts(ports: List<Int>) {
        val sanitized = ports.filter { it in 1..65535 }.distinct().sorted()
        store.edit { it[Keys.exposedPorts] = sanitized.joinToString(",") }
    }

    suspend fun setNetworkScope(scope: NetworkScope) {
        store.edit { it[Keys.networkScope] = scope.name }
    }

    suspend fun setActiveEndpoint(id: String) {
        store.edit { it[Keys.activeEndpointId] = id }
    }

    /**
     * Insert or update an endpoint by [RemoteEndpoint.id]. Preserves
     * `lastSeenMs` and `addedAtMs` if the endpoint already exists so
     * the trust state and timestamps survive edits.
     */
    suspend fun upsertEndpoint(endpoint: RemoteEndpoint) {
        store.edit { prefs ->
            val current = decodeEndpoints(prefs[Keys.remoteEndpoints]).toMutableList()
            val existingIdx = current.indexOfFirst { it.id == endpoint.id }
            val now = System.currentTimeMillis()
            val merged = if (existingIdx >= 0) {
                val prior = current[existingIdx]
                current[existingIdx] = endpoint.copy(
                    addedAtMs = if (prior.addedAtMs == 0L) now else prior.addedAtMs,
                    lastSeenMs = if (endpoint.lastSeenMs == 0L) prior.lastSeenMs else endpoint.lastSeenMs,
                )
            } else {
                endpoint.copy(addedAtMs = if (endpoint.addedAtMs == 0L) now else endpoint.addedAtMs)
            }
            prefs[Keys.remoteEndpoints] = encodeEndpoints(current)
        }
    }

    suspend fun removeEndpoint(id: String) {
        store.edit { prefs ->
            val current = decodeEndpoints(prefs[Keys.remoteEndpoints])
                .filter { it.id != id }
            prefs[Keys.remoteEndpoints] = encodeEndpoints(current)
            if (prefs[Keys.activeEndpointId] == id) {
                prefs.remove(Keys.activeEndpointId)
            }
        }
    }

    suspend fun markEndpointSeen(id: String) {
        store.edit { prefs ->
            val now = System.currentTimeMillis()
            val current = decodeEndpoints(prefs[Keys.remoteEndpoints]).map { ep ->
                if (ep.id == id) ep.copy(lastSeenMs = now) else ep
            }
            prefs[Keys.remoteEndpoints] = encodeEndpoints(current)
        }
    }

    suspend fun trustEndpoint(id: String, trusted: Boolean) {
        store.edit { prefs ->
            val current = decodeEndpoints(prefs[Keys.remoteEndpoints]).map { ep ->
                if (ep.id == id) ep.copy(trusted = trusted) else ep
            }
            prefs[Keys.remoteEndpoints] = encodeEndpoints(current)
        }
    }

    /**
     * Synchronous read of the user's custom model path. Used by the
     * foreground service's auto-load path so it doesn't have to
     * subscribe to the flow just to make a one-time decision at
     * startup. Returns an empty string if no custom path is set.
     *
     * Wrapped in [runBlocking] because DataStore is async-only; the
     * FGS startup is already on a coroutine scope so this is cheap.
     */
    fun customModelPathSync(): String = runCatching {
        kotlinx.coroutines.runBlocking { customModelPathFlow.first() }
    }.getOrDefault("")

    suspend fun setAccentHue(hue: AccentHue) {
        store.edit { it[Keys.accentHue] = hue.name }
    }

    suspend fun setBasePalette(palette: BasePalette) {
        store.edit { it[Keys.basePalette] = palette.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[Keys.fontScale] = scale.coerceIn(0.85f, 1.5f) }
    }

    suspend fun setDensityScale(scale: Float) {
        store.edit { it[Keys.densityScale] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        store.edit { it[Keys.animationsEnabled] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        store.edit { it[Keys.highContrast] = enabled }
    }

    suspend fun setCustomPalette(palette: CustomPalette) {
        store.edit { prefs ->
            if (palette is CustomPalette.None) {
                prefs.remove(Keys.customPaletteJson)
            } else {
                prefs[Keys.customPaletteJson] = json.encodeToString(
                    CustomPalette.serializer(), palette,
                )
            }
        }
    }

    suspend fun setCustomModelPath(path: String) {
        store.edit { prefs ->
            if (path.isBlank()) {
                prefs.remove(Keys.customModelPath)
            } else {
                prefs[Keys.customModelPath] = path.trim()
            }
        }
    }

    suspend fun resetToDefaults() {
        store.edit { it.clear() }
    }

    private fun decodeEndpoints(raw: String?): List<RemoteEndpoint> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(RemoteEndpoint.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun encodeEndpoints(endpoints: List<RemoteEndpoint>): String =
        json.encodeToString(ListSerializer(RemoteEndpoint.serializer()), endpoints)

    private val stringListSerializer = ListSerializer(String.serializer())

    private fun decodeStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            json.decodeFromString(stringListSerializer, raw).toSet()
        }.getOrDefault(emptySet())
    }

    private fun encodeStringSet(values: Set<String>): String =
        json.encodeToString(stringListSerializer, values.toList())

    private object Keys {
        val accentHue = stringPreferencesKey("theme.accent_hue")
        val basePalette = stringPreferencesKey("theme.base_palette")
        val themeMode = stringPreferencesKey("theme.theme_mode")
        val fontScale = floatPreferencesKey("theme.font_scale")
        val densityScale = floatPreferencesKey("theme.density_scale")
        val animationsEnabled = booleanPreferencesKey("theme.animations_enabled")
        val highContrast = booleanPreferencesKey("theme.high_contrast")
        val customPaletteJson = stringPreferencesKey("theme.custom_palette_json")
        val customModelPath = stringPreferencesKey("model.custom_path")
        val networkScope = stringPreferencesKey("network.scope")
        val remoteEndpoints = stringPreferencesKey("network.remote_endpoints")
        val activeEndpointId = stringPreferencesKey("network.active_endpoint_id")
        val firewallPolicy = stringPreferencesKey("network.firewall_policy")
        val exposedPorts = stringPreferencesKey("network.exposed_ports")
        val runtimeRegistryVersion = intPreferencesKey("runtime.registry_version")
        val ragMode = stringPreferencesKey("cloud.rag_mode")
        val loopMode = stringPreferencesKey("cloud.loop_mode")
        val webSearchVendor = stringPreferencesKey("cloud.web_search_vendor")
        val webSearchCx = stringPreferencesKey("cloud.web_search_cx")
        val llmEndpoint = stringPreferencesKey("cloud.llm_endpoint")
        val llmModel = stringPreferencesKey("cloud.llm_model")
        val llmApiKeyProviderId = stringPreferencesKey("cloud.llm_api_key_provider_id")
        val cloudBrowserEnabled = booleanPreferencesKey("feature.cloud.browser")
        val androidAutomationEnabled = booleanPreferencesKey("feature.cloud.android_automation")
        val androidAutomationAllowlist = stringPreferencesKey("android_automation.allowlist")
        val androidAutomationHighRiskPackages = stringPreferencesKey("android_automation.high_risk_packages")

        // --- Phase Observability 1 — inference boost + tracing --------
        val inferenceBoostEnabled = booleanPreferencesKey("feature.inference.boost")
        val tracingMode = stringPreferencesKey("tracing.mode")
        val tracingSampleRate = intPreferencesKey("tracing.sample_rate")
        val tracingIncludeNetwork = booleanPreferencesKey("tracing.include_network")
        val tracingIncludeInference = booleanPreferencesKey("tracing.include_inference")
        val tracingIncludeAgent = booleanPreferencesKey("tracing.include_agent")
        val tracingOtelEndpoint = stringPreferencesKey("tracing.otel_endpoint")
        val tracingOtelHeaders = stringPreferencesKey("tracing.otel_headers")
        val netDeviceCaptureEnabled = booleanPreferencesKey("feature.net.device_capture")
        val feedbackRepoSlug = stringPreferencesKey("feedback.repo_slug")

        // --- Phase Cloud 2 — on-device agent capabilities -----------
        // Each row in Settings → Cloud → Agent capabilities owns a
        // master toggle (`feature.cloud.agent.<tag>`) plus, for the
        // high-risk ones, a per-target allowlist. The keys are
        // per-capability so a partial upgrade survives schema bumps.

        /**
         * Master-toggle key for one [com.meshlit.core.cloudmcp.agent.AgentCapability].
         * Computed by [agentCapabilityKey].
         */
        fun agentCapabilityKey(tag: String) =
            booleanPreferencesKey("feature.cloud.agent.$tag")

        /** Per-target allowlist key (SMS recipients, storage URIs). */
        fun agentCapabilityAllowlistKey(tag: String) =
            stringPreferencesKey("feature.cloud.agent.$tag.allowlist")
    }

    /**
     * Phase 12.2 — deserialize the saved custom palette. Missing
     * key → None (graceful migration). Malformed JSON → None
     * (Schema changes don't crash the user's saved theme).
     */
    private fun decodeCustomPalette(raw: String?): CustomPalette {
        if (raw.isNullOrBlank()) return CustomPalette.None
        return runCatching {
            json.decodeFromString(CustomPalette.serializer(), raw)
        }.getOrDefault(CustomPalette.None)
    }

    companion object {
        /**
         * Packages whose tools always require an explicit
         * per-action confirmation, regardless of the user's
         * allowlist. Used by the Android automation permission
         * policy in `AndroidAutomationPermission`.
         */
        val defaultHighRiskPackages: Set<String> = setOf(
            "com.android.settings",
            "com.google.android.gms",
            "com.android.systemui",
        )

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_settings")

/**
 * Parse the user-pasted OTLP header blob (`k=v\nk=v`) into a
 * `Map<String, String>`. Shared between [SettingsRepository]'s
 * sync accessor and the Settings → Tracing screen UI so the
 * format stays in one place.
 */
internal fun parseOtelHeaders(raw: String): Map<String, String> = raw.lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() && !it.startsWith('#') }
    .mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) null
        else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }
    .toMap()

/**
 * Tracing mode toggle. Read by [com.meshlit.core.observability.TracingController]
 * and the [com.meshlit.MeshlitApplication] cold-start path.
 *
 * - [Off] — no spans are created; the tracer is a no-op. Default.
 * - [Local] — spans are written to the in-app LogBuffer + surfaced
 *   in the Log screen / exported via [com.meshlit.observability.LogExporter].
 * - [Otel] — same as Local, plus spans are forwarded to the OTLP
 *   endpoint URL configured in [SettingsRepository.tracingOtelEndpointFlow].
 */
enum class TracingMode {
    Off,
    Local,
    Otel,
}