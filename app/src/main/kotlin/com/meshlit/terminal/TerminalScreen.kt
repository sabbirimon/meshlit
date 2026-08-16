package com.meshlit.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.capability.CapabilityTier
import com.meshlit.di.koinInject
import kotlinx.coroutines.launch

/**
 * Interactive in-app terminal backed by the ghostty-style VT emulator
 * in [com.meshlit.terminal.vt]. Each command runs through
 * [TerminalSession.execute], which feeds every line into the
 * emulator with SGR colors derived from the line kind.
 *
 * Layout (top → bottom):
 *   1. Header (title + tier pill)
 *   2. VT emulator view — 80×24 grid, autoscroll
 *   3. Prompt row: `$ <textfield>` [clear] [send]
 */
@Composable
fun TerminalScreen(
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember { koinInject<MeshlitApplication>() }
    val capabilityTier: CapabilityTier = koinInject()
    val session = remember { TerminalSession(context, app) }
    val scope = rememberCoroutineScope()

    val isRunning by session.isRunning.collectAsState()
    val active = isRunning

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.terminal_title),
                subtitle = stringResource(R.string.terminal_subtitle),
                tier = capabilityTier,
                active = active,
                onOpenDrawer = onOpenDrawer,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TerminalView(
                screen = session.screen,
                onSend = { line -> scope.launch { session.execute(line) } },
                onClear = { session.clear() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
