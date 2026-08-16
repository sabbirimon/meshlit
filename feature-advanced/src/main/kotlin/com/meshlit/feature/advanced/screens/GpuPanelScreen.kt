package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * GPU panel. Stub for now: shows a Vulkan toggle and the entries
 * a real `GpuDetector.probe()` will populate. Step 10 lights this up.
 */
@Composable
fun GpuPanelScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var vulkanEnabled by remember { mutableStateOf(true) }
    var lastProbe by remember { mutableStateOf("(no probe yet)") }
    SectionScreen(
        title = "GPU panel",
        subtitle = "Vulkan compute + eGPU status.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        badge = "Vulkan",
        content = {
            SectionCard(title = "Compute backend", accent = accent) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Vulkan compute", modifier = Modifier.weight(1f))
                    Switch(checked = vulkanEnabled, onCheckedChange = { vulkanEnabled = it })
                }
                Button(onClick = { lastProbe = "Probe at ${System.currentTimeMillis()}" }) { Text("Re-probe") }
                Text(lastProbe, style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
