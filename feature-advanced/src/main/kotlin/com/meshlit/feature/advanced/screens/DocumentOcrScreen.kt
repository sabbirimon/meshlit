package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Document OCR destination. Stub for now. */
@Composable
fun DocumentOcrScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Document OCR",
        subtitle = "Nemotron OCR for scanned PDFs.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Drop a PDF", accent = accent) {
                Text("(drop here)")
            }
        },
    )
}
