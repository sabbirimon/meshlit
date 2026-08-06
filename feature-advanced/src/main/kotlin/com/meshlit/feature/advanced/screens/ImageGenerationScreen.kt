package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Image generation destination. Stub for now. */
@Composable
fun ImageGenerationScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Image generation",
        subtitle = "Cosmos3 diffusion (stub).",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        badge = "Stub",
        content = {
            SectionCard(title = "Prompt", accent = accent) {
                Text("(prompt box)")
            }
        },
    )
}
