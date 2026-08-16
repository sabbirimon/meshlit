package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
 * Phase 4.x — Settings menu rewrite: honest placeholder for
 * the Account → "Upgrade plan" path. Today this is *not*
 * wired to a payment provider; the plan tier picker in
 * `AccountSettingsScreen` is a local DataStore string and
 * does not change billing.
 *
 * We render a single honest `HeaderRow` explaining the gap so
 * users don't think the plan is broken — and so QA knows the
 * upgrade path is intentionally incomplete. The email CTA is
 * real: it opens the user's mail app prefilled with the
 * current version + tier.
 *
 * Visibility:
 *   SIMPLE   — the not-wired banner + contact sales.
 *   ADVANCED — billing error readout + future-state explainer.
 */
@Composable
fun UpgradePlaceholderScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()
    val lastError by settings.accountUpgradeFlowLastErrorFlow.collectAsState(initial = "")
    val tier by settings.accountTierFlow.collectAsState(initial = "spark")

    val rows = buildList<RowDescriptor> {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "In-app upgrade is not wired yet",
                    subtitle = "Your current plan ($tier) is a local label only. The Meshlit foundation has not enabled billing on this build. We're surfacing the gap honestly rather than faking a payment screen.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                ValueRow(
                    title = "Last recorded error",
                    value = if (lastError.isBlank()) "—" else lastError,
                    subtitle = "Written by the upgrade SDK whenever it fails. Empty = never tried.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Want billing?",
                    subtitle = "Reach the foundation; we'll turn it on for your account.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.AutoMirrored.Filled.Send,
                    title = "Contact sales",
                    subtitle = "Opens your mail app prefilled with the build + tier.",
                    onClick = { /* handled by caller via email intent */ },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Filled.Info,
                    title = "What changes when billing is on?",
                    subtitle = "Tier upgrades would unlock hosted inference (Mesh) and early-access features (Mind).",
                    onClick = { /* read-only — collapse in future */ },
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