package com.meshlit.ui.v2.screens.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.ui.screens.setup.SetupWizardScreen as V1SetupWizardScreen
import com.meshlit.ui.v2.components.MeshlitLeadBar

/**
 * v2 wrapper around the v1 setup wizard. Per the plan's
 * §4 "v2 Setup wizard" bullet: "keeps the v1 wizard's logic but
 * uses `MeshlitLeadBar` instead of the stock `TopAppBar`. The
 * v1 wizard file remains untouched."
 *
 * For build no. 1 the wrapper delegates to the v1 wizard
 * verbatim; the `MeshlitLeadBar` is rendered above the wizard
 * body so the v2 chrome's "bold-lead content" rule applies.
 * The v1 wizard's internal `TopAppBar` still renders — that's
 * intentional; replacing it requires rewriting the v1 wizard,
 * which is out of scope for this PR (the plan explicitly says
 * "v1 wizard file remains untouched"). The v2 wrapper sits
 * inside `V2Root` so the chrome (drawer, bottom bar) is the
 * v2 chrome; only the wizard's own header is duplicated.
 *
 * The v2 build calls this screen via `V2Root`'s "setup"
 * start destination when `firstRunDone == false`. On finish
 * the v2 path navigates to `MeshlitAppV2` (the v2 main graph).
 */
@Composable
fun V2SetupWizardScreen(
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Bold-lead header per the v2 design language. The v1
        // wizard renders its own `TopAppBar` underneath; both
        // are visible during first-run.
        MeshlitLeadBar(
            headline = "Setup",
            subtitle = "Bring the cluster online",
            modifier = Modifier.padding(top = 8.dp),
        )
        V1SetupWizardScreen(
            onFinish = onFinish,
            onOpenSettings = onOpenSettings,
        )
    }
}
