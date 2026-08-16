package com.meshlit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * A small action row rendered under an LLM response: copy-to-clipboard
 * and a regenerate button. Stub implementation — the full Stitch-
 * themed version was on the MeshlitV2 design system which never
 * landed, so this is a plain Material 3 fallback that compiles and
 * functions correctly.
 */
@Composable
fun LlmOutputActions(
    text: String,
    onRegenerate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = {
            clipboard.setText(AnnotatedString(text))
        }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onRegenerate != null) {
            IconButton(onClick = onRegenerate) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Regenerate",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}