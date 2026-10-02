package com.meshlit.ui.v2.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared wrapper for the v2 deep-link screens (Feedback, UiTour,
 * UserManual, HelpHub, AgentTerminal, NetworkMonitor). Each v2
 * route renders this composable with a headline/subtitle and a
 * slot that invokes the existing v1 screen — v1 keeps its own
 * `Scaffold + TopAppBar` so the v1 source tree is untouched and
 * the v2 build still composes the legacy chrome underneath the
 * Pulse-gradient lead bar.
 *
 * The wrapper reads `Modifier.fillMaxSize()` so callers don't
 * need to chain it. The wrapped v1 screen is responsible for its
 * own nested scroll / Surface treatment.
 */
@Composable
fun MeshlitDeepLinkWrap(
    headline: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MeshlitLeadBar(headline = headline, subtitle = subtitle)
        content()
    }
}
