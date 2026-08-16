package com.meshlit.feature.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshlit.feature.advanced.components.AdvancedHeader
import com.meshlit.feature.advanced.components.SectionHeader

/**
 * Top-level hub for the Advanced workbench. Three flat sections
 * (Speech lab · Developer diagnostics · Assistant add-ons) plus
 * the surface add-ons (Ghosty + MCP) that have no natural home
 * elsewhere.
 *
 * Renders the same [AdvancedDestination] enum that the internal
 * NavHost uses, so adding a new destination touches exactly two
 * places (enum entry + which section card list it lives in).
 */
@Composable
fun AdvancedScreen(
    accent: Color,
    accentDim: Color,
    onNavigate: (AdvancedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        AdvancedHeader(
            title = "Advanced",
            subtitle = "Speech, diagnostics, add-ons",
            accent = accent,
            accentDim = accentDim,
            onBack = null,
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SectionHeader(
                title = "Speech lab",
                caption = "Speech-to-text, text-to-speech and voice activity detection.",
            )
            SpeechLabSection(
                accent = accent,
                onNavigate = onNavigate,
            )
            SectionHeader(
                title = "Developer diagnostics",
                caption = "Tools, solutions, benchmarks and the GPU panel.",
            )
            DeveloperDiagnosticsSection(
                accent = accent,
                onNavigate = onNavigate,
            )
            SectionHeader(
                title = "Assistant add-ons",
                caption = "Document, vision and image side-kicks.",
            )
            AssistantAddonsSection(
                accent = accent,
                onNavigate = onNavigate,
            )
            SectionHeader(
                title = "Surface add-ons",
                caption = "Floating overlays and embedded HTTP servers.",
            )
            SurfaceAddonsSection(
                accent = accent,
                onNavigate = onNavigate,
            )
        }
    }
}
