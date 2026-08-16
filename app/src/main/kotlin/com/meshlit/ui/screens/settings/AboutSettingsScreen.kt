package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.settings.visibility.RowDescriptor
import com.meshlit.settings.visibility.SettingsVisibility
import com.meshlit.settings.visibility.Visibility

/**
 * Phase 4.x — Settings menu rewrite: dedicated About screen
 * with the app version, manual deep-link, third-party credits,
 * and a single tap to send feedback.
 *
 * Reaches the existing `UserManualScreen`,
 * `HelpHubScreen`, `FeedbackScreen`, and `UiTourScreen`
 * (in the `ui/screens/help/` package) via launcher stubs.
 *
 * Visibility:
 *   SIMPLE   — version, capability tier, manual / help / tour /
 *              feedback launcher rows.
 *   ADVANCED — diagnostics, third-party credits, build label.
 */
@Composable
fun AboutSettingsScreen(
    onOpenManual: () -> Unit,
    onOpenHelpHub: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenUiTour: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()

    val rows = buildList<RowDescriptor> {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Meshlit ${com.meshlit.BuildConfig.VERSION_NAME}",
                    subtitle = "Build ${com.meshlit.BuildConfig.VERSION_CODE} · ${
                        runCatching { app.nodeIdHex.take(8) }.getOrDefault("—")
                    }…",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                ValueRow(
                    title = "Capability tier",
                    value = app.capabilityTier.name,
                    subtitle = "Computed from chipset + RAM at cold start.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Documentation",
                    subtitle = "Full manual, common questions, in-app tour.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Info,
                    title = "User manual",
                    subtitle = "In-app guide indexed by topic.",
                    onClick = onOpenManual,
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Info,
                    title = "Help hub",
                    subtitle = "Common questions and workarounds.",
                    onClick = onOpenHelpHub,
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Info,
                    title = "UI tour",
                    subtitle = "5-minute walkthrough of the bottom-bar and drawers.",
                    onClick = onOpenUiTour,
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Support",
                    subtitle = "Send feedback directly from the app.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Info,
                    title = "Send feedback",
                    subtitle = "Opens an email prefilled with version + node id.",
                    onClick = onOpenFeedback,
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                HeaderRow(
                    title = "Credits",
                    subtitle = "RunAnywhere SDK, llama.cpp, Maple Mono, Compose.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                ValueRow(
                    title = "Build label",
                    value = "debug",
                    subtitle = "Release builds stamp the channel here.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Build,
                    title = "Diagnostic snapshot",
                    subtitle = "If the app crashed, find the latest dump under Files.",
                    onClick = { /* handled by parent - opens Files screen */ },
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SettingsVisibility.Render(rows = rows, simpleMode = simple)
        }
    }
}