@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.meshlit.feature.ghosty

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Persistent configuration for the floating Ghosty overlay.
 *
 * Stored in DataStore Preferences under a flat key namespace so the
 * whole config can be read or written in one pass. Defaults are
 * chosen to match the typical mobile setup: bottom-right bubble,
 * 80% opacity, no hot-word.
 */
@Serializable
data class GhostyConfig(
    val enabled: Boolean = false,
    val autoShow: Boolean = true,
    val opacity: Float = 0.85f,
    val bubbleSizeDp: Int = 56,
    val position: Position = Position.BottomEnd,
    val hotWord: String = "",
) {
    init {
        require(opacity in 0f..1f) { "opacity must be in 0..1, got $opacity" }
        require(bubbleSizeDp in 24..128) { "bubbleSizeDp must be in 24..128, got $bubbleSizeDp" }
    }

    enum class Position { TopStart, TopEnd, BottomStart, BottomEnd }

    companion object {
        val DEFAULT = GhostyConfig()
    }
}

/**
 * DataStore-backed CRUD for [GhostyConfig]. Reads are exposed as
 * a [Flow] so Compose screens can collect the latest values; writes
 * happen on a coroutine caller.
 */
class GhostyConfigStore(private val store: DataStore<Preferences>) {

    val config: Flow<GhostyConfig> = store.data.map { prefs ->
        GhostyConfig(
            enabled = prefs[KEY_ENABLED] ?: false,
            autoShow = prefs[KEY_AUTOSHOW] ?: true,
            opacity = prefs[KEY_OPACITY] ?: 0.85f,
            bubbleSizeDp = prefs[KEY_BUBBLE_SIZE] ?: 56,
            position = GhostyConfig.Position.entries.firstOrNull {
                it.name == prefs[KEY_POSITION]
            } ?: GhostyConfig.Position.BottomEnd,
            hotWord = prefs[KEY_HOTWORD] ?: "",
        )
    }

    suspend fun current(): GhostyConfig = config.first()

    suspend fun update(transform: (GhostyConfig) -> GhostyConfig) {
        store.edit { prefs ->
            val prior = GhostyConfig(
                enabled = prefs[KEY_ENABLED] ?: false,
                autoShow = prefs[KEY_AUTOSHOW] ?: true,
                opacity = prefs[KEY_OPACITY] ?: 0.85f,
                bubbleSizeDp = prefs[KEY_BUBBLE_SIZE] ?: 56,
                position = GhostyConfig.Position.entries.firstOrNull {
                    it.name == prefs[KEY_POSITION]
                } ?: GhostyConfig.Position.BottomEnd,
                hotWord = prefs[KEY_HOTWORD] ?: "",
            )
            val next = transform(prior)
            prefs[KEY_ENABLED] = next.enabled
            prefs[KEY_AUTOSHOW] = next.autoShow
            prefs[KEY_OPACITY] = next.opacity
            prefs[KEY_BUBBLE_SIZE] = next.bubbleSizeDp
            prefs[KEY_POSITION] = next.position.name
            prefs[KEY_HOTWORD] = next.hotWord
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("ghosty_enabled")
        val KEY_AUTOSHOW = booleanPreferencesKey("ghosty_autoshow")
        val KEY_OPACITY = floatPreferencesKey("ghosty_opacity")
        val KEY_BUBBLE_SIZE = intPreferencesKey("ghosty_bubble_size")
        val KEY_POSITION = stringPreferencesKey("ghosty_position")
        val KEY_HOTWORD = stringPreferencesKey("ghosty_hotword")
    }
}