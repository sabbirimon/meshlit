package com.meshlit.imagegen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.models.BundleEntryView
import com.meshlit.models.BundleMember
import com.meshlit.models.SdModelBundles
import java.util.Locale

/**
 * Confirmation surface before starting a serial Stable Diffusion
 * bundle import. Keeps large downloads from starting on an
 * accidental tap and makes the on-device disk requirement visible.
 */
@Composable
fun SdImportDialog(
    bundleId: String,
    freeBytes: Long,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val members = SdModelBundles.all[bundleId].orEmpty()
    val estimatedBytes = members.sumOf { member ->
        SdModelBundles.allCatalog[member.entryId]?.approxSizeBytes ?: 0L
    }
    val enoughSpace = freeBytes >= estimatedBytes * 2L
    val displayName = SdModelBundles.displayNames[bundleId] ?: bundleId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download $displayName?") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "This downloads ${members.size} file${if (members.size == 1) "" else "s"} " +
                        "to your private app storage. The import is serial and can be retried safely.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                members.forEach { member ->
                    BundleMemberRow(member)
                }
                Text(
                    "Estimated download: ${formatBytes(estimatedBytes)}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "Disk: ${formatBytes(freeBytes)} free · " +
                        if (enoughSpace) "enough space" else "less than 2× the bundle is free",
                    color = if (enoughSpace) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                enabled = enoughSpace,
            ) { Text("Start download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BundleMemberRow(member: BundleMember) {
    val entry: BundleEntryView? = SdModelBundles.allCatalog[member.entryId]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            entry?.displayName ?: member.entryId,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            entry?.let { formatBytes(it.approxSizeBytes) } ?: "missing",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
