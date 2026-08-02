package com.meshlit.agent

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.launch

/**
 * Claude-Code-style agent surface. Full chat with code blocks,
 * autopilot toggle, and three modes (chat / code / plan).
 *
 * Layout:
 *  ┌──────────────────────────────────────────────┐
 *  │  Header (title + autopilot switch)           │
 *  ├──────────────────────────────────────────────┤
 *  │  Mode chips: [Chat] [Code] [Plan]            │
 *  │  Autopilot switch: [○] Autopilot             │
 *  ├──────────────────────────────────────────────┤
 *  │  $ User message                              │
 *  │  > Agent reply                               │
 *  │  ┌─ kotlin ────────────────────────────────┐  │
 *  │  │ ```kotlin                                │  │
 *  │  │ ...code...                               │  │
 *  │  │                                          │  │
 *  │  │ [Copy] [Apply]                           │  │
 *  │  └──────────────────────────────────────────┘  │
 *  │  (scrollable history)                        │
 *  ├──────────────────────────────────────────────┤
 *  │  [Type a task…]  [Send/Stop]  [Clear]        │
 *  └──────────────────────────────────────────────┘
 *
 * The screen owns an [AgentSession] bound to `rememberCoroutineScope`
 * and reads `session.messages` / `session.isRunning` / `session.mode`
 * as state. The mode chips and autopilot switch are bidirectional
 * (click → session setter; session change → recompose).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val scope = rememberCoroutineScope()
    val session = remember { AgentSession(context, app, scope) }

    val messages by session.messages.collectAsState()
    val isRunning by session.isRunning.collectAsState()
    val mode by session.mode.collectAsState()
    val autopilot by session.autopilot.collectAsState()
    val coordinatorState by app.inferenceCoordinator.state.collectAsState()
    val customPath by app.settingsRepository.customModelPathFlow.collectAsState(initial = "")
    val tier = app.capabilityTier

    // Discover all locally-available model files. Bundled +
    // imported-models directory + the active custom-path override.
    val models by produceState(initialValue = emptyList<ModelEntry>(), app) {
        val bundled = app.bundledModelPath()
        val imported = java.io.File(app.filesDir, "imported-models")
            .takeIf { it.exists() }
            ?.listFiles { f -> f.isFile && f.name.endsWith(".gguf", true) }
            ?.toList()
            ?.map { ModelEntry(it.name, it.absolutePath, ModelEntry.Source.IMPORTED) }
            ?: emptyList()
        val bundledEntry = bundled?.let {
            ModelEntry(it.name, it.absolutePath, ModelEntry.Source.BUNDLED)
        }
        val customEntry = if (customPath.isNotBlank()) {
            val f = java.io.File(customPath)
            if (f.exists()) ModelEntry(f.name, f.absolutePath, ModelEntry.Source.CUSTOM) else null
        } else null
        value = listOfNotNull(bundledEntry) + imported + listOfNotNull(customEntry)
    }

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val activeModelName = (coordinatorState as? CoordinatorState.Ready)?.model?.modelName
        ?: models.firstOrNull()?.displayName
        ?: stringResource(R.string.agent_no_model)

    // ---- Export + attach ----
    var exportSheetVisible by remember { mutableStateOf(false) }
    var exportToast by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val attached = AgentExporter.attachImage(context, uri)
            exportToast = if (attached != null) {
                context.getString(R.string.agent_export_done, attached.lastPathSegment ?: "image")
            } else {
                context.getString(R.string.agent_export_failed, "image")
            }
        }
    }

    fun shareUri(uri: android.net.Uri, mime: String) {
        val intent = AgentExporter.shareIntent(uri, mime).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.agent_share_chooser))
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }.onFailure {
            exportToast = context.getString(R.string.agent_export_failed, it.message ?: "")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.agent_title),
                subtitle = stringResource(R.string.agent_subtitle),
                tier = tier,
                active = isRunning,
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
            ModelPickerBar(
                activeModel = activeModelName,
                models = models,
                isRunning = isRunning,
                onPick = { entry ->
                    scope.launch {
                        session.loadModel(entry.path)
                    }
                },
            )
            ModeBar(
                mode = mode,
                autopilot = autopilot,
                isRunning = isRunning,
                canExport = messages.isNotEmpty(),
                onModeChange = { session.setMode(it) },
                onAutopilotChange = { session.setAutopilot(it) },
                onExport = { exportSheetVisible = true },
            )

            exportToast?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                if (messages.isEmpty()) {
                    EmptyAgent()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(messages) { msg ->
                            when (msg) {
                                is ChatMessage.UserMessage -> UserBubble(msg.text)
                                is ChatMessage.AgentMessage -> AgentBubble(msg)
                                is ChatMessage.SystemMessage -> SystemBubble(msg)
                            }
                        }
                    }
                }
            }

            InputBar(
                input = input,
                isRunning = isRunning,
                onInputChange = { input = it },
                onSend = {
                    val toRun = input
                    input = ""
                    session.send(toRun)
                },
                onStop = { session.cancel() },
                onClear = { session.clear() },
            )
        }
    }

    if (exportSheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { exportSheetVisible = false },
            sheetState = sheetState,
        ) {
            ExportSheet(
                hasCode = messages.any { it is ChatMessage.AgentMessage && it.codeBlocks.isNotEmpty() },
                onSaveFile = {
                    val uri = AgentExporter.writeTranscript(context, messages)
                    if (uri != null) {
                        exportToast = context.getString(
                            R.string.agent_export_done,
                            uri.lastPathSegment ?: "transcript.md",
                        )
                    } else {
                        exportToast = context.getString(R.string.agent_export_failed, "io")
                    }
                    exportSheetVisible = false
                },
                onShareText = {
                    val uri = AgentExporter.writeTranscript(context, messages)
                    if (uri != null) {
                        shareUri(uri, "text/markdown")
                    } else {
                        exportToast = context.getString(R.string.agent_export_failed, "io")
                    }
                    exportSheetVisible = false
                },
                onSaveCode = {
                    val uri = AgentExporter.writeCodeBlocksFile(context, messages)
                    if (uri != null) {
                        exportToast = context.getString(
                            R.string.agent_export_done,
                            uri.lastPathSegment ?: "code.txt",
                        )
                    } else {
                        exportToast = context.getString(R.string.agent_export_failed, "io")
                    }
                    exportSheetVisible = false
                },
                onAttachImage = {
                    runCatching {
                        imagePicker.launch(arrayOf("image/*"))
                    }
                    exportSheetVisible = false
                },
            )
        }
    }
}

@Composable
private fun ExportSheet(
    hasCode: Boolean,
    onSaveFile: () -> Unit,
    onShareText: () -> Unit,
    onSaveCode: () -> Unit,
    onAttachImage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.agent_export_title),
            style = MaterialTheme.typography.titleMedium,
        )
        ExportRow(
            icon = Icons.Filled.IosShare,
            label = stringResource(R.string.agent_export_save_files),
            onClick = onSaveFile,
        )
        ExportRow(
            icon = Icons.Filled.AutoAwesome,
            label = stringResource(R.string.agent_export_share_text),
            onClick = onShareText,
        )
        if (hasCode) {
            ExportRow(
                icon = Icons.Filled.Code,
                label = stringResource(R.string.agent_export_save_code),
                onClick = onSaveCode,
            )
        }
        ExportRow(
            icon = Icons.Filled.Image,
            label = stringResource(R.string.agent_export_save_image),
            onClick = onAttachImage,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExportRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModeBar(
    mode: AgentSession.Mode,
    autopilot: Boolean,
    isRunning: Boolean,
    canExport: Boolean,
    onModeChange: (AgentSession.Mode) -> Unit,
    onAutopilotChange: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeChip(
                label = stringResource(R.string.agent_mode_chat),
                icon = Icons.Outlined.AutoAwesome,
                selected = mode == AgentSession.Mode.CHAT,
                enabled = !isRunning,
                onClick = { onModeChange(AgentSession.Mode.CHAT) },
            )
            ModeChip(
                label = stringResource(R.string.agent_mode_code),
                icon = Icons.Outlined.Code,
                selected = mode == AgentSession.Mode.CODE,
                enabled = !isRunning,
                onClick = { onModeChange(AgentSession.Mode.CODE) },
            )
            ModeChip(
                label = stringResource(R.string.agent_mode_plan),
                icon = Icons.Outlined.PlaylistAddCheck,
                selected = mode == AgentSession.Mode.PLAN,
                enabled = !isRunning,
                onClick = { onModeChange(AgentSession.Mode.PLAN) },
            )
            Spacer(Modifier.weight(1f))
            // Export button: fades in once the conversation has
            // something worth exporting. AnimatedVisibility gives a
            // gentle reveal instead of an abrupt appearance.
            AnimatedVisibility(
                visible = canExport,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                IconButton(
                    onClick = onExport,
                    enabled = canExport,
                ) {
                    Icon(
                        imageVector = Icons.Filled.IosShare,
                        contentDescription = stringResource(R.string.agent_export),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = if (autopilot) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.agent_autopilot),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (autopilot) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = autopilot,
                    onCheckedChange = onAutopilotChange,
                    enabled = !isRunning,
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@Composable
private fun EmptyAgent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.agent_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.agent_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
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
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.agent_role_user),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                // SelectionContainer lets the user long-press to
                // select & copy the bubble text. Wrapped only around
                // the message body so the role label stays
                // non-selectable (it's just chrome).
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentBubble(msg: AgentMessageImpl) {
    val display = if (msg.finalText.isNotEmpty()) msg.finalText else msg.streamingText
    val isStreaming = msg.finalText.isEmpty() && msg.streamingText.isNotEmpty()
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 720.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.agent_role_agent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (msg.tokenCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.agent_token_count,
                                msg.tokenCount,
                                msg.elapsedMs,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (display.isBlank() && isStreaming) {
                    Text(
                        text = stringResource(R.string.agent_thinking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // SelectionContainer lets the user long-press to
                    // select & copy. We skip the wrap while the
                    // message is actively streaming so the selection
                    // anchor doesn't fight with the per-token
                    // recomposition.
                    if (isStreaming) {
                        Text(
                            text = display,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = display,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (msg.codeBlocks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    msg.codeBlocks.forEach { block ->
                        CodeBlockView(block)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemBubble(msg: ChatMessage.SystemMessage) {
    val color = when (msg.kind) {
        ChatMessage.SystemMessage.Kind.ERROR -> MaterialTheme.colorScheme.error
        ChatMessage.SystemMessage.Kind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.agent_role_system),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
private fun CodeBlockView(block: CodeBlock) {
    val clipboard = LocalClipboardManager.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = block.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = {
                        clipboard.setText(AnnotatedString(block.code))
                    },
                    label = { Text(stringResource(R.string.agent_copy)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                ) {
                    Text(
                        text = block.code,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 140.dp),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.agent_input_hint),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            ),
                        )
                    }
                    inner()
                },
            )
            IconButton(
                onClick = onClear,
                enabled = !isRunning,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.agent_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = if (isRunning) onStop else onSend,
                enabled = input.isNotBlank() || isRunning,
            ) {
                if (isRunning) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.agent_stop),
                        tint = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.agent_send),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelPickerBar(
    activeModel: String,
    models: List<ModelEntry>,
    isRunning: Boolean,
    onPick: (ModelEntry) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.agent_active_model),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activeModel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = !isRunning && models.isNotEmpty(),
            ) {
                Text(
                    text = stringResource(R.string.agent_choose_model),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                if (models.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.agent_no_models_available)) },
                        onClick = { menuOpen = false },
                    )
                } else {
                    models.forEach { entry ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = entry.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = entry.sourceLabel(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onPick(entry)
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class ModelEntry(
    val displayName: String,
    val path: String,
    val source: Source,
) {
    enum class Source { BUNDLED, IMPORTED, CUSTOM }

    fun sourceLabel(): String = when (source) {
        Source.BUNDLED -> "Bundled"
        Source.IMPORTED -> "Imported"
        Source.CUSTOM -> "Custom path"
    }
}
