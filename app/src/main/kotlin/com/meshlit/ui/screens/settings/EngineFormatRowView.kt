package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Render one row of the supported-formats card. */
@Composable
internal fun EngineFormatRowView(row: EngineFormatRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.formatLabel(),
            style = MaterialTheme.typography.titleSmall,
            color = if (row.isShipped) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.statusLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (row.isShipped) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "runtime: ${row.runtimeLabel()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}