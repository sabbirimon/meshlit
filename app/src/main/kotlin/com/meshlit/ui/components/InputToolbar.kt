package com.meshlit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber
import com.meshlit.ui.theme.RaOutline
import com.meshlit.ui.theme.RaSurfaceVariant

/**
 * RunAnywhere-style input toolbar — the bottom row above the text
 * input that hosts the round icon buttons: menu / cloud-tools /
 * mic / think / send. Mirrors upstream `ChatInputBar` (menu,
 * Cloud, Microphone, Brain, Send).
 *
 * Visual contract:
 *  - row: `RaSurfaceVariant` `RoundedCornerShape(20.dp)` surface,
 *    `fillMaxWidth()`, padded 8dp horizontal / 6dp vertical.
 *  - buttons: round (40dp) `IconButton` with
 *    `surfaceContainerHigh` container color, `onSurfaceVariant`
 *    content. The send button is brand-amber.
 *  - leading slot: round 40dp container with the menu glyph.
 *  - tools slot (Cloud): round 40dp container. When tools are
 *    enabled the container tints to `MeshlitAmber` at 15% alpha.
 *  - mic slot: round 40dp container with `Icons.Filled.Mic`.
 *  - think slot: round 40dp container with `Icons.Filled.Bolt`.
 *    Disabled when thinking isn't supported.
 *  - send slot (Send): round 40dp brand-amber container with
 *    `Icons.AutoMirrored.Filled.Send` (or `Icons.Filled.Stop`
 *    while the agent is generating).
 *
 * The `onSend` callback is required; `onStop` is invoked when the
 * agent is currently generating and the user taps the same slot.
 */
@Composable
fun InputToolbar(
    onOpenMenu: () -> Unit,
    onToggleTools: () -> Unit,
    onOpenMic: () -> Unit,
    onToggleThinking: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    toolsEnabled: Boolean = false,
    thinkingEnabled: Boolean = false,
    thinkingSupported: Boolean = true,
    isGenerating: Boolean = false,
    canSend: Boolean = false,
) {
    val dimens = InputToolbarDimens

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = RaSurfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolbarIcon(
                icon = Icons.Filled.Menu,
                contentDescription = "Open menu",
                onClick = onOpenMenu,
                size = dimens.iconButtonSize,
            )

            ToolbarIcon(
                icon = Icons.Filled.Cloud,
                contentDescription = if (toolsEnabled) {
                    "Disable web and tools"
                } else {
                    "Enable web and tools"
                },
                onClick = onToggleTools,
                size = dimens.iconButtonSize,
                container = if (toolsEnabled) {
                    MeshlitAmber.copy(alpha = 0.15f)
                } else {
                    null
                },
                content = if (toolsEnabled) MeshlitAmber else null,
            )

            ToolbarIcon(
                icon = Icons.Filled.Mic,
                contentDescription = "Voice input",
                onClick = onOpenMic,
                size = dimens.iconButtonSize,
            )

            ToolbarIcon(
                icon = Icons.Filled.Bolt,
                contentDescription = when {
                    !thinkingSupported -> "Thinking not supported by current model"
                    thinkingEnabled -> "Disable thinking"
                    else -> "Enable thinking"
                },
                onClick = onToggleThinking,
                enabled = thinkingSupported,
                size = dimens.iconButtonSize,
                container = if (thinkingEnabled) {
                    MeshlitAmber.copy(alpha = 0.15f)
                } else {
                    null
                },
                content = if (thinkingEnabled) MeshlitAmber else null,
            )

            ToolbarIcon(
                icon = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isGenerating) {
                    "Stop generation"
                } else {
                    "Send message"
                },
                onClick = {
                    if (isGenerating) onStop() else onSend()
                },
                enabled = isGenerating || canSend,
                size = dimens.iconButtonSize,
                container = MeshlitAmber,
                content = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: androidx.compose.ui.graphics.Color? = null,
    content: androidx.compose.ui.graphics.Color? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = container ?: RaSurfaceVariant,
            contentColor = content ?: MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = container ?: RaSurfaceVariant,
            disabledContentColor = (content ?: MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = 0.4f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

private object InputToolbarDimens {
    val iconButtonSize: Dp = 44.dp
    // Surface padding is 8dp horizontal / 6dp vertical.
    val outlineColor = RaOutline
}