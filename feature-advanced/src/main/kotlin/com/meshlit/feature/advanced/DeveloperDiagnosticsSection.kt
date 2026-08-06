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
 * "Developer diagnostics" cards: web tools, solutions, cloud
 * providers, benchmarks, GPU panel, advanced settings, storage.
 */
@Composable
fun DeveloperDiagnosticsSection(
    accent: Color,
    onNavigate: (AdvancedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubCard(
            icon = AdvancedDestination.WebTools.icon,
            title = AdvancedDestination.WebTools.label,
            subtitle = AdvancedDestination.WebTools.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.WebTools) },
        )
        HubCard(
            icon = AdvancedDestination.Solutions.icon,
            title = AdvancedDestination.Solutions.label,
            subtitle = AdvancedDestination.Solutions.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Solutions) },
        )
        HubCard(
            icon = AdvancedDestination.CloudProviders.icon,
            title = AdvancedDestination.CloudProviders.label,
            subtitle = AdvancedDestination.CloudProviders.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.CloudProviders) },
        )
        HubCard(
            icon = AdvancedDestination.Benchmarks.icon,
            title = AdvancedDestination.Benchmarks.label,
            subtitle = AdvancedDestination.Benchmarks.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Benchmarks) },
        )
        HubCard(
            icon = AdvancedDestination.GpuPanel.icon,
            title = AdvancedDestination.GpuPanel.label,
            subtitle = AdvancedDestination.GpuPanel.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.GpuPanel) },
            badge = "Vulkan",
        )
        HubCard(
            icon = AdvancedDestination.Settings.icon,
            title = AdvancedDestination.Settings.label,
            subtitle = AdvancedDestination.Settings.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Settings) },
        )
        HubCard(
            icon = AdvancedDestination.Storage.icon,
            title = AdvancedDestination.Storage.label,
            subtitle = AdvancedDestination.Storage.description,
            accent = accent,
            onClick = { onNavigate(AdvancedDestination.Storage) },
        )
    }
}