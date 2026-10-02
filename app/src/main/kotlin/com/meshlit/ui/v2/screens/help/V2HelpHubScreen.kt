package com.meshlit.ui.v2.screens.help

import androidx.compose.runtime.Composable
import com.meshlit.ui.screens.help.HelpHubScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the help hub. The hub fans out to the manual,
 * the tour, and the feedback screen — the lead bar gives the
 * entry a Pulse-gradient identity, the v1 hub keeps its
 * category card grid + back button.
 */
@Composable
fun V2HelpHubScreen(
    onBack: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenTour: () -> Unit,
    onOpenFeedback: () -> Unit,
) {
    MeshlitDeepLinkWrap(
        headline = "Help",
        subtitle = "Manual, tour, and feedback",
    ) {
        HelpHubScreen(
            onBack = onBack,
            onOpenManual = onOpenManual,
            onOpenTour = onOpenTour,
            onOpenFeedback = onOpenFeedback,
        )
    }
}
