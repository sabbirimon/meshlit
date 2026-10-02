package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.di.koinInject
import com.meshlit.inference.MetricsRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the v2 `ClusterScreen`. Polls the shared `MetricsRegistry`
 * once per second (matching the v1 `MetricsScreen`'s `LaunchedEffect`
 * loop) and projects the snapshot into a `ClusterUiState`.
 *
 * The v1 screen read `registry.queueDepth / failureTagCounts /
 * sparkline / peerHealthCache.state` directly with `collectAsState()`
 * — the v2 build replaces that with a real ViewModel + sealed
 * UiState + `collectAsStateWithLifecycle()` per the plan's step 5
 * audit.
 *
 * Wiring: Koin factory in `V2CoreModule.kt` (see step 4 wiring)
 * binds `MetricsRegistry` and constructs the ViewModel via
 * `viewModelFactory { initializer { ClusterViewModel(get()) } }`.
 */
class ClusterViewModel(
    private val registry: MetricsRegistry = koinInject(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClusterUiState>(ClusterUiState.Loading)
    val uiState: StateFlow<ClusterUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                runCatching {
                    val snap = registry.snapshot()
                    _uiState.value = ClusterUiState.Ready(
                        queueDepth = snap.queueDepth,
                        activeTools = 0,
                        successCount = snap.successJobs,
                        failureCount = snap.failureTags.values.sum(),
                        tokensTotal = snap.totalTokensGenerated,
                        tokensPerSec = snap.avgTokensPerSecond,
                        failureTags = snap.failureTags,
                        peerHealth = emptyMap(),
                    )
                }.onFailure { t ->
                    _uiState.value = ClusterUiState.Failure(
                        message = t.message ?: "Failed to read cluster registry",
                    )
                }
                delay(1000L)
            }
        }
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ClusterViewModel(registry = koinInject())
                }
            }
    }
}