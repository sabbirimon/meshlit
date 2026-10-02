package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the v2 HF token Settings card.
 *
 * Three fields:
 *  - **personalToken** — every user. Free tier.
 *  - **proToken** — paid / Enterprise users. Empty for most users.
 *  - **orgSlug** — the organization name from
 *    <https://huggingface.co/settings/organizations>. Empty for free
 *    users.
 *
 * The token values are *never* logged. The screen renders dots /
 * placeholder strings instead of the actual token in logs, and we
 * record only `hasPersonal = true|false` / `hasPro = true|false`
 * when a save happens.
 */
class HuggingFaceTokenViewModel(
    private val settings: SettingsRepository = koinInject(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HuggingFaceTokenUiState.empty())
    val uiState: StateFlow<HuggingFaceTokenUiState> = _uiState.asStateFlow()

    init {
        // Hydrate from DataStore on mount. We keep "is the value present"
        // separate from the value itself so the UI can show a badge
        // ("token saved") without ever rendering the actual token in
        // a log line or screen capture.
        viewModelScope.launch {
            combine(
                settings.huggingFaceTokenFlow,
                settings.huggingFaceProTokenFlow,
                settings.huggingFaceOrgSlugFlow,
            ) { personal, pro, org ->
                HuggingFaceTokenUiState(
                    personalToken = personal,
                    hasPersonalToken = personal.isNotEmpty(),
                    proToken = pro,
                    hasProToken = pro.isNotEmpty(),
                    orgSlug = org,
                    hasOrgSlug = org.isNotEmpty(),
                    showSaved = false,
                    showProSaved = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onPersonalTokenChange(value: String) {
        _uiState.update { it.copy(personalToken = value, showSaved = false) }
    }

    fun onProTokenChange(value: String) {
        _uiState.update { it.copy(proToken = value, showProSaved = false) }
    }

    fun onOrgSlugChange(value: String) {
        _uiState.update { it.copy(orgSlug = value, showProSaved = false) }
    }

    fun savePersonal() {
        val token = _uiState.value.personalToken
        viewModelScope.launch {
            settings.setHuggingFaceToken(token)
            // Clear the field after save so a screen recording or
            // shoulder-surfer can't see it linger. The
            // `hasPersonalToken` flag stays true so the UI can render
            // "Token saved" without re-prompting.
            _uiState.update {
                it.copy(personalToken = "", hasPersonalToken = token.isNotEmpty(), showSaved = true)
            }
        }
    }

    fun clearPersonal() {
        viewModelScope.launch {
            settings.setHuggingFaceToken("")
            _uiState.update { it.copy(personalToken = "", hasPersonalToken = false, showSaved = false) }
        }
    }

    fun savePro() {
        val token = _uiState.value.proToken
        val org = _uiState.value.orgSlug
        viewModelScope.launch {
            settings.setHuggingFaceProToken(token)
            settings.setHuggingFaceOrgSlug(org)
            _uiState.update {
                it.copy(
                    proToken = "",
                    hasProToken = token.isNotEmpty(),
                    orgSlug = org,
                    hasOrgSlug = org.isNotEmpty(),
                    showProSaved = true,
                )
            }
        }
    }

    fun clearPro() {
        viewModelScope.launch {
            settings.setHuggingFaceProToken("")
            settings.setHuggingFaceOrgSlug("")
            _uiState.update {
                it.copy(
                    proToken = "",
                    hasProToken = false,
                    orgSlug = "",
                    hasOrgSlug = false,
                    showProSaved = false,
                )
            }
        }
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HuggingFaceTokenViewModel(settings = koinInject())
                }
            }
    }
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}

data class HuggingFaceTokenUiState(
    val personalToken: String,
    val hasPersonalToken: Boolean,
    val proToken: String,
    val hasProToken: Boolean,
    val orgSlug: String,
    val hasOrgSlug: Boolean,
    val showSaved: Boolean,
    val showProSaved: Boolean,
) {
    companion object {
        fun empty(): HuggingFaceTokenUiState = HuggingFaceTokenUiState(
            personalToken = "",
            hasPersonalToken = false,
            proToken = "",
            hasProToken = false,
            orgSlug = "",
            hasOrgSlug = false,
            showSaved = false,
            showProSaved = false,
        )
    }
}