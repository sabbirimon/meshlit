package com.meshlit.ui.v2.screens.help

import androidx.compose.runtime.Composable
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.screens.help.FeedbackScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the feedback screen. Adds a Pulse-gradient
 * lead bar above the v1 `FeedbackScreen` (which keeps its own
 * `Scaffold` + `TopAppBar` for back navigation + the issue
 * form body). The headline + subtitle is the v2 chrome's
 * "Send feedback" identity.
 */
@Composable
fun V2FeedbackScreen(
    settings: SettingsRepository,
    onBack: () -> Unit,
) {
    MeshlitDeepLinkWrap(
        headline = "Feedback",
        subtitle = "Send a bug report or feature request",
    ) {
        FeedbackScreen(settings = settings, onBack = onBack)
    }
}
