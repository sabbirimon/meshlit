package com.meshlit.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlit.core.common.logger
import com.meshlit.core.net.openrouter.OpenRouterAuthKeyData
import com.meshlit.core.net.openrouter.OpenRouterClient
import com.meshlit.core.net.openrouter.OpenRouterException
import com.meshlit.core.net.openrouter.OpenRouterKeyVault
import com.meshlit.core.net.openrouter.OpenRouterModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 4 — ViewModel backing [OpenRouterSettingsCard] +
 * [OpenRouterModelBrowserScreen].
 *
 * State flows:
 *  - [status]: the UI-state machine for the settings card
 *  - [models]: full catalog from `/api/v1/models` (cached on
 *    refresh; survives recomposition)
 *  - [selectedModelId]: the user's default model choice
 *  - [error]: transient error from the catalog fetch
 *  - [loading]: true while a refresh is in flight
 *
 * Backward-compat: no DI — the host activity constructs the
 * ViewModel with explicit `vault` + `client` references so a
 * Robolectric / unit test can swap them for fakes.
 */
class OpenRouterModelBrowserViewModel(
    private val vault: OpenRouterKeyVault,
    private val client: OpenRouterClient = OpenRouterClient(),
    private val defaultProvider: String = "openrouter",
) : ViewModel() {

    private val log = logger("OpenRouterModelBrowserViewModel")

    private val _models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val models: StateFlow<List<OpenRouterModel>> = _models.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedModelId = MutableStateFlow<String?>(null)
    val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _status = MutableStateFlow<OpenRouterUiStatus>(OpenRouterUiStatus.NotConfigured)
    val status: StateFlow<OpenRouterUiStatus> = _status.asStateFlow()

    private var query: String = ""

    init {
        // If the vault already has a key, surface it as Connected
        // (without re-validating; that's [refresh]'s job). This
        // keeps the card from flickering on cold start.
        if (vault.exists()) {
            _status.value = OpenRouterUiStatus.Validating
            viewModelScope.launch { validateAndConnect() }
        }
    }

    fun setQuery(q: String) { query = q }

    /** Group + filter the cached model list by provider. Pure —
     *  no IO, called from the Compose lazy column. */
    fun groupedFiltered(): List<Pair<String, List<OpenRouterModel>>> {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) _models.value
            else _models.value.filter {
                it.name.lowercase().contains(q) || it.id.lowercase().contains(q)
            }
        return filtered
            .groupBy { it.providerDisplay.ifEmpty { "Other" } }
            .toSortedMap()
            .map { (provider, rows) ->
                provider to rows.sortedBy { it.name.lowercase() }
            }
    }

    fun saveKey(rawKey: String, onSaved: (OpenRouterAuthKeyData) -> Unit = {}) {
        val ok = vault.save(rawKey)
        if (!ok) {
            _status.value = OpenRouterUiStatus.Error("Key format invalid")
            return
        }
        _status.value = OpenRouterUiStatus.Validating
        viewModelScope.launch {
            try {
                val data = client.validateKey(vault.load() ?: rawKey)
                _status.value = OpenRouterUiStatus.Connected(data.toUi())
                onSaved(data)
            } catch (e: Throwable) {
                _status.value = OpenRouterUiStatus.Error(
                    when (e) {
                        is OpenRouterException.Unauthorized -> "Key rejected by OpenRouter"
                        is OpenRouterException.Http -> "OpenRouter HTTP ${e.code}"
                        is OpenRouterException.Network -> "Network error: ${e.message}"
                        else -> e.message ?: "Unknown error"
                    },
                )
            }
        }
    }

    fun retryValidation() {
        if (!vault.exists()) {
            _status.value = OpenRouterUiStatus.NotConfigured
            return
        }
        _status.value = OpenRouterUiStatus.Validating
        viewModelScope.launch { validateAndConnect() }
    }

    fun disconnect() {
        vault.wipe()
        _models.value = emptyList()
        _selectedModelId.value = null
        _status.value = OpenRouterUiStatus.NotConfigured
    }

    fun pickModel(model: OpenRouterModel) {
        _selectedModelId.value = model.id
    }

    /**
     * Fetch the catalog. Caller passes the API key explicitly so
     * the ViewModel can also be used in a "preview" mode that
     * doesn't persist the key.
     */
    fun refresh() {
        val key = vault.load() ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _models.value = client.listModels(key)
            } catch (e: Throwable) {
                _error.value = e.message ?: "Failed to load models"
                log.warn("OpenRouterModelBrowserViewModel", "refresh failed: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun validateAndConnect() {
        val key = vault.load()
        if (key == null) {
            _status.value = OpenRouterUiStatus.NotConfigured
            return
        }
        try {
            val data = client.validateKey(key)
            _status.value = OpenRouterUiStatus.Connected(data.toUi())
        } catch (e: Throwable) {
            _status.value = OpenRouterUiStatus.Error(
                when (e) {
                    is OpenRouterException.Unauthorized -> "Key rejected by OpenRouter"
                    is OpenRouterException.Http -> "OpenRouter HTTP ${e.code}"
                    is OpenRouterException.Network -> "Network error: ${e.message}"
                    else -> e.message ?: "Unknown error"
                },
            )
        }
    }

    private fun OpenRouterAuthKeyData.toUi() = OpenRouterAuthDataUi(
        label = label,
        usageLabel = usageLabel,
        isFreeTier = isFreeTier,
    )
}

/** Mirrors [OpenRouterStatus] for the ViewModel side — kept
 *  separate so the ViewModel can evolve without forcing the
 *  Compose tree to recompose. */
sealed interface OpenRouterUiStatus {
    data object NotConfigured : OpenRouterUiStatus
    data object Validating : OpenRouterUiStatus
    data class Connected(val data: OpenRouterAuthDataUi) : OpenRouterUiStatus
    data class Error(val message: String) : OpenRouterUiStatus
}

/** Adapter from the ViewModel's [OpenRouterUiStatus] to the
 *  Composable's [OpenRouterStatus]. Keeps the two surfaces
 *  decoupled so a Compose recompile doesn't pull in `core-net`. */
fun OpenRouterUiStatus.toComposable(): OpenRouterStatus = when (this) {
    OpenRouterUiStatus.NotConfigured -> OpenRouterStatus.NotConfigured
    OpenRouterUiStatus.Validating -> OpenRouterStatus.Validating
    is OpenRouterUiStatus.Connected -> OpenRouterStatus.Connected(data)
    is OpenRouterUiStatus.Error -> OpenRouterStatus.Error(message)
}