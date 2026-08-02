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
import com.meshlit.ui.theme.AccentHue
import com.meshlit.ui.theme.BasePalette
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
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

    private object Keys {
        val accentHue = stringPreferencesKey("theme.accent_hue")
        val basePalette = stringPreferencesKey("theme.base_palette")
        val themeMode = stringPreferencesKey("theme.theme_mode")
        val fontScale = floatPreferencesKey("theme.font_scale")
        val densityScale = floatPreferencesKey("theme.density_scale")
        val animationsEnabled = booleanPreferencesKey("theme.animations_enabled")
        val highContrast = booleanPreferencesKey("theme.high_contrast")
        val customModelPath = stringPreferencesKey("model.custom_path")
        val networkScope = stringPreferencesKey("network.scope")
        val remoteEndpoints = stringPreferencesKey("network.remote_endpoints")
        val activeEndpointId = stringPreferencesKey("network.active_endpoint_id")
        val runtimeRegistryVersion = intPreferencesKey("runtime.registry_version")
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_settings")