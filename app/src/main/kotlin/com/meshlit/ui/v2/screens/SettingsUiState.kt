package com.meshlit.ui.v2.screens

import com.meshlit.ui.screens.settings.SettingsCategory

/**
 * Sealed UiState for the v2 `SettingsScreen`. Replaces the v1
 * `ui/screens/settings/SettingsScreen.kt` (185 LOC) inline-collect
 * pattern with a real ViewModel + sealed UiState.
 */
sealed interface SettingsUiState {

    data class Ready(
        val categories: List<SettingsCategory>,
        val query: String,
        val results: List<SettingsCategory>,
    ) : SettingsUiState {
        val isFiltering: Boolean get() = query.isNotBlank()
    }
}