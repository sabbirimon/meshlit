package com.meshlit.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.MeshlitApplication
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.theme.AccentHue
import com.meshlit.ui.theme.BasePalette
import com.meshlit.ui.theme.CustomPalette
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backing ViewModel for [ThemeCustomizationScreen]. Reads the active
 * [MeshlitThemeConfig] from the repository and pushes user changes
 * back. The screen binds to [config] via `collectAsState()` and the
 * live theme re-renders the moment any setter fires.
 *
 * Each setter writes through [SettingsRepository] — which persists
 * to DataStore. The repository's Flow re-emits, the screen
 * re-composes, and the user sees the change immediately.
 */
class ThemeSettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val config: StateFlow<MeshlitThemeConfig> = repository.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MeshlitThemeConfig.Default,
    )

    fun setAccentHue(hue: AccentHue) {
        viewModelScope.launch { repository.setAccentHue(hue) }
    }

    fun setBasePalette(palette: BasePalette) {
        viewModelScope.launch { repository.setBasePalette(palette) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { repository.setFontScale(scale) }
    }

    fun setDensityScale(scale: Float) {
        viewModelScope.launch { repository.setDensityScale(scale) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAnimationsEnabled(enabled) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { repository.setHighContrast(enabled) }
    }

    fun setCustomPalette(palette: CustomPalette) {
        viewModelScope.launch { repository.setCustomPalette(palette) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    companion object {
        fun factory(app: MeshlitApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ThemeSettingsViewModel(repository = app.settingsRepository)
            }
        }
    }
}

fun themeSettingsViewModelFactory(context: Context): ViewModelProvider.Factory =
    ThemeSettingsViewModel.factory(context.applicationContext as MeshlitApplication)