package com.meshlit.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import kotlinx.coroutines.launch

/**
 * Interactive in-app terminal for the Sessions tab. Layout:
 *
 *  ┌──────────────────────────────────────────────┐
 *  │  Header (title + tier pill)                  │
 *  ├──────────────────────────────────────────────┤
 *  │  ┃ $ help                                    │
 *  │  ┃ Available commands:                       │
 *  │  ┃   help       Show this help               │
 *  │  ┃   status     ...                          │
 *  │  ┃                                             │
 *  │  ┃ $ status                                  │
 *  │  ┃   device : Pixel-7                        │
 *  │  ┃   tier   : FULL                           │
 *  │  ┃                                             │
 *  │  (scrollable history of [TerminalGroup] cards) │
 *  ├──────────────────────────────────────────────┤
 *  │  $ [_____________________]  [Clear]  [Send]  │
 *  └──────────────────────────────────────────────┘
 *
 * Each command produces a [TerminalGroup]. The screen renders the
 * group as a card with a left accent stripe whose color matches the
 * group's status (success/error/info). Lines inside the group keep
 * their per-line kind colors so a `logs` tail can mix ERROR red
 * rows with INFO key rows and still look cohesive.
 */
@Composable
fun TerminalScreen(
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val session = remember { TerminalSession(context, app) }
    val scope = rememberCoroutineScope()

    val groups by session.groups.collectAsState()
    val isRunning by session.isRunning.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom whenever new groups arrive.
    LaunchedEffect(groups.size) {
        if (groups.isNotEmpty()) {
            listState.animateScrollToItem(groups.size - 1)
        }
    }

    val active = isRunning

    androidx.compose.material3.Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.terminal_title),
                subtitle = stringResource(R.string.terminal_subtitle),
                tier = app.capabilityTier,
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
            // History — terminal-styled.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        TerminalGroupCard(group = group)
                    }
                }
            }

            // Input row — fixed at the bottom.
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
                    Text(
                        text = stringResource(R.string.terminal_prompt_prefix),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val toRun = input
                                input = ""
                                scope.launch { session.execute(toRun) }
                            },
                        ),
                        modifier = Modifier
                            .weight(1f),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.terminal_hint),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                    ),
                                )
                            }
                            inner()
                        },
                    )
                    IconButton(
                        onClick = { session.clear() },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = stringResource(R.string.terminal_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            val toRun = input
                            input = ""
                            scope.launch { session.execute(toRun) }
                        },
                        enabled = input.isNotBlank() && !isRunning,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = stringResource(R.string.terminal_send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One command's output rendered as a card. The accent stripe on the
 * left is the only "grouping" chrome — inside the card the lines
 * keep their own colors so an ERROR row mixed with INFO/KEY rows
 * still reads naturally.
 */
@Composable
private fun TerminalGroupCard(group: TerminalGroup) {
    val status = group.status()
    val accent = when (status) {
        GroupStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        GroupStatus.ERROR -> MaterialTheme.colorScheme.error
        GroupStatus.STREAM -> MaterialTheme.colorScheme.tertiary
        GroupStatus.IDLE -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // Left accent stripe — 3 dp wide, colored by group status.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                // Optional command chip — only show when the group has
                // an actual command (welcome banner doesn't).
                if (group.command != "welcome") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = group.command,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                group.lines.forEach { line ->
                    TerminalLineRow(line)
                }
            }
        }
    }
}

private enum class GroupStatus { SUCCESS, ERROR, STREAM, IDLE }

private fun TerminalGroup.status(): GroupStatus {
    if (lines.any { it.kind == TerminalLine.Kind.ERROR }) return GroupStatus.ERROR
    if (lines.any { it.kind == TerminalLine.Kind.STREAM }) return GroupStatus.STREAM
    if (lines.any { it.kind == TerminalLine.Kind.SUCCESS }) return GroupStatus.SUCCESS
    return GroupStatus.IDLE
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    val (color, weight) = when (line.kind) {
        TerminalLine.Kind.INPUT -> MaterialTheme.colorScheme.primary to FontWeight.SemiBold
        TerminalLine.Kind.STDOUT -> MaterialTheme.colorScheme.onSurface to FontWeight.Normal
        TerminalLine.Kind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant to FontWeight.Normal
        TerminalLine.Kind.ERROR -> MaterialTheme.colorScheme.error to FontWeight.Medium
        TerminalLine.Kind.STREAM -> MaterialTheme.colorScheme.tertiary to FontWeight.Medium
        TerminalLine.Kind.HEADER -> MaterialTheme.colorScheme.primary to FontWeight.Bold
        TerminalLine.Kind.KEY -> MaterialTheme.colorScheme.tertiary to FontWeight.SemiBold
        TerminalLine.Kind.SUCCESS -> MaterialTheme.colorScheme.primary to FontWeight.SemiBold
    }
    val prefix = when (line.kind) {
        TerminalLine.Kind.INPUT -> "$ "
        TerminalLine.Kind.STDOUT -> "  "
        TerminalLine.Kind.INFO -> "» "
        TerminalLine.Kind.ERROR -> "✗ "
        TerminalLine.Kind.STREAM -> "› "
        TerminalLine.Kind.HEADER -> "── "
        TerminalLine.Kind.KEY -> "  "
        TerminalLine.Kind.SUCCESS -> "✓ "
    }
    // Surface a small timestamp prefix on ERROR and SUCCESS lines so
    // the user can tell *when* a happy/sad event happened without
    // scanning the wall-clock. INFO/KEY/STDOUT lines skip it because
    // they fire in tight bursts and the prefix would dominate.
    val ts = when (line.kind) {
        TerminalLine.Kind.ERROR, TerminalLine.Kind.SUCCESS, TerminalLine.Kind.HEADER ->
            formatClock(line.timestampMs) + " "
        else -> ""
    }
    Text(
        text = ts + prefix + line.text,
        style = TextStyle(
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontWeight = weight,
        ),
    )
}

/** Compact HH:mm:ss for in-line timestamps. Uses the device's local
 *  timezone so the user sees the time their wrist says it is. */
private fun formatClock(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
        cal.get(java.util.Calendar.SECOND),
    )
}