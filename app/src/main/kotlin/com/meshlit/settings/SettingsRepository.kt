package com.meshlit.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent user settings. Separate DataStore from
 * [DeviceProfileRepository] so theme / display prefs don't tangle
 * with device identity.
 *
 * Currently persisted keys:
 *  - `theme.mode` — system | light | dark
 *  - `theme.accent` — material accent color name
 *  - `model.custom_path` — optional override pointing at a GGUF the
 *    user sideloaded. When set, the bundled-model auto-load skips it.
 */
class SettingsRepository(private val context: Context) {

    private val store: DataStore<Preferences> = context.settingsDataStore

    val themeModeFlow: Flow<String> = store.data.map { it[Keys.themeMode] ?: THEME_SYSTEM }
    val themeAccentFlow: Flow<String> = store.data.map { it[Keys.themeAccent] ?: ACCENT_PURPLE }

    /** Empty string == no override (bundled model is used). */
    val customModelPathFlow: Flow<String> = store.data.map { it[Keys.customModelPath] ?: "" }

    suspend fun setThemeMode(mode: String) {
        store.edit { it[Keys.themeMode] = mode }
    }

    suspend fun setThemeAccent(accent: String) {
        store.edit { it[Keys.themeAccent] = accent }
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

    private object Keys {
        val themeMode = stringPreferencesKey("theme.mode")
        val themeAccent = stringPreferencesKey("theme.accent")
        val customModelPath = stringPreferencesKey("model.custom_path")
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val ACCENT_PURPLE = "purple"
        const val ACCENT_BLUE = "blue"
        const val ACCENT_GREEN = "green"
        const val ACCENT_AMBER = "amber"
    }
}

private val Context.settingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_settings")
