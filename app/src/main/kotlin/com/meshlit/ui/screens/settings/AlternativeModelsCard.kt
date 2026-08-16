package com.meshlit.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.meshlit.models.ModelCatalog

/**
 * Card listing the alternative-model entries from [ModelCatalog].
 *
 * Each row carries a tri-state action:
 *  - **Idle / Failed** → tap `Download` to (re)start the fetch.
 *  - **Running** → tap `Cancel` to abort.
 *  - **Done** → tap `Delete` to remove the local copy.
 *
 * The host injects the actual download / cancel / delete behavior via
 * the three callbacks. The card never blocks on its own; it always
 * reflects the latest [DownloadStatus] for the row.
 *
 * [visibleEntries] is the post-filter list driven by the parent
 * screen's search bar. [rateTracker] maps each entry id to a
 * live-blob-rate sampler so the row can show MB/s alongside the
 * percent.
 */
@Composable
internal fun AlternativeModelsCard(
    installedIds: SnapshotStateMap<String, Boolean>,
    rowStatus: SnapshotStateMap<String, DownloadStatus>,
    onPick: (ModelCatalog.Entry) -> Unit,
    onCancel: (ModelCatalog.Entry) -> Unit,
    onDelete: (ModelCatalog.Entry) -> Unit,
    visibleEntries: List<ModelCatalog.Entry>? = null,
    rateTracker: SnapshotStateMap<String, ByteRateTracker>? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val entries = visibleEntries ?: ModelCatalog.all
            if (entries.isEmpty()) {
                Text(
                    text = "No models match the current filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                entries.forEach { entry ->
                    val installed = installedIds[entry.id] == true
                    val status = rowStatus[entry.id] ?: DownloadStatus.Idle
                    AlternativeRow(
                        entry = entry,
                        isInstalled = installed,
                        status = status,
                        rateTracker = rateTracker?.get(entry.id),
                        onDownload = { onPick(entry) },
                        onCancel = { onCancel(entry) },
                        onDelete = { onDelete(entry) },
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
private fun AlternativeRow(
    entry: ModelCatalog.Entry,
    isInstalled: Boolean,
    status: DownloadStatus,
    rateTracker: ByteRateTracker?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
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
                text = if (isInstalled) "✓" else entry.family,
                style = MaterialTheme.typography.labelSmall,
                color = if (isInstalled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
            )
        }
        Text(
            text = "runtime: ${entry.runtimeDisplayName}",
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
            val isDone = status is DownloadStatus.Done
            val isFailed = status is DownloadStatus.Failed
            OutlinedButton(
                onClick = onDownload,
                enabled = !isInstalled && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when {
                        isRunning -> "Downloading…"
                        isInstalled -> "Already imported"
                        isFailed -> "Retry"
                        else -> "Download"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            when {
                isRunning -> {
                    FilledTonalButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Cancel",
                        )
                    }
                }
                isFailed -> {
                    FilledTonalButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Retry",
                        )
                    }
                }
                isInstalled -> {
                    FilledTonalButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                        )
                    }
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

@Composable
internal fun DownloadStatusPanel(
    status: DownloadStatus,
    displayName: String,
    bytesPerSecond: Double = 0.0,
    bytesDownloaded: Long = 0L,
    totalBytes: Long = 0L,
    approxSizeMb: Long = 0L,
) {
    when (status) {
        is DownloadStatus.Idle -> Unit
        is DownloadStatus.Running -> {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.models_download_progress, status.progress, displayName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (bytesPerSecond > 0.0) {
                        Text(
                            text = formatDownloadRate(bytesPerSecond),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Downloaded / total size. Falls back to
                    // "MB downloaded" when the server omitted
                    // Content-Length and we don't have a total.
                    val sizeText = formatDownloadSizeText(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        approxSizeMb = approxSizeMb,
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val etaText = formatEta(bytesPerSecond, totalBytes, bytesDownloaded)
                    if (etaText != null) {
                        Text(
                            text = stringResource(R.string.models_download_eta, etaText),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (status.progress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is DownloadStatus.Done -> {
            Text(
                text = stringResource(R.string.models_download_done, status.absolutePath),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        is DownloadStatus.Failed -> {
            Text(
                text = stringResource(R.string.models_download_failed, status.reason),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Human-readable transfer rate for a download row. */
private fun formatDownloadRate(bytesPerSecond: Double): String {
    if (bytesPerSecond <= 0.0) return ""
    val mbps = bytesPerSecond / (1024.0 * 1024.0)
    return when {
        mbps >= 1.0 -> "%.1f MB/s".format(mbps)
        bytesPerSecond >= 1024.0 -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
        else -> "%.0f B/s".format(bytesPerSecond)
    }
}

/**
 * "1.4 MB / 368 MB" — pick the right unit. When the server
 * didn't supply `Content-Length` we fall back to the catalog
 * hint; if that hint is missing too, just show the downloaded
 * amount on its own.
 */
private fun formatDownloadSizeText(
    bytesDownloaded: Long,
    totalBytes: Long,
    approxSizeMb: Long,
): String {
    val downloaded = formatBytes(bytesDownloaded)
    val effectiveTotal = when {
        totalBytes > 0L -> totalBytes
        approxSizeMb > 0L -> approxSizeMb * 1024L * 1024L
        else -> 0L
    }
    return if (effectiveTotal > 0L) {
        "$downloaded / ${formatBytes(effectiveTotal)}"
    } else {
        downloaded
    }
}

/** "1.4 MB" / "368 MB" / "1.4 GB" — pick the right unit. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

/**
 * Time-to-completion estimate. Returns `null` when the rate is
 * too low (< 1 KB/s) or the total is unknown, so the UI can
 * hide the ETA column rather than show "ETA unknown".
 *
 * The format follows the project's existing pattern:
 *  - < 60s   → "32s"
 *  - < 1h    → "2m 30s"
 *  - ≥ 1h    → "1h 12m"
 */
private fun formatEta(
    bytesPerSecond: Double,
    totalBytes: Long,
    bytesDownloaded: Long,
): String? {
    if (bytesPerSecond < 1024.0) return null
    val effectiveTotal = when {
        totalBytes > 0L -> totalBytes
        else -> 0L
    }
    if (effectiveTotal <= 0L) return null
    val remainingBytes = (effectiveTotal - bytesDownloaded).coerceAtLeast(0L)
    if (remainingBytes == 0L) return null
    val totalSeconds = (remainingBytes / bytesPerSecond).toLong()
    return when {
        totalSeconds < 60L -> "${totalSeconds}s"
        totalSeconds < 3600L -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
        else -> "${totalSeconds / 3600}h ${(totalSeconds % 3600) / 60}m"
    }
}