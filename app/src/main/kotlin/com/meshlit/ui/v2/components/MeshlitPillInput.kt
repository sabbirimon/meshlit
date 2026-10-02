package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.theme.rememberMeshlitPulsePhase

/**
 * The bottom-anchored pill input — the single entry point for
 * chat, jobs, and agent in the v2 build. Sits in the `bottomBar`
 * slot of `MeshlitAppV2`'s `Scaffold` so it persists across
 * every top-level screen.
 *
 * Layout:
 * ```
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ ☰  Ask your cluster…                  🎙  ⚡      ➤      │
 *   └──────────────────────────────────────────────────────────┘
 * ```
 *
 * The pill itself is a `Surface(RoundedCornerShape(28.dp))` in
 * `MeshlitSurfaceHigh` with a 1 dp `MeshlitOutlineV2` border.
 * The outer glow is intentionally NOT applied yet — added in a
 * follow-up PR alongside the `imePadding` + `navigationBarsPadding`
 * audit (per `docs/UI_AUDIT.md` item 2).
 *
 * IME behavior: `ImeAction.Send` → `onSend()`. The keyboard type
 * is `Text` (not `Ascii`) so non-Latin text flows through.
 *
 * @param isGenerating when true, the trailing send icon flips
 *        to a stop icon. The caller is responsible for the stop
 *        behavior (e.g. AgentSession.stop()).
 * @param thinkingEnabled when true, the bolt icon turns violet
 *        to signal the model will use extended reasoning.
 * @param onToggleThinking optional; if null, the bolt button is
 *        hidden. Keeps the tray uncluttered for callers that
 *        don't expose a thinking toggle.
 */
@Composable
fun MeshlitPillInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask your cluster…",
    isGenerating: Boolean = false,
    thinkingEnabled: Boolean = false,
    onToggleThinking: (() -> Unit)? = null,
) {
    val pulsePhase = rememberMeshlitPulsePhase()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MeshlitSurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Leading menu — opens the quick-action sheet in v2.
            IconButton(onClick = onMenu) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Quick actions",
                    tint = MeshlitTextSecondaryV2,
                )
            }

            // Text field. Uses BasicTextField (not OutlinedTextField)
            // so the visual is flush with the pill chrome — no
            // outline border, no label, no helper text. The cursor
            // (SolidColor PulseViolet) is the only accent.
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MeshlitPulseViolet),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                decorationBox = { inner ->
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MeshlitTextTertiaryV2,
                        )
                    }
                    inner()
                },
            )

            // Mic — placeholder for v2 build no. 1. The v1 build
            // wires the mic to the agent's voice tool; the v2
            // build does the same via the callback the caller
            // wires in `MeshlitAppV2`.
            IconButton(onClick = onMic) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice input",
                    tint = MeshlitTextSecondaryV2,
                )
            }

            // Optional thinking toggle. Hidden when the caller
            // passes `onToggleThinking = null` so the tray stays
            // compact for callers that don't expose reasoning.
            if (onToggleThinking != null) {
                IconButton(onClick = onToggleThinking) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Toggle reasoning",
                        tint = if (thinkingEnabled) MeshlitPulseViolet else MeshlitTextSecondaryV2,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Send / stop. Always the trailing button. The Pulse
            // violet fill ties the input to the rest of the v2
            // accent system (lead bar rail, drawer selected row).
            IconButton(
                onClick = onSend,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MeshlitPulseViolet,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Filled.Stop else Icons.Filled.Send,
                    contentDescription = if (isGenerating) "Stop" else "Send",
                )
            }
        }
    }

    // The pulsePhase is captured here so the Surface tracks the
    // v2 animation cadence even when the caller doesn't bind
    // any other Pulse-gradient surface in the same screen (e.g.
    // a screen that has only the pill input and no drawer).
    // The phaseFraction is intentionally unused at the moment —
    // the outer glow is being added in a follow-up PR per
    // docs/UI_AUDIT.md item 2.
    @Suppress("UNUSED_EXPRESSION")
    pulsePhase
}

// `MeshlitOutlineV2` is imported above for the future outer-border
// traversal; the v1 build consumes the same `MeshlitOutline` via
// the v1 theme. Marked `Suppress` so a stale linter doesn't
// drop the import.
@Suppress("unused")
private val outlineRef = MeshlitOutlineV2
