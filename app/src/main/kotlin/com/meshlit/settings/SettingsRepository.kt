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
import com.meshlit.ui.theme.AccentHue
import com.meshlit.ui.theme.BasePalette
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun resetToDefaults() {
        store.edit { it.clear() }
    }

    private object Keys {
        val accentHue = stringPreferencesKey("theme.accent_hue")
        val basePalette = stringPreferencesKey("theme.base_palette")
        val themeMode = stringPreferencesKey("theme.theme_mode")
        val fontScale = floatPreferencesKey("theme.font_scale")
        val densityScale = floatPreferencesKey("theme.density_scale")
        val animationsEnabled = booleanPreferencesKey("theme.animations_enabled")
        val highContrast = booleanPreferencesKey("theme.high_contrast")
    }
}

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_settings")