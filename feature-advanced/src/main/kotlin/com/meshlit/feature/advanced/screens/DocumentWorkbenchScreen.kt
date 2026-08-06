package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** RAG workbench. Stub for now. */
@Composable
fun DocumentWorkbenchScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    SectionScreen(
        title = "Document workbench",
        subtitle = "RAG over your local files.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Pinned folders", accent = accent) {
                Text("(no folders pinned)")
            }
        },
    )
}