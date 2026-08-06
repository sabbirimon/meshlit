package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.rag.RagMode
import com.meshlit.ui.screens.cloud.AgentLoopMode


/**
 * Settings for RAG backend mode (Local / Remote / Auto / Ask) and
 * agent-loop display mode (Live stream / Step-by-step log).
 *
 * Persists to `SettingsRepository` (the existing DataStore-backed
 * repo — see `core-orchestration`). The mode flows into the
 * Cloud Hub, Agent Terminal, and the Settings hub search.
 */
@Composable
fun RagSettingsScreen(
    initialRagMode: RagMode,
    initialLoopMode: AgentLoopMode,
    onRagModeChange: (RagMode) -> Unit,
    onLoopModeChange: (AgentLoopMode) -> Unit,
) {
    var ragMode by remember { mutableStateOf(initialRagMode) }
    var loopMode by remember { mutableStateOf(initialLoopMode) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(title = stringResource(R.string.cloud_settings_rag_title)) {
                Text(
                    text = stringResource(R.string.cloud_settings_rag_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RagMode.entries.forEach { mode ->
                        FilterChip(
                            selected = ragMode == mode,
                            onClick = {
                                ragMode = mode
                                onRagModeChange(mode)
                            },
                            label = {
                                Text(
                                    text = when (mode) {
                                        RagMode.Local -> stringResource(R.string.cloud_rag_local)
                                        RagMode.Remote -> stringResource(R.string.cloud_rag_remote)
                                        RagMode.Auto -> stringResource(R.string.cloud_rag_auto)
                                        RagMode.Ask -> stringResource(R.string.cloud_rag_ask)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            Section(title = stringResource(R.string.cloud_settings_loop_title)) {
                Text(
                    text = stringResource(R.string.cloud_settings_loop_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AgentLoopMode.entries.forEach { mode ->
                        FilterChip(
                            selected = loopMode == mode,
                            onClick = {
                                loopMode = mode
                                onLoopModeChange(mode)
                            },
                            label = {
                                Text(
                                    text = when (mode) {
                                        AgentLoopMode.Live -> stringResource(R.string.cloud_loop_live)
                                        AgentLoopMode.Step -> stringResource(R.string.cloud_loop_step)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        content()
    }
}