package com.meshlit.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.DeviceProfile
import com.meshlit.core.common.GpuFamily
import com.meshlit.core.common.SocFamily
import com.meshlit.di.koinInject
import com.meshlit.settings.DeviceProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backing ViewModel for [DeviceScreen]. Reads the resolved
 * [DeviceProfile] + Advanced-mode toggle from the repository and
 * pushes user-edited override fields back through.
 *
 * State is a single [State] object so the screen can recompose once
 * for any change (profile OR advanced toggle).
 */
class DeviceScreenViewModel(
    private val deviceProfileRepository: DeviceProfileRepository,
    private val application: MeshlitApplication,
) : ViewModel() {

    data class State(
        val profile: DeviceProfile,
        val advancedEnabled: Boolean,
    )

    private val _advanced = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        deviceProfileRepository.flow,
        deviceProfileRepository.advancedEnabledFlow,
    ) { profile, advanced ->
        State(profile = profile, advancedEnabled = advanced)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = State(
            profile = DeviceProfile(detection = DeviceProfileRepository.placeholderDetection()),
            advancedEnabled = false,
        ),
    )

    init {
        // Seed the local Advanced toggle from the persisted value once.
        viewModelScope.launch {
            deviceProfileRepository.advancedEnabledFlow.collect { _advanced.value = it }
        }
    }

    fun setAdvancedEnabled(enabled: Boolean) {
        _advanced.value = enabled
        viewModelScope.launch { deviceProfileRepository.setAdvancedEnabled(enabled) }
    }

    fun refresh() {
        // Re-run the system probe on the app scope and let
        // updateDetection push the result into the flow.
        application.runSystemProbePublic()
    }

    // --- Override setters ------------------------------------------------

    fun setManualManufacturer(manufacturer: String?) {
        viewModelScope.launch { deviceProfileRepository.setManualManufacturer(manufacturer) }
    }

    fun setManualModelName(model: String?) {
        viewModelScope.launch { deviceProfileRepository.setManualModelName(model) }
    }

    fun setManualChipset(family: SocFamily?) {
        viewModelScope.launch { deviceProfileRepository.setManualChipset(family) }
    }

    fun setManualSocModel(model: String?) {
        viewModelScope.launch { deviceProfileRepository.setManualSocModel(model) }
    }

    fun setManualGpu(family: GpuFamily?) {
        viewModelScope.launch { deviceProfileRepository.setManualGpu(family) }
    }

    fun setManualRamMb(ramMb: Long?) {
        viewModelScope.launch { deviceProfileRepository.setManualRamMb(ramMb) }
    }

    fun setManualCpuCores(cores: Int?) {
        viewModelScope.launch { deviceProfileRepository.setManualCpuCores(cores) }
    }

    fun setManualNote(note: String?) {
        viewModelScope.launch { deviceProfileRepository.setManualNote(note) }
    }

    fun clearOverride() {
        viewModelScope.launch { deviceProfileRepository.clearOverride() }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DeviceScreenViewModel(
                    deviceProfileRepository = koinInject<DeviceProfileRepository>(),
                    application = koinInject<MeshlitApplication>(),
                )
            }
        }
    }
}

fun deviceScreenViewModelFactory(): ViewModelProvider.Factory =
    DeviceScreenViewModel.factory()