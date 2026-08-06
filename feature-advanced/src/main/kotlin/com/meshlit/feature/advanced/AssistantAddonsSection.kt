package com.meshlit.feature.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshlit.feature.advanced.components.HubCard

/**
 * "Assistant add-ons" cards: document workbench (RAG), vision
 * workbench, document OCR, segmentation, image generation.
 */
@Composable
fun AssistantAddonsSection(
    accent: Color,
    onNavigate: (AdvancedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubCard(
            icon = AdvancedDestination.DocumentWorkbench.icon,
            title = AdvancedDestination.DocumentWorkbench.label,
            subtitle = AdvancedDestination.DocumentWorkbench.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.DocumentWorkbench) },
        )
        HubCard(
            icon = AdvancedDestination.VisionWorkbench.icon,
            title = AdvancedDestination.VisionWorkbench.label,
            subtitle = AdvancedDestination.VisionWorkbench.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.VisionWorkbench) },
        )
        HubCard(
            icon = AdvancedDestination.DocumentOcr.icon,
            title = AdvancedDestination.DocumentOcr.label,
            subtitle = AdvancedDestination.DocumentOcr.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.DocumentOcr) },
        )
        HubCard(
            icon = AdvancedDestination.Segmentation.icon,
            title = AdvancedDestination.Segmentation.label,
            subtitle = AdvancedDestination.Segmentation.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Segmentation) },
        )
        HubCard(
            icon = AdvancedDestination.ImageGeneration.icon,
            title = AdvancedDestination.ImageGeneration.label,
            subtitle = AdvancedDestination.ImageGeneration.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.ImageGeneration) },
            badge = "Stub",
        )
    }
}

/**
 * "Surface" cards: ghosty overlay + MCP server. These are the
 * two app-level settings that previously had no UI surface at all.
 */
@Composable
fun SurfaceAddonsSection(
    accent: Color,
    onNavigate: (AdvancedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubCard(
            icon = AdvancedDestination.GhostySettings.icon,
            title = AdvancedDestination.GhostySettings.label,
            subtitle = AdvancedDestination.GhostySettings.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.GhostySettings) },
        )
        HubCard(
            icon = AdvancedDestination.McpSettings.icon,
            title = AdvancedDestination.McpSettings.label,
            subtitle = AdvancedDestination.McpSettings.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.McpSettings) },
        )
    }
}