package com.meshlit.ui.v2.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.core.inference.FinishReason
import com.meshlit.ui.components.LlmOutputActions
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSuccess
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.theme.MeshlitWarning

/**
 * New v2 chat screen. Clean Material 3 surface:
 *
 *  1. **Top bar** — title + backend chip (engine + loaded model) +
 *     "Clear" action.
 *  2. **Model picker card** — only shown when no model is loaded or
 *     the user taps the chip. Lists imported models in
 *     `filesDir/imported-models/`. Tapping one calls
 *     [ChatViewModel.loadModel].
 *  3. **Conversation scroll** — `LazyColumn` with bubbles per
 *     [ChatMessage]. The streaming assistant bubble mutates in
 *     place as tokens arrive. Auto-scrolls to the bottom while
 *     generating.
 *  4. **Empty state** — three suggestion chips that prefill the
 *     composer (since users tend to type "hello" first).
 *  5. **Composer** — multi-line `OutlinedTextField` + Send/Stop
 *     button. IME `action = Send` submits. Composer is hidden
 *     while a generation is in flight only if no draft is in
 *     flight (we keep it visible so the user can queue the next
 *     prompt).
 *  6. **Output action toolbar** — `LlmOutputActions` (Copy / Save /
 *     Export / Share / Regenerate) under every finished assistant
 *     bubble. Already wires the real Save/Export paths from the
 *     commit that landed alongside this screen.
 *
 * Loading the screen for the first time:
 *  - Mount → [ChatViewModel] resolves the coordinator singleton +
 *    subscribes to its `state` flow.
 *  - `refreshAvailableModels(context)` enumerates `filesDir/imported-models/`.
 *  - The user picks a model (or has one loaded already), types,
 *    taps Send, sees tokens stream.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.refreshAvailableModels(context)
    }

    var showModelPicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MeshlitInk,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            ChatTopBar(
                status = state.coordinatorStatus,
                loadedModelName = state.loadedModel?.modelName?.substringAfterLast('/'),
                engineTag = state.engineTag,
                onOpenModelPicker = { showModelPicker = true },
                onClear = viewModel::clearConversation,
                onOpenAdvanced = { showAdvanced = true },
            )
            HorizontalDivider(color = MeshlitOutlineV2.copy(alpha = 0.3f))

            if (showModelPicker) {
                ModelPickerCard(
                    available = state.availableModels,
                    loadedPath = state.loadedModel?.modelPath,
                    onPick = { path ->
                        viewModel.loadModel(path)
                        showModelPicker = false
                    },
                    onClose = { showModelPicker = false },
                )
            }
            if (showAdvanced) {
                AdvancedCard(
                    maxTokens = state.maxTokens,
                    temperature = state.temperature,
                    onMaxTokens = viewModel::setMaxTokens,
                    onTemperature = viewModel::setTemperature,
                    onClose = { showAdvanced = false },
                )
            }

            ConversationList(
                messages = state.messages,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            if (state.showEmptyState) {
                EmptyState(
                    onPrompt = { viewModel.onDraftChange(it) },
                )
            }

            if (state.lastError != null && !state.isGenerating) {
                ErrorBanner(
                    message = state.lastError!!,
                    onRetry = viewModel::retryLast,
                    onDismiss = { viewModel.onDraftChange(state.draft) /* keep draft, clear error via re-render only */ },
                )
            }

            Composer(
                draft = state.draft,
                onDraftChange = viewModel::onDraftChange,
                isGenerating = state.isGenerating,
                canSend = state.canSend,
                onSend = { viewModel.send(state.draft) },
                onStop = viewModel::cancel,
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    status: String,
    loadedModelName: String?,
    engineTag: String,
    onOpenModelPicker: () -> Unit,
    onClear: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Chat",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MeshlitTextPrimaryV2,
        )
        Spacer(modifier = Modifier.weight(1f))
        BackendChip(
            status = status,
            loadedModelName = loadedModelName,
            engineTag = engineTag,
            onClick = onOpenModelPicker,
        )
        IconButton(onClick = onOpenAdvanced) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Generation settings",
                tint = MeshlitTextSecondaryV2,
            )
        }
        TextButton(onClick = onClear) {
            Text("Clear", color = MeshlitTextSecondaryV2)
        }
    }
}

@Composable
private fun BackendChip(
    status: String,
    loadedModelName: String?,
    engineTag: String,
    onClick: () -> Unit,
) {
    val isError = status.startsWith("Error")
    val isGenerating = status.contains("generating")
    val tint = when {
        isError -> MeshlitWarning
        isGenerating -> MeshlitPulseViolet
        loadedModelName != null -> MeshlitSuccess
        else -> MeshlitPulseViolet
    }
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, RoundedCornerShape(4.dp)),
            )
            Text(
                text = if (loadedModelName != null) {
                    "${engineTag.ifEmpty { "engine" }} · ${truncateName(loadedModelName)}"
                } else {
                    "No model loaded"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MeshlitTextPrimaryV2,
            )
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MeshlitPulseViolet,
                )
            }
        }
    }
}

private fun truncateName(name: String): String =
    if (name.length > 28) name.take(25) + "…" else name

@Composable
private fun ModelPickerCard(
    available: List<AvailableModel>,
    loadedPath: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MeshlitPulseViolet,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pick a model",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshlitTextPrimaryV2,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done", color = MeshlitPulseViolet) }
            }
            Text(
                text = if (available.isEmpty()) {
                    "No models downloaded yet. Use the Models tab to import one."
                } else {
                    "${available.size} model${if (available.size == 1) "" else "s"} on this device"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextTertiaryV2,
            )
            available.forEach { model ->
                ModelPickerRow(
                    model = model,
                    loaded = model.path == loadedPath,
                    onPick = { onPick(model.path) },
                )
            }
        }
    }
}

@Composable
private fun ModelPickerRow(
    model: AvailableModel,
    loaded: Boolean,
    onPick: () -> Unit,
) {
    Surface(
        color = if (loaded) MeshlitSurfaceHigh else MeshlitSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Memory,
                contentDescription = null,
                tint = if (loaded) MeshlitSuccess else MeshlitPulseViolet,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "${model.sizeMb} MB · ${model.path.substringAfterLast('/')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
            if (loaded) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Loaded",
                    tint = MeshlitSuccess,
                )
            }
        }
    }
}

@Composable
private fun AdvancedCard(
    maxTokens: Int,
    temperature: Float,
    onMaxTokens: (Int) -> Unit,
    onTemperature: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var tokensInput by remember(maxTokens) { mutableStateOf(maxTokens.toString()) }
    var tempInput by remember(temperature) { mutableStateOf("%.2f".format(temperature)) }
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Generation settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeshlitTextPrimaryV2,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done", color = MeshlitPulseViolet) }
            }
            AdvancedRow(
                label = "Max tokens",
                value = tokensInput,
                onValue = { v ->
                    tokensInput = v
                    v.toIntOrNull()?.let(onMaxTokens)
                },
                hint = "16 – 2048",
            )
            AdvancedRow(
                label = "Temperature",
                value = tempInput,
                onValue = { v ->
                    tempInput = v
                    v.toFloatOrNull()?.let(onTemperature)
                },
                hint = "0.0 – 2.0",
            )
        }
    }
}

@Composable
private fun AdvancedRow(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshlitTextPrimaryV2,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MeshlitTextTertiaryV2,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeshlitPulseViolet,
                unfocusedBorderColor = MeshlitOutlineV2,
                cursorColor = MeshlitPulseViolet,
                focusedTextColor = MeshlitTextPrimaryV2,
                unfocusedTextColor = MeshlitTextPrimaryV2,
            ),
        )
    }
}

@Composable
private fun ConversationList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            when (msg) {
                is ChatMessage.User -> UserBubble(text = msg.text)
                is ChatMessage.Assistant -> AssistantBubble(message = msg)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MeshlitPulseViolet.copy(alpha = 0.18f),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MeshlitPulseViolet.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshlitTextPrimaryV2,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage.Assistant) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(modifier = Modifier.widthIn(max = 360.dp)) {
            Surface(
                color = when {
                    message.isError -> MeshlitSurfaceHigh.copy(alpha = 0.95f)
                    else -> MeshlitSurfaceContainer
                },
                shape = RoundedCornerShape(18.dp),
                border = if (message.isError) {
                    androidx.compose.foundation.BorderStroke(1.dp, MeshlitWarning.copy(alpha = 0.6f))
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, MeshlitOutlineV2.copy(alpha = 0.4f))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshlitTextPrimaryV2,
                        )
                    } else if (message.isStreaming) {
                        Text(
                            text = "Generating…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeshlitTextTertiaryV2,
                        )
                    }
                    if (message.isError) {
                        Text(
                            text = message.hint ?: message.errorTag ?: "Inference failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshlitWarning,
                        )
                    }
                    if (message.tokensLabel.isNotEmpty()) {
                        Text(
                            text = message.tokensLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshlitTextTertiaryV2,
                        )
                    }
                }
            }
            // Only render the action toolbar for finished assistant
            // messages — no Copy/Save/Export buttons while streaming.
            if (!message.isStreaming) {
                Spacer(Modifier.height(4.dp))
                LlmOutputActions(
                    text = message.text.ifEmpty { "(empty reply)" },
                    onRegenerate = null,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    onPrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What can I help with?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MeshlitTextPrimaryV2,
        )
        Text(
            text = "Pick a model above, then type a prompt. All inference stays on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MeshlitTextSecondaryV2,
        )
        SuggestionChips(onPrompt = onPrompt)
    }
}

@Composable
private fun SuggestionChips(onPrompt: (String) -> Unit) {
    val suggestions = listOf(
        "Summarize the last 24 hours of news",
        "Write a haiku about latency",
        "Explain what an LLM KV cache is",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { prompt ->
            Surface(
                color = MeshlitSurfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable { onPrompt(prompt) },
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextSecondaryV2,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeshlitWarning.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitWarning,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MeshlitPulseViolet,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Retry", color = MeshlitPulseViolet)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Dismiss",
                    tint = MeshlitTextTertiaryV2,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isGenerating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MeshlitSurface,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp),
                placeholder = {
                    Text(
                        text = "Type a prompt…",
                        color = MeshlitTextTertiaryV2,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() },
                ),
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshlitPulseViolet,
                    unfocusedBorderColor = MeshlitOutlineV2.copy(alpha = 0.6f),
                    cursorColor = MeshlitPulseViolet,
                    focusedTextColor = MeshlitTextPrimaryV2,
                    unfocusedTextColor = MeshlitTextPrimaryV2,
                    focusedContainerColor = MeshlitSurfaceContainer,
                    unfocusedContainerColor = MeshlitSurfaceContainer,
                ),
            )
            SendOrStopButton(
                isGenerating = isGenerating,
                canSend = canSend,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun SendOrStopButton(
    isGenerating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    if (isGenerating) {
        Surface(
            color = MeshlitWarning.copy(alpha = 0.2f),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MeshlitWarning.copy(alpha = 0.5f)),
            modifier = Modifier.clickable(onClick = onStop),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop generation",
                    tint = MeshlitWarning,
                )
            }
        }
    } else {
        Surface(
            color = if (canSend) MeshlitPulseViolet else MeshlitSurfaceHigh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable(enabled = canSend, onClick = onSend),
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else MeshlitTextTertiaryV2,
                )
            }
        }
    }
}