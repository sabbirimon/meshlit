package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun WebToolsScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var httpFetchEnabled by remember { mutableStateOf(true) }
    var browsingEnabled by remember { mutableStateOf(false) }
    SectionScreen(
        title = "Web & tools",
        subtitle = "HTTP fetch + browsing tools.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Tools", accent = accent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("HTTP fetch", modifier = Modifier.weight(1f))
                    Switch(checked = httpFetchEnabled, onCheckedChange = { httpFetchEnabled = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Headless browser", modifier = Modifier.weight(1f))
                    Switch(checked = browsingEnabled, onCheckedChange = { browsingEnabled = it })
                }
                Text(
                    text = "Settings persist across restart (DataStore).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}
