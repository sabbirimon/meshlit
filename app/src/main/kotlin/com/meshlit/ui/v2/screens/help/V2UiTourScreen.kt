package com.meshlit.ui.v2.screens.help

import androidx.compose.runtime.Composable
import com.meshlit.setup.FirstRunSetupRepository
import com.meshlit.ui.nav.TopLevelDestination
import com.meshlit.ui.screens.help.UiTourScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the UI tour. Re-skins the chrome with a
 * `MeshlitLeadBar` ("Tour" / "Walk through the cluster") so
 * the tour entry point matches the v2 design language without
 * rewriting the v1 tour logic.
 */
@Composable
fun V2UiTourScreen(
    firstRun: FirstRunSetupRepository,
    onBack: () -> Unit,
    onOpenDestination: (TopLevelDestination) -> Unit,
) {
    MeshlitDeepLinkWrap(
        headline = "Tour",
        subtitle = "Walk through the cluster",
    ) {
        UiTourScreen(
            firstRun = firstRun,
            onBack = onBack,
            onOpenDestination = onOpenDestination,
        )
    }
}
