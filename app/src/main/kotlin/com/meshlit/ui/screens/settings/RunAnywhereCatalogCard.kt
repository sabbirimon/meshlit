package com.meshlit.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.inference.RunAnywhereCatalog

/** RunAnywhere-backed catalog card. Mirrors [AlternativeModelsCard]
 *  shape but routes downloads through
 *  `RunAnywhereInferenceEngine.downloadModelById(id)`. */
@Composable
internal fun RunAnywhereCatalogCard(
    loadedIds: SnapshotStateMap<String, Boolean>,
    rowStatus: SnapshotStateMap<String, DownloadStatus>,
    onGet: (RunAnywhereCatalog.Entry) -> Unit,
    onCancel: (RunAnywhereCatalog.Entry) -> Unit,
    visibleEntries: List<RunAnywhereCatalog.Entry>? = null,
    rateTracker: SnapshotStateMap<String, ByteRateTracker>? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val entries = visibleEntries ?: RunAnywhereCatalog.all
            if (entries.isEmpty()) {
                Text(
                    text = "No models match the current filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                entries.forEach { entry ->
                    RunAnywhereRow(
                        entry = entry,
                        isLoaded = loadedIds[entry.id] == true,
                        status = rowStatus[entry.id] ?: DownloadStatus.Idle,
                        rateTracker = rateTracker?.get(entry.id),
                        onGet = { onGet(entry) },
                        onCancel = { onCancel(entry) },
                    )
                    if (entry != entries.last()) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RunAnywhereRow(
    entry: RunAnywhereCatalog.Entry,
    isLoaded: Boolean,
    status: DownloadStatus,
    rateTracker: ByteRateTracker?,
    onGet: () -> Unit,
    onCancel: () -> Unit,
) {
    val originFlag = when (entry.origin) {
        "USA" -> "\uD83C\uDDFA\uD83C\uDDF8"
        "China" -> "\uD83C\uDDE8\uD83C\uDDF3"
        else -> ""
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$originFlag ${entry.origin}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.license} · ~${entry.approxSizeMb} MB · ${entry.language}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isLoaded) "✓" else entry.family,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLoaded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            text = stringResource(R.string.models_runanywhere_runtime_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val isRunning = status is DownloadStatus.Running
            OutlinedButton(
                onClick = onGet,
                enabled = !isLoaded && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when {
                        isRunning -> stringResource(R.string.models_runanywhere_getting_button)
                        isLoaded -> stringResource(R.string.models_runanywhere_installed_button)
                        else -> stringResource(R.string.models_runanywhere_get_cta)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (isRunning) {
                FilledTonalButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "Cancel",
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = status !is DownloadStatus.Idle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DownloadStatusPanel(
                status = status,
                displayName = entry.displayName,
                bytesPerSecond = rateTracker?.bytesPerSecond() ?: 0.0,
            )
        }
    }
}
