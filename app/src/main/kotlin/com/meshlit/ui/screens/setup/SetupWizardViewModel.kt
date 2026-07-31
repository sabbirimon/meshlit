package com.meshlit.ui.screens.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.OemProfile
import com.meshlit.core.common.OemSetupStep
import com.meshlit.setup.FirstRunSetupRepository
import com.meshlit.setup.SetupCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backing ViewModel for [SetupWizardScreen]. Reads the OEM profile +
 * completed-step set, exposes a derived [State], and routes the
 * "Take me there" / "Mark done" actions to the [SetupCoordinator].
 */
class SetupWizardViewModel(
    private val applicationContext: Context,
    private val repository: FirstRunSetupRepository,
    private val coordinator: SetupCoordinator,
    private val profile: OemProfile,
) : ViewModel() {

    data class State(
        val profile: OemProfile,
        val steps: List<OemSetupStep>,
        val completedSteps: Set<OemSetupStep>,
        val doneCount: Int,
        val totalCount: Int,
    )

    val state: StateFlow<State> = combine(
        repository.completedStepsFlow,
        flowOf(profile.setupSteps),
    ) { done, steps ->
        State(
            profile = profile,
            steps = steps,
            completedSteps = done,
            doneCount = steps.count { it in done },
            totalCount = steps.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = State(
            profile = profile,
            steps = profile.setupSteps,
            completedSteps = emptySet(),
            doneCount = 0,
            totalCount = profile.setupSteps.size,
        ),
    )

    fun completeStep(step: OemSetupStep) {
        viewModelScope.launch { coordinator.completeStep(step) }
    }

    fun undoStep(step: OemSetupStep) {
        viewModelScope.launch { coordinator.undoStep(step) }
    }

    fun skipRemaining(onDone: () -> Unit) {
        viewModelScope.launch {
            coordinator.completeAll(profile)
            onDone()
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            coordinator.finish()
            onDone()
        }
    }

    fun openSystemScreen(step: OemSetupStep) {
        val intent: Intent = coordinator.intentForStep(step, profile)
            ?: fallbackIntent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            applicationContext.startActivity(intent)
        } catch (t: Throwable) {
            runCatching { applicationContext.startActivity(fallbackIntent()) }
        }
    }

    private fun fallbackIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", applicationContext.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        fun factory(app: MeshlitApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SetupWizardViewModel(
                    applicationContext = app.applicationContext,
                    repository = app.firstRunSetupRepository,
                    coordinator = SetupCoordinator(
                        context = app.applicationContext,
                        repository = app.firstRunSetupRepository,
                        notificationCenter = app.notificationCenter,
                    ),
                    profile = app.oemDetection.profile,
                )
            }
        }
    }
}

fun setupWizardViewModelFactory(context: Context): ViewModelProvider.Factory =
    SetupWizardViewModel.factory(context.applicationContext as MeshlitApplication)