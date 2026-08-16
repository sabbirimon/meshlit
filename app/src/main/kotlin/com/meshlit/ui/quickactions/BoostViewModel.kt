package com.meshlit.ui.quickactions

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for the drawer's Boost quick action. Toggles the
 * inference-boost feature flag in [SettingsRepository] and surfaces
 * a Toast describing the result.
 *
 * Boost mode:
 *   - When enabled, [com.meshlit.core.inference.InferenceBoostController]
 *     raises the OS-level priority of the inference thread pool and
 *     prefers the NPU / GPU engine when one is available.
 *   - On a device without an NPU we still flip the flag (so the
 *     thread-priority bump applies) but tell the user there's no
 *     hardware to accelerate.
 *   - Tapping Boost when boost is already on flips it back off.
 */
class BoostViewModel(
    private val app: MeshlitApplication,
) : ViewModel() {

    private val settings: SettingsRepository = app.settingsRepository

    /**
     * Toggle boost mode. The flip is persisted in
     * [SettingsRepository.setInferenceBoostEnabled] and observed by
     * the inference engine on the next job start.
     */
    fun boost(context: Context) {
        viewModelScope.launch {
            val now = settings.inferenceBoostEnabledFlow.first()
            val next = !now
            settings.setInferenceBoostEnabled(next)
            val message = if (next) {
                context.getString(R.string.drawer_boost_on)
            } else {
                context.getString(R.string.drawer_boost_off)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}