package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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

/** Phase 4.x — Settings menu rewrite: launcher for
 *  `FeedbackScreen`. */
@Composable
fun FeedbackLauncherScreen(
    onOpenFeedback: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()

    val rows = buildList<RowDescriptor> {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Send feedback",
                    subtitle = "Email or GitHub issue template prefilled with version + node id.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.AutoMirrored.Filled.Send,
                    title = "Open feedback",
                    subtitle = "Subject, body, and device metadata pre-populated.",
                    onClick = onOpenFeedback,
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