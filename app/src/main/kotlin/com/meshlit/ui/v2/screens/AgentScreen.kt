package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.ui.v2.components.MeshlitLeadBar
import com.meshlit.ui.v2.components.MeshlitPillInput

/**
 * v2 Agent screen (replaces `agent/AgentScreen.kt` for the
 * `meshlitV2` build). Renders:
 *   - `MeshlitLeadBar("Agent", "<active model> · <autopilot on/off>")`
 *   - The shared `MeshlitPillInput` at the bottom (IME-send +
 *     mic + thinking toggle + send/stop)
 *   - A summary of the message count + last message for build
 *     no. 1 (the full message list + Markdown rendering lands
 *     in a follow-up PR).
 *
 * State: `collectAsStateWithLifecycle()` on `viewModel.uiState`.
 * The pill input dispatches `viewModel.send(text)` on IME-send.
 */
@Composable
fun AgentScreen(
    modifier: Modifier = Modifier,
    viewModel: AgentViewModel = viewModel(factory = AgentViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = uiState as? AgentUiState.Ready ?: return

    var input by remember { mutableStateOf(TextFieldValue("")) }

    Column(modifier = modifier.fillMaxSize()) {
        MeshlitLeadBar(
            headline = "Agent",
            subtitle = if (ready.activeModel != null) {
                "${ready.activeModel} · ${if (ready.autopilot) "Autopilot" else "Manual"}"
            } else {
                "Idle"
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "${ready.messages.size} messages",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            ready.messages.lastOrNull()?.let { last ->
                val preview = when (last) {
                    is com.meshlit.agent.ChatMessage.UserMessage -> last.text
                    is com.meshlit.agent.ChatMessage.AgentMessage -> last.finalText
                    is com.meshlit.agent.ChatMessage.SystemMessage -> last.text
                }
                Text(
                    text = "Last: ${preview.take(120)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        MeshlitPillInput(
            value = input,
            onValueChange = { input = it },
            onSend = {
                viewModel.send(input.text)
                input = TextFieldValue("")
            },
            onMic = { /* wired in step 4 — voice capture */ },
            onMenu = { /* wired in step 4 — quick action sheet */ },
            isGenerating = ready.isRunning,
            thinkingEnabled = ready.autopilot,
            onToggleThinking = { viewModel.toggleThinking() },
            placeholder = "Ask your cluster…",
        )
    }
}