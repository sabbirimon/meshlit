package com.meshlit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitAmber
import com.meshlit.ui.theme.RaOrange
import com.meshlit.ui.theme.RaOutline
import com.meshlit.ui.theme.RaSurface
import com.meshlit.ui.theme.RaSurfaceVariant
import com.meshlit.ui.theme.RaTextPrimary
import com.meshlit.ui.theme.RaTextSecondary

/**
 * RunAnywhere-style list card. Pure layout shell — the trailing-slot
 * state machine (Loaded / Use / Get / Set-token / Cancel-progress)
 * lives in `ModelTrailingAction.kt` rather than baked in here, so
 * the same shell renders cleanly on a Settings category row (no
 * model state) and on a Models catalog row (full state machine).
 *
 * Visual contract (mirrors upstream `ModelRow.kt`):
 *  - root: `Surface` with `RoundedCornerShape(16.dp)`
 *  - background: `RaSurface`
 *  - border:
 *      - default → 1dp `RaOutline`
 *      - `highlightLabel != null` → 1dp `RaOrange.copy(alpha = 0.5f)`
 *        and `RaOrange.copy(alpha = 0.08f)` background (the "Top pick"
 *        highlight in the screenshot)
 *  - layout: `Row { leadingSquare | titleColumn(weight=1) | trailing }`
 *    with 16dp horizontal / 12dp vertical padding
 *  - leading: 40dp `RaSurfaceVariant` rounded square with the icon
 *    tinted `RaOrange` at 24dp
 *  - title: `titleMedium` SemiBold single-line ellipsis
 *  - subtitle: `bodySmall` `RaTextSecondary`
 *  - chips: `FlowRow` of caller-provided `RaPillChip`s (Smart /
 *    Thinks / Fast row in the screenshot)
 *  - trailing slot: caller-provided; accepts `RaPillChip` ("Loaded" /
 *    "Use"), `RaGetButton`, or a busy/cancel control from
 *    `ModelTrailingAction`
 *
 * On tap, `onClick?.invoke()`. When `onClick` is null the card is
 * inert (used by the agent "model chip" header card which is just
 * display-only).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaListCard(
    leadingIcon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: @Composable (() -> Unit)? = null,
    chips: @Composable (() -> Unit)? = null,
    highlightLabel: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isHighlighted = highlightLabel != null
    val containerColor = if (isHighlighted) {
        RaOrange.copy(alpha = 0.08f)
    } else {
        RaSurface
    }
    val borderColor = if (isHighlighted) {
        RaOrange.copy(alpha = 0.5f)
    } else {
        RaOutline
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))

    val surfaceContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Leading orange-tinted icon square.
            Surface(
                color = RaSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Row(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = RaOrange,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Title + optional subtitle + optional metadata + optional
            // chips. The `FlowRow` of chips wraps when the trailing
            // slot is wide.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (highlightLabel != null) {
                    RaPillChip(
                        text = highlightLabel,
                        tone = RaPillTone.TOP_PICK,
                        icon = Icons.Filled.Bolt,
                    )
                }
                Text(
                    text = title,
                    color = RaTextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = RaTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (metadata != null) {
                    Spacer(Modifier.height(2.dp))
                    metadata()
                }
                if (chips != null) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        chips()
                    }
                }
            }

            // Trailing slot + optional chevron.
            if (trailing != null || showChevron) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (trailing != null) {
                        trailing()
                    }
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = RaTextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    } else {
        Surface(
            modifier = rowModifier,
            color = containerColor,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, borderColor),
            content = surfaceContent,
        )
    }
}

/**
 * Convenience overload that renders an info row with a chevron trailing
 * glyph (no `chips`, no `metadata`). Used by the Settings hub category
 * cards and the "About" rows that have no model state.
 */
@Composable
fun RaNavRow(
    leadingIcon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    RaListCard(
        leadingIcon = leadingIcon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        showChevron = true,
        modifier = modifier,
    )
}
