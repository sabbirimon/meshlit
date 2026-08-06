package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Advanced settings. Sliders for opacity, mount points, etc. Stubs
 * persist nothing for now — backend wiring lands in Step 8.
 */
@Composable
fun SettingsScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    var opacity by remember { mutableStateOf(0.85f) }
    var bubbleSize by remember { mutableStateOf(56f) }
    SectionScreen(
        title = "Advanced settings",
        subtitle = "Toggles, opacity, mount points.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Overlay", accent = accent) {
                Text("Opacity ${(opacity * 100).toInt()}%")
                Slider(value = opacity, onValueChange = { opacity = it }, modifier = Modifier.fillMaxWidth())
                Text("Bubble size ${bubbleSize.toInt()}dp")
                Slider(value = bubbleSize, onValueChange = { bubbleSize = it }, valueRange = 32f..96f, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}
