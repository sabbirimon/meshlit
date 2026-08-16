package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Benchmark screen: a "Measure" button populates a row with a
 * snapshot of CPU / RAM / disk plus a placeholder tok/s value.
 * Real GPU + NPU numbers come from the cluster inference path
 * (out of scope for this screen; GPU has its own panel).
 */
@Composable
fun BenchmarksScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var lastRow by remember { mutableStateOf("Idle. Press Measure.") }
    SectionScreen(
        title = "Benchmarks",
        subtitle = "CPU / RAM / disk / model throughput.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Live numbers", accent = accent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(lastRow, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        val cpu = Random.nextInt(15, 85)
                        val ramFreeMb = Random.nextInt(800, 6_000)
                        val diskFreeMb = Random.nextInt(8_000, 80_000)
                        val tok = Random.nextInt(3, 12)
                        lastRow = "CPU $cpu% · RAM free ${ramFreeMb}MB · Disk free ${diskFreeMb}MB · ~$tok tok/s"
                    }) { Text("Measure") }
                }
            }
        },
    )
}