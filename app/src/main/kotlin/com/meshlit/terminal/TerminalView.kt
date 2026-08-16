package com.meshlit.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meshlit.terminal.vt.Attr
import com.meshlit.terminal.vt.Color as VtColor
import com.meshlit.terminal.vt.Cursor
import com.meshlit.terminal.vt.Row
import com.meshlit.terminal.vt.Screen
import kotlinx.coroutines.launch

/**
 * Ghostty-style terminal view backed by an [Screen] emulator.
 *
 * The emulator owns the cell grid + scrollback + cursor. We render
 * each row by collapsing consecutive cells with the same [Attr] into
 * a single `AnnotatedString` span — one `BasicText` per row, not per
 * cell. That matches ghostty's per-row optimisation.
 *
 * Diff strategy: when the screen's `dirtyRows` is empty for a tick,
 * we skip the entire render. When it's non-empty, we rebuild all
 * rows (Compose's text composition tree handles the heavy lifting).
 * For a 24×80 screen that's at most 24 small `AnnotatedString`s.
 */
@Composable
fun TerminalView(
    screen: Screen,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by screen.state.collectAsState()
    val rows = (0 until screen.rows).map { screen.activeRow(it) }
    var input by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Auto-scroll on the bottom row when new output lands and the user
    // is currently at the bottom — same pattern as the previous screen.
    LaunchedEffect(state.version) {
        if (listState.firstVisibleItemIndex >= state.rows - 2) {
            listState.animateScrollToItem(state.rows - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                userScrollEnabled = true,
            ) {
                items(rows) { row ->
                    TerminalRow(
                        row = row,
                        cursorRow = screen.cursor.row,
                        cursorCol = screen.cursor.col,
                        cursorVisible = screen.cursor.visible && screen.cursor.style != Cursor.Style.Bar,
                        attr = screen.attr,
                    )
                }
                // A spacer for the trailing input row.
                items(1) { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 4.dp)) }
            }
        }
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
                    text = "$",
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
                            onSend(toRun)
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.CleaningServices,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        val toRun = input
                        input = ""
                        onSend(toRun)
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalRow(
    row: Row,
    cursorRow: Int,
    cursorCol: Int,
    cursorVisible: Boolean,
    attr: Attr,
) {
    val builder = buildAnnotatedString {
        var i = 0
        while (i < row.cols) {
            val cell = row[i]
            val start = i
            while (i + 1 < row.cols && row[i + 1].attr == cell.attr) i++
            val text = (start..i).mapNotNull { row[it].codepoint.takeIf { cp -> cp != 0 }?.let { String(Character.toChars(it)) } }.joinToString("")
            if (text.isNotEmpty()) {
                pushStyle(SpanStyle(color = attrToColor(cell.attr)))
                append(text)
                pop()
            }
            i++
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = builder,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
            ),
        )
        // Reference the cursor coords so Compose knows to recompose when
        // the screen state bumps; the actual block cursor is rendered by
        // overdrawing the cell at (cursorRow, cursorCol) in the future.
        @Suppress("UNUSED_EXPRESSION")
        cursorRow
        @Suppress("UNUSED_EXPRESSION")
        cursorCol
        @Suppress("UNUSED_EXPRESSION")
        cursorVisible
    }
}

private fun attrToColor(attr: Attr): Color {
    val fallback = Color(0xFFE5E5E5.toInt()) // xterm white
    val rgb = VtColor.resolve(attr.fg, VtColor.STANDARD_PALETTE)
    return Color(0xFF000000.toInt() or rgb)
}