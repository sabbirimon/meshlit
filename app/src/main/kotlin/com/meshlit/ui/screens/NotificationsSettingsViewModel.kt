package com.meshlit.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.MeshlitApplication
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationCenter
import com.meshlit.notifications.NotificationPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backing ViewModel for [NotificationsSettingsScreen]. The screen
 * subscribes to the preferences flow; this VM also re-applies the
 * user's changes to the OS notification channels so the OS settings
 * stay in sync.
 *
 * Uses a manual factory rather than Hilt to keep this Phase 0.5 file
 * dependency-light. Phase 3 will introduce Hilt module wiring for
 * the global NotificationCenter.
 */
class NotificationsSettingsViewModel(
    private val preferences: NotificationPreferences,
    private val notificationCenter: NotificationCenter,
) : ViewModel() {

    val prefs: StateFlow<Map<NotificationCategory, NotificationPreferences.CategoryPrefs>> =
        preferences.flow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(category, enabled)
            if (!enabled) notificationCenter.cancelAll(category)
            notificationCenter.reapplyAllChannels()
        }
    }

    fun setSound(category: NotificationCategory, allow: Boolean) {
        viewModelScope.launch {
            preferences.setSound(category, allow)
            notificationCenter.reapplyAllChannels()
        }
    }

    fun setVibration(category: NotificationCategory, allow: Boolean) {
        viewModelScope.launch {
            preferences.setVibration(category, allow)
            notificationCenter.reapplyAllChannels()
        }
    }

    companion object {
        fun factory(app: MeshlitApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsSettingsViewModel(
                    preferences = app.notificationPreferences,
                    notificationCenter = app.notificationCenter,
                )
            }
        }
    }
}

/**
 * Convenience function for [androidx.lifecycle.viewmodel.compose.viewModel]
 * callers that have a Context in scope. Drops down to a manual factory
 * rather than relying on Hilt (which we don't have set up yet).
 */
fun notificationsSettingsViewModelFactory(context: Context): ViewModelProvider.Factory =
    NotificationsSettingsViewModel.factory(context.applicationContext as MeshlitApplication)