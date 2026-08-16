package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.rag.RagDecision
import com.meshlit.core.cloudmcp.rag.RagMode

/**
 * Live agent terminal. Consumes [McpEvent]s from the
 * CloudMcpCoordinator and renders them as a vertically scrolling
 * card list. Two display modes:
 *  - **Live stream** — newest at top, auto-scrolls into view on
 *    each event.
 *  - **Step-by-step log** — same events rendered as a numbered
 *    static list without auto-scroll.
 *
 * The mode is controlled by the user via Settings → RAG/Loop and
 * flows in via [loopMode]. The composer at the bottom sends a
 * prompt to the cloud LLM (NaraRouter); the agent loop routes
 * any `tool_calls` back to the matching CloudMcpSession and
 * streams results back to the LLM.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AgentTerminalScreen(
    providerId: String?,
    loopMode: AgentLoopMode,
    ragMode: RagMode,
    ragDecision: RagDecision?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onLoopModeChange: (AgentLoopMode) -> Unit = {},
) {
    val events = remember { mutableStateListOf<McpEvent>() }
    var prompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = providerId?.let { "Terminal · $it" }
                            ?: stringResource(R.string.cloud_open_terminal),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RagIndicatorChip(mode = ragMode, state = ragDecision)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            // Loop-mode toggle.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                androidx.compose.material3.FilterChip(
                    selected = loopMode == AgentLoopMode.Live,
                    onClick = { onLoopModeChange(AgentLoopMode.Live) },
                    label = { Text(stringResource(R.string.cloud_loop_live)) },
                )
                androidx.compose.material3.FilterChip(
                    selected = loopMode == AgentLoopMode.Step,
                    onClick = { onLoopModeChange(AgentLoopMode.Step) },
                    label = { Text(stringResource(R.string.cloud_loop_step)) },
                )
            }

            // Event stream.
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.cloud_terminal_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    reverseLayout = loopMode == AgentLoopMode.Live,
                ) {
                    items(events) { ev ->
                        EventCard(event = ev, index = events.indexOf(ev) + 1)
                    }
                }
            }

            // Composer.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(stringResource(R.string.cloud_prompt_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        if (prompt.isNotBlank()) {
                            events.add(McpEvent.Thought(providerId ?: "nara", "You: $prompt"))
                            onSend(prompt)
                            prompt = ""
                        }
                    },
                    enabled = prompt.isNotBlank(),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cloud_send),
                    )
                }
            }
        }
    }
}

enum class AgentLoopMode { Live, Step }

@Composable
private fun EventCard(event: McpEvent, index: Int) {
    val containerColor: Color = when (event) {
        is McpEvent.Thought -> MaterialTheme.colorScheme.surfaceVariant
        is McpEvent.ToolCall -> MaterialTheme.colorScheme.surfaceVariant
        is McpEvent.ToolResult -> if (event.ok) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        is McpEvent.Error -> MaterialTheme.colorScheme.errorContainer
        is McpEvent.Connected -> MaterialTheme.colorScheme.tertiaryContainer
        is McpEvent.Disconnected -> MaterialTheme.colorScheme.surfaceVariant
        is McpEvent.Done -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "#$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = when (event) {
                    is McpEvent.Thought -> stringResource(
                        R.string.cloud_terminal_thought, event.text,
                    )
                    is McpEvent.ToolCall -> stringResource(
                        R.string.cloud_terminal_tool_call, event.name, event.args.toString(),
                    )
                    is McpEvent.ToolResult -> stringResource(
                        R.string.cloud_terminal_tool_result, event.body,
                    )
                    is McpEvent.Error -> stringResource(
                        R.string.cloud_terminal_error, event.message,
                    )
                    is McpEvent.Connected -> "Connected (${event.tools.size} tools)"
                    is McpEvent.Disconnected -> "Disconnected"
                    is McpEvent.Done -> "Done"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}