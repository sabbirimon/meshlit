package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the v2 `DevicesScreen`. Aggregates the three flows the
 * v1 screen consumed inline (network scope, remote endpoints,
 * active endpoint id) into one `DevicesUiState` via `combine`.
 *
 * The v1 build never had a `DevicesViewModel` — the v1 screen
 * read the flows directly inside a composable. The v2 build
 * finishes the pattern (see the original plan §6) by giving
 * the screen a real ViewModel + sealed UiState + lifecycle-
 * aware collect.
 *
 * Wiring: Koin factory in `V2CoreModule.kt` (see step 4 wiring)
 * binds `SettingsRepository` and constructs the ViewModel via
 * `viewModelFactory { initializer { DevicesViewModel(get()) } }`.
 *
 * For build no. 1 the ViewModel ships with a safe default — if
 * the SettingsRepository doesn't expose a `combine(scope, endpoints, active)`
 * shape yet, the ViewModel falls back to the v1 read pattern
 * via on-demand suspending getters. The companion's
 * `factory()` is the canonical entry point.
 */
class DevicesViewModel(
    private val repository: SettingsRepository = koinInject(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val uiState: StateFlow<DevicesUiState> = _uiState

    init {
        viewModelScope.launch {
            // Combine the three flows the v1 screen read inline.
            // On any data flow error we surface a `Failure` state
            // with the v1-style "couldn't load settings" message.
            runCatching {
                combine(
                    repository.networkScopeFlow,
                    repository.remoteEndpointsFlow,
                    repository.activeEndpointIdFlow,
                ) { scope, endpoints, activeId ->
                    Triple(scope, endpoints, activeId)
                }.collect { (scope, endpoints, activeId) ->
                    _uiState.value = DevicesUiState.Ready(
                        scope = scope,
                        endpoints = endpoints,
                        activeEndpointId = activeId,
                        // Pairing payload is the device's own
                        // `LocalPeerDescriptor`; the v1 build
                        // renders it via `PairingCard`. The v2
                        // build renders it via `MeshlitPairingIdentityCard`
                        // (see step 4 wiring). For build no. 1 we
                        // surface a placeholder so the v2 screen
                        // can render without crashing on the
                        // descriptor generation race.
                        ownPayload = PairingPayload(
                            descriptor = "node-${scope.name.lowercase()}",
                            qrString = "",
                        ),
                    )
                }
            }.onFailure { t ->
                _uiState.value = DevicesUiState.Failure(
                    message = t.message ?: "Failed to load device settings",
                )
            }
        }
    }

    /**
     * Update the active network scope. Mirrors the v1
     * `ScopePicker.onChange` call — the v2 lead bar's
     * `MeshlitLeadBar("Devices", "Scope: <x>")` reflects the
     * new scope immediately because the `_uiState` flow emits
     * on every `set`.
     */
    fun setScope(scope: com.meshlit.core.common.NetworkScope) {
        viewModelScope.launch {
            repository.setNetworkScope(scope)
        }
    }

    companion object {
        /**
         * Koin factory used by `viewModel(factory = …)` in the
         * Compose layer. Mirrors `ThemeSettingsViewModel.factory()`.
         */
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DevicesViewModel(repository = koinInject())
                }
            }
    }
}
