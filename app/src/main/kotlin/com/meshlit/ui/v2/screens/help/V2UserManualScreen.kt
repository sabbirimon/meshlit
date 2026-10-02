package com.meshlit.ui.v2.screens.help

import androidx.compose.runtime.Composable
import com.meshlit.ui.screens.help.ManualSection
import com.meshlit.ui.screens.help.UserManualScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the user manual. Adds the v2 lead bar above
 * the v1 manual section list. The `onOpenSection` argument is
 * left optional so the v1 default behavior (no-op) is preserved.
 */
@Composable
fun V2UserManualScreen(
    onBack: () -> Unit,
    onOpenSection: ((ManualSection) -> Unit)? = null,
) {
    MeshlitDeepLinkWrap(
        headline = "Manual",
        subtitle = "How the cluster fits together",
    ) {
        UserManualScreen(onBack = onBack, onOpenSection = onOpenSection)
    }
}
