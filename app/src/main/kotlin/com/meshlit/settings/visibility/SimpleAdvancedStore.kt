package com.meshlit.settings.visibility

import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

/**
 * Wraps the `ui.simpleMode` DataStore key as a [StateFlow] so
 * the Settings hub + every category screen can react to the
 * user's Simple/Advanced toggle without each one re-reading
 * the repository.
 *
 * The hub top-card's `ChipRow` is the single source of truth:
 * flipping it there re-emits via DataStore, which all screens
 * observe through this store. The previous per-category
 * `rememberSaveable` toggle in `CategoryScreen.kt` was local
 * only — this is the load-bearing replacement.
 */
class SimpleAdvancedStore(
    private val repository: SettingsRepository,
    scope: CoroutineScope,
) {
    val mode: StateFlow<Boolean> = repository.uiSimpleModeFlow
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    suspend fun setSimple(simple: Boolean) {
        repository.setUiSimpleMode(simple)
    }
}
