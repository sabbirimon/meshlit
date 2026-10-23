package com.meshlit.ui.modern

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User-ordered navigation shortcuts shared by Settings and the drawer. */
internal object MenuShortcutStore {
    private const val KEY = "ids"
    private val defaults = listOf("models", "devices", "agents", "device-dashboard")
    private val state = MutableStateFlow(defaults)
    private var initialized = false

    @Synchronized fun ids(context: Context): StateFlow<List<String>> {
        if (!initialized) {
            val saved = context.applicationContext.getSharedPreferences("menu-shortcuts", Context.MODE_PRIVATE)
                .getString(KEY, null)
            state.value = normalize(saved?.split(',') ?: defaults).ifEmpty { normalize(defaults) }
            initialized = true
        }
        return state
    }

    @Synchronized fun save(context: Context, orderedIds: List<String>) {
        val normalized = normalize(orderedIds)
        require(normalized.isNotEmpty() && normalized.size <= 12) { "Choose 1–12 menu shortcuts" }
        check(context.applicationContext.getSharedPreferences("menu-shortcuts", Context.MODE_PRIVATE)
            .edit().putString(KEY, normalized.joinToString(",")).commit()) { "Could not save menu shortcuts" }
        state.value = normalized
        initialized = true
    }

    fun choices(): List<SettingsDestination> = SettingsDestinations.all.filter { it.id != "menu-layout" }

    private fun normalize(ids: List<String>): List<String> {
        val allowed = choices().mapTo(mutableSetOf()) { it.id }
        return ids.filter { it in allowed }.distinct().take(12)
    }
}
