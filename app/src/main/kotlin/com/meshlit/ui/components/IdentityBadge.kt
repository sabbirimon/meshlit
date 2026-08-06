package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact pill that renders the running identity —
 *
 *   [icon] Meshlit · Llama-3-8B · Local · runanywhere · v0.2.0
 *
 * Two visual variants:
 *
 *  - [variant] = [IdentityBadgeVariant.Toolbar] — small, single-line,
 *    monochrome pill designed to live inside the Jobs compact toolbar
 *    next to [StatusPill]. Shows the short `badgeText()`.
 *  - [variant] = [IdentityBadgeVariant.Bubble] — slightly larger
 *    surface pinned above each completed reply bubble on the Jobs
 *    screen and at the top of the Agent conversation. Shows the
 *    `fullText()` form so the version + engine are visible.
 *
 * Both variants share the accent dot so the user can scan the
 * chat and see which bubble came from which model.
 */
@Composable
fun IdentityBadge(
    identity: Identity,
    modifier: Modifier = Modifier,
    variant: IdentityBadgeVariant = IdentityBadgeVariant.Toolbar,
) {
    val accent = com.meshlit.ui.theme.MeshlitAmber
    val text = when (variant) {
        IdentityBadgeVariant.Toolbar -> identity.badgeText()
        IdentityBadgeVariant.Bubble -> identity.fullText()
    }
    val verticalPadding = if (variant == IdentityBadgeVariant.Toolbar) 4.dp else 6.dp
    val horizontalPadding = if (variant == IdentityBadgeVariant.Toolbar) 8.dp else 12.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (variant == IdentityBadgeVariant.Toolbar)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (variant == IdentityBadgeVariant.Toolbar) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (variant == IdentityBadgeVariant.Bubble)
                    FontWeight.SemiBold
                else
                    FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (variant == IdentityBadgeVariant.Bubble && identity.appVersion.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "v${identity.appVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}

/** Visual preset for [IdentityBadge]. */
enum class IdentityBadgeVariant { Toolbar, Bubble }