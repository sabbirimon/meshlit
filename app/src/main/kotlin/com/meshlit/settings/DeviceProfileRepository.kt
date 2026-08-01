package com.meshlit.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.common.DetectedDeviceInfo
import com.meshlit.core.common.DeviceProfile
import com.meshlit.core.common.EGpuConnection
import com.meshlit.core.common.GpuFamily
import com.meshlit.core.common.NodeKind
import com.meshlit.core.common.PeripheralDevice
import com.meshlit.core.common.SocFamily
import com.meshlit.core.common.UserOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Persistent source of truth for the device profile. The detection
 * half comes from the system probe (no need to persist — it's
 * deterministic from Build.*). The user override AND the resolved
 * profile are persisted so the rest of the app can read them
 * synchronously on first frame.
 *
 * Storage keys are namespaced under `device.*` so they don't
 * collide with `theme.*` in [SettingsRepository]. Both DataStores
 * live in different files to keep the surface tidy:
 *  - `meshlit_settings` — theme + display
 *  - `meshlit_device_profile` — device identity + override + peripherals
 *
 * What we persist:
 *  - user override as a JSON blob (any field can be overridden)
 *  - node kind (the form-factor the user picked)
 *  - peripherals list (so it survives a relaunch — USB devices don't
 *    reattach in the same order on every boot)
 *  - dev-mode flags (advanced toggle, debug overlay)
 *
 * What we don't persist:
 *  - DetectedDeviceInfo — re-derived from Build.* on every launch
 *  - effective profile — re-computed by [DeviceProfile.effective]
 */
class DeviceProfileRepository(private val context: Context) {

    private val store: DataStore<Preferences> = context.deviceProfileDataStore

    /** Last probe result. Held in memory so [flow] can fold it in. */
    @Volatile private var lastDetection: DetectedDeviceInfo? = null
    @Volatile private var lastPeripherals: List<PeripheralDevice> = emptyList()

    /** Cached device profile. Emits once at startup, then again any
     *  time the user edits their override or the probe completes. */
    val flow: Flow<DeviceProfile> = store.data.map { prefs ->
        val detection = lastDetection ?: placeholderDetection()
        val override = readOverride(prefs)
        val nodeKind = prefs[Keys.nodeKind]?.let { runCatching { NodeKind.valueOf(it) }.getOrNull() }
            ?: NodeKind.PHONE
        DeviceProfile(
            detection = detection,
            override = override,
            nodeKind = nodeKind,
            connectedPeripherals = lastPeripherals,
            knownPeerNodes = emptyList(),
        )
    }

    /** Cache the latest probe result so the next [flow] emission
     *  picks it up. Call this from the probe's completion handler. */
    fun updateDetection(detection: DetectedDeviceInfo, peripherals: List<PeripheralDevice> = emptyList()) {
        lastDetection = detection
        lastPeripherals = peripherals
    }

    suspend fun setNodeKind(kind: NodeKind) {
        store.edit { it[Keys.nodeKind] = kind.name }
    }

    suspend fun setOverride(override: UserOverride?) {
        store.edit { prefs ->
            if (override == null) {
                prefs.remove(Keys.overrideJson)
            } else {
                prefs[Keys.overrideJson] = json.encodeToString(UserOverride.serializer(), override)
            }
        }
    }

    suspend fun clearOverride() {
        store.edit { it.remove(Keys.overrideJson) }
    }

    suspend fun setEGpuOverride(egpu: EGpuConnection?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(externalGpuOverride = egpu))
    }

    suspend fun setManualRamMb(ramMb: Long?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(totalRamMb = ramMb))
    }

    suspend fun setManualChipset(family: SocFamily?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(socFamily = family))
    }

    suspend fun setManualGpu(family: GpuFamily?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(gpuFamily = family))
    }

    suspend fun setManualSocModel(model: String?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(socModel = model?.takeIf { it.isNotBlank() }))
    }

    suspend fun setManualModelName(model: String?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(model = model?.takeIf { it.isNotBlank() }))
    }

    suspend fun setManualManufacturer(manufacturer: String?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(manufacturer = manufacturer?.takeIf { it.isNotBlank() }))
    }

    suspend fun setManualCpuCores(cores: Int?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(cpuCoreCount = cores))
    }

    suspend fun setManualNote(note: String?) {
        val current = currentOverride() ?: UserOverride()
        setOverride(current.copy(note = note?.takeIf { it.isNotBlank() }))
    }

    /** Read the current override synchronously. Useful for the
     *  "edit a single field" path (e.g. user toggles one chip). */
    suspend fun currentOverride(): UserOverride? {
        val prefs = store.data.first()
        return readOverride(prefs)
    }

    /** Set Advanced-mode toggle (separate from the Settings hub's
     *  category-level Advanced switch — this is the Device-screen-level
     *  one that exposes the editor below the read-only summary). */
    suspend fun setAdvancedEnabled(enabled: Boolean) {
        store.edit { it[Keys.advancedEnabled] = enabled }
    }

    val advancedEnabledFlow: Flow<Boolean> = store.data.map { it[Keys.advancedEnabled] ?: false }

    private fun readOverride(prefs: Preferences): UserOverride? {
        val raw = prefs[Keys.overrideJson] ?: return null
        return runCatching { json.decodeFromString(UserOverride.serializer(), raw) }.getOrNull()
    }

    private object Keys {
        val overrideJson = stringPreferencesKey("device.override_json")
        val nodeKind = stringPreferencesKey("device.node_kind")
        val advancedEnabled = booleanPreferencesKey("device.advanced_enabled")
        // Reserved for future use
        val debugOverlay = intPreferencesKey("device.debug_overlay")
        val sampleRate = longPreferencesKey("device.sample_rate")
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Placeholder detection used before the first probe completes.
         * Reads `Build.*` cheaply so the UI has something to show.
         */
        fun placeholderDetection(): DetectedDeviceInfo = DetectedDeviceInfo(
            manufacturer = android.os.Build.MANUFACTURER ?: "Unknown",
            brand = android.os.Build.BRAND ?: "Unknown",
            model = android.os.Build.MODEL ?: "Unknown",
            device = android.os.Build.DEVICE ?: "Unknown",
            product = android.os.Build.PRODUCT ?: "Unknown",
            hardware = android.os.Build.HARDWARE ?: "Unknown",
            board = android.os.Build.BOARD ?: "Unknown",
            abis = android.os.Build.SUPPORTED_ABIS?.toList() ?: listOf(android.os.Build.CPU_ABI),
            primaryAbi = android.os.Build.SUPPORTED_ABIS?.firstOrNull() ?: android.os.Build.CPU_ABI,
            socFamily = SocFamily.OTHER,
            socModel = android.os.Build.SOC_MODEL,
            gpuFamily = GpuFamily.UNKNOWN,
            hasNpu = false,
            npuName = null,
            totalRamMb = 0L,
            availableRamMb = 0L,
            totalStorageMb = 0L,
            availableStorageMb = 0L,
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuMaxFreqKHz = 0L,
            androidVersion = android.os.Build.VERSION.RELEASE ?: "Unknown",
            androidSdkInt = android.os.Build.VERSION.SDK_INT,
            securityPatch = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.os.Build.VERSION.SECURITY_PATCH
            } else null,
            buildFingerprint = android.os.Build.FINGERPRINT ?: "Unknown",
            buildType = android.os.Build.TYPE ?: "user",
        )
    }
}

private val Context.deviceProfileDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_device_profile")