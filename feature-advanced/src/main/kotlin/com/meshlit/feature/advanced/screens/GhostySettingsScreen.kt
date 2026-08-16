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

/**
 * Ghosty settings. The "Enable Ghosty" toggle persists via the
 * app singleton. Service-level wiring lands in Step 7 (the
 * `feature-ghosty` overlay module). For now this screen owns the
 * flags and explains what's coming.
 */
@Composable
fun GhostySettingsScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
    initialEnabled: Boolean = false,
    onEnabledChange: (Boolean) -> Unit = {},
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var autoShow by remember { mutableStateOf(true) }
    var hotWord by remember { mutableStateOf(false) }
    SectionScreen(
        title = "Ghosty settings",
        subtitle = "Floating chat overlay.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Overlay", accent = accent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable Ghosty", modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onEnabledChange(it)
                        },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Auto-show", modifier = Modifier.weight(1f))
                    Switch(checked = autoShow, onCheckedChange = { autoShow = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Hot-word", modifier = Modifier.weight(1f))
                    Switch(checked = hotWord, onCheckedChange = { hotWord = it })
                }
                Text(
                    text = "Grant SYSTEM_ALERT_WINDOW + specialUse FGS to enable.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )
}
