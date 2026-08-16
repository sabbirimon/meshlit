package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** VLM workbench. Stub for now. */
@Composable
fun VisionWorkbenchScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Vision workbench",
        subtitle = "VLM prompt playground.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Image + prompt", accent = accent) {
                Text("(drop in an image + prompt to run VLM)")
            }
        },
    )
}
