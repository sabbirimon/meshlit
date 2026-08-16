package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Segmentation destination. Stub for now. */
@Composable
fun SegmentationScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Segmentation",
        subtitle = "SegFormer pixel masks.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Image", accent = accent) {
                Text("(drop in an image)")
            }
        },
    )
}
