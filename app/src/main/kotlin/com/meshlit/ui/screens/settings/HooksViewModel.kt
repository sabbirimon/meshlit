package com.meshlit.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlit.core.common.HookDefinition
import com.meshlit.core.common.HookTrigger
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backing state for [HooksScreen] and [HookEditorScreen]. Reads the
 * persisted registry + master kill switch straight from
 * [SettingsRepository] so the screen always reflects what the
 * engine sees. Writes are simple `upsertHook` / `deleteHook` /
 * `setHooksEnabled` calls.
 *
 * Why a `ViewModel` and not a plain `Composable`:
 *  - The hook editor opens a synthetic `HookDefinition` that the
 *    user fills out, then commits via [upsert]. That state belongs
 *    on the editor screen, not the registry screen. Keeping them
 *    separate is cleaner than passing local state through a nav arg.
 *  - The engine reads from `SettingsRepository` directly, so the
 *    ViewModel never has to notify any consumer — `upsertHook`
 *    triggers a DataStore write that propagates through the
 *    engine's flow automatically.
 */
class HooksViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    /**
     * Live registry + master toggle. The screen recomposes on
     * every emission — both flows are cheap (a decode per write).
     */
    val uiState: StateFlow<UiState> = combine(
        settings.hooksRegistryFlow,
        settings.hooksEnabledFlow,
    ) { hooks, enabled ->
        UiState(hooks = hooks, masterEnabled = enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHooksEnabled(enabled) }
    }

    fun upsert(hook: HookDefinition) {
        viewModelScope.launch { settings.upsertHook(hook) }
    }

    fun delete(id: String) {
        viewModelScope.launch { settings.deleteHook(id) }
    }

    data class UiState(
        val hooks: List<HookDefinition> = emptyList(),
        val masterEnabled: Boolean = true,
    )

    companion object {
        /**
         * Construct an empty hook with a fresh UUID and a sensible
         * starter script body so the editor opens with a real draft.
         * Called by the "+ New hook" CTA on [HooksScreen].
         */
        fun newDraft(): HookDefinition = HookDefinition(
            id = java.util.UUID.randomUUID().toString(),
            name = "New hook",
            enabled = true,
            trigger = HookTrigger.OnTurnEnd,
            script = com.meshlit.core.common.ConfigScript(
                name = "new-hook",
                description = "Hook body — edit me",
                steps = listOf(
                    com.meshlit.core.common.ConfigScriptStep.Set(
                        key = "started_by",
                        value = "hook",
                    ),
                ),
            ),
            timeoutMs = 10_000L,
        )
    }
}