package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.RaOutline
import com.meshlit.ui.theme.RaSurfaceVariant
import com.meshlit.ui.theme.RaTextPrimary

/**
 * RunAnywhere-style suggestion pill. The "Plan my day / Rewrite
 * clearly / Compare options" chips below the agent hero. A
 * pill-rounded outline surface with subtle fill, dark text.
 *
 * Visual contract:
 *  - shape: `RoundedCornerShape(20.dp)` — pill radius.
 *  - background: `RaSurfaceVariant` (the dark-mode card fill).
 *  - border: 1dp `RaOutline` so the chip reads against any
 *    surface (the screenshot places chips directly on the
 *    background, so we add an outline).
 *  - padding: 16dp horizontal / 10dp vertical.
 *  - text: `bodyMedium` `RaTextPrimary`, single-line ellipsis.
 *
 * `onClick` is required — these chips drive actions, never act as
 * plain labels. For non-actionable labels use a plain `Text`.
 */
@Composable
fun SuggestionChipPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(RaSurfaceVariant),
        color = RaSurfaceVariant,
        contentColor = RaTextPrimary,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RaOutline),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RaTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}