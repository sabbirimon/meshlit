package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
 * Phase 4.x — Settings menu rewrite: thin launcher that
 * exists so `CategoryScreen`'s exhaustive `when` can route
 * `SettingsCategory.USER_MANUAL` to a real Composable.
 *
 * The actual user manual lives in
 * `app/src/main/kotlin/com/meshlit/ui/screens/help/UserManualScreen.kt`
 * — this launcher just opens it via the parent nav controller.
 *
 * Visibility:
 *   SIMPLE   — the open-manual row.
 *   ADVANCED — an honest "what this launcher does" explainer
 *              that is hidden in simple mode.
 */
@Composable
fun UserManualLauncherScreen(
    onOpenManual: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()

    val rows = buildList<RowDescriptor> {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "User manual",
                    subtitle = "Every screen, every knob, every keyboard shortcut.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Open user manual",
                    subtitle = "Indexed by topic; searchable.",
                    onClick = onOpenManual,
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                HeaderRow(
                    title = "About this launcher",
                    subtitle = "Reachable from About → User manual, and from the Settings hub.",
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