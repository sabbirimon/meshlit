package com.meshlit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.R
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationPreferences

/**
 * Per-category notification preferences UI. Lets the user toggle
 * notifications on/off and adjust OS-level importance for each
 * category. The OS settings still win — toggling in this screen
 * updates our in-app filter, but the channel in the OS settings
 * determines whether the notification actually surfaces.
 *
 * The user is sent to the OS settings screen via
 * [android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS] for
 * things we can't change here (lock-screen visibility, override DND,
 * per-channel LED color).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen() {
    val context = LocalContext.current
    val viewModel: NotificationsSettingsViewModel = viewModel(
        factory = notificationsSettingsViewModelFactory(context),
    )
    val prefs by viewModel.prefs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.notifications_title)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.notifications_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(NotificationCategory.entries) { cat ->
                CategoryRow(
                    category = cat,
                    prefs = prefs[cat] ?: NotificationPreferences.CategoryPrefs(),
                    onEnabledChange = { viewModel.setEnabled(cat, it) },
                    onSoundChange = { viewModel.setSound(cat, it) },
                    onVibrationChange = { viewModel.setVibration(cat, it) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: NotificationCategory,
    prefs: NotificationPreferences.CategoryPrefs,
    onEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(category.descRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = prefs.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            if (prefs.enabled) {
                ToggleRow(
                    label = stringResource(R.string.notif_toggle_sound),
                    checked = prefs.allowSound,
                    onCheckedChange = onSoundChange,
                    enabled = category.allowSound,
                )
                ToggleRow(
                    label = stringResource(R.string.notif_toggle_vibration),
                    checked = prefs.allowVibration,
                    onCheckedChange = onVibrationChange,
                    enabled = category.allowVibration,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}