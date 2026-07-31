package com.meshlit.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.R
import com.meshlit.core.common.OemProfile
import com.meshlit.core.common.OemSetupStep

/**
 * First-run setup wizard. Shown by MainActivity when the detected
 * OEM has incomplete setup steps. The wizard walks the user through
 * each step with a clear "why this matters" explanation and a
 * "Take me there" button that launches the OEM-specific intent.
 *
 * Steps come from [OemProfile.setupSteps] — a Pixel sees 2 steps,
 * a Xiaomi MIUI device sees 5, a HarmonyOS NEXT device sees 3.
 *
 * Layout:
 *  - Top: progress bar (done / total)
 *  - Per-step card with: icon, label, description, action button,
 *    "Done" checkbox
 *  - Bottom: "Skip setup" / "Finish" buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SetupWizardViewModel = viewModel(
        factory = setupWizardViewModelFactory(context),
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.setup_open_settings))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ProgressBar(done = state.doneCount, total = state.totalCount)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = state.profile.displayName,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.setup_explainer),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (state.profile.killsFgsAggressively) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.setup_kills_fgs_warning),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                items(state.steps.size) { idx ->
                    val step = state.steps[idx]
                    StepCard(
                        step = step,
                        done = step in state.completedSteps,
                        onComplete = { viewModel.completeStep(step) },
                        onUndo = { viewModel.undoStep(step) },
                        onOpen = { viewModel.openSystemScreen(step) },
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { viewModel.skipRemaining(onFinish) }) {
                    Text(stringResource(R.string.setup_skip))
                }
                Button(
                    onClick = { viewModel.finish(onFinish) },
                    enabled = state.doneCount == state.totalCount,
                ) {
                    Text(stringResource(R.string.setup_finish))
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(done: Int, total: Int) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$done / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepCard(
    step: OemSetupStep,
    done: Boolean,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit,
) {
    val icon: ImageVector = when (step) {
        OemSetupStep.NOTIFICATION_PERMISSION -> Icons.Default.Notifications
        OemSetupStep.BATTERY_WHITELIST -> Icons.Default.BatteryAlert
        OemSetupStep.BATTERY_SAVER_DISABLE -> Icons.Default.PowerSettingsNew
        OemSetupStep.AUTOSTART_PERMISSION -> Icons.Default.RocketLaunch
        OemSetupStep.MI_PUSH_OPT_IN -> Icons.Default.Notifications
        OemSetupStep.HMS_PUSH_OPT_IN -> Icons.Default.Notifications
        OemSetupStep.HARMONYOS_COMPAT_LAYER_CHECK -> Icons.Default.Security
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = labelFor(step),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = descriptionFor(step),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (done) {
                    OutlinedButton(onClick = onUndo) {
                        Text(stringResource(R.string.setup_undo))
                    }
                } else {
                    OutlinedButton(onClick = onOpen) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.setup_take_me_there))
                    }
                    Button(onClick = onComplete) {
                        Text(stringResource(R.string.setup_mark_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun labelFor(step: OemSetupStep): String = when (step) {
    OemSetupStep.NOTIFICATION_PERMISSION -> stringResource(R.string.setup_step_notif_label)
    OemSetupStep.BATTERY_WHITELIST -> stringResource(R.string.setup_step_battery_label)
    OemSetupStep.BATTERY_SAVER_DISABLE -> stringResource(R.string.setup_step_saver_label)
    OemSetupStep.AUTOSTART_PERMISSION -> stringResource(R.string.setup_step_autostart_label)
    OemSetupStep.MI_PUSH_OPT_IN -> stringResource(R.string.setup_step_mi_push_label)
    OemSetupStep.HMS_PUSH_OPT_IN -> stringResource(R.string.setup_step_hms_push_label)
    OemSetupStep.HARMONYOS_COMPAT_LAYER_CHECK -> stringResource(R.string.setup_step_harmonyos_label)
}

@Composable
private fun descriptionFor(step: OemSetupStep): String = when (step) {
    OemSetupStep.NOTIFICATION_PERMISSION -> stringResource(R.string.setup_step_notif_desc)
    OemSetupStep.BATTERY_WHITELIST -> stringResource(R.string.setup_step_battery_desc)
    OemSetupStep.BATTERY_SAVER_DISABLE -> stringResource(R.string.setup_step_saver_desc)
    OemSetupStep.AUTOSTART_PERMISSION -> stringResource(R.string.setup_step_autostart_desc)
    OemSetupStep.MI_PUSH_OPT_IN -> stringResource(R.string.setup_step_mi_push_desc)
    OemSetupStep.HMS_PUSH_OPT_IN -> stringResource(R.string.setup_step_hms_push_desc)
    OemSetupStep.HARMONYOS_COMPAT_LAYER_CHECK -> stringResource(R.string.setup_step_harmonyos_desc)
}