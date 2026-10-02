package com.meshlit.ui.v2.screens.network

import androidx.compose.runtime.Composable
import com.meshlit.ui.screens.network.NetworkMonitorScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the network monitor. Adds the v2 lead bar
 * above the v1 capture/inspect tabs. `onOpenDrawer` is kept on
 * the v1 signature so the v2 back button can still bubble to
 * the drawer when the screen is reached via a deep link.
 */
@Composable
fun V2NetworkMonitorScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    MeshlitDeepLinkWrap(
        headline = "Network monitor",
        subtitle = "Capture, inspect, export",
    ) {
        NetworkMonitorScreen(onBack = onBack, onOpenDrawer = onOpenDrawer)
    }
}
