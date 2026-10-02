package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.di.koinInject
import com.meshlit.ui.screens.settings.SettingsCategory
import com.meshlit.ui.screens.settings.SettingsSearchIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the v2 `SettingsScreen`. Holds the local query string
 * and filters the v1 `SettingsSearchIndex` against it.
 *
 * The v1 screen used `mutableStateOf("")` for the query and
 * read `repository.flow` directly inline. The v2 build moves
 * the query into a ViewModel + sealed UiState + lifecycle-aware
 * collect, per the plan's step 5 wiring audit.
 *
 * For build no. 1 there is no `SettingsRepository` injection —
 * the search index is the only dependency. The `repository`
 * default is left as `koinInject` for follow-up work that
 * subscribes to the theme/custom-palette flows.
 */
class SettingsViewModel(
    private val searchIndex: SettingsSearchIndex = koinInject(),
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SettingsUiState>(
        SettingsUiState.Ready(
            categories = SettingsCategory.entries,
            query = "",
            results = SettingsCategory.entries,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            _query.collect { q ->
                val results = if (q.isBlank()) {
                    SettingsCategory.entries
                } else {
                    SettingsCategory.entries.filter { cat ->
                        cat.name.contains(q, ignoreCase = true) ||
                            searchIndex.search(q).any { it.category == cat }
                    }
                }
                _uiState.value = SettingsUiState.Ready(
                    categories = SettingsCategory.entries,
                    query = q,
                    results = results,
                )
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(searchIndex = koinInject())
                }
            }
    }
}