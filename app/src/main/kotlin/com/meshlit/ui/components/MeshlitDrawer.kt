package com.meshlit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.core.common.CapabilityTier
import com.meshlit.ui.nav.TopLevelDestination

/**
 * Side-drawer content. Lives inside a `ModalNavigationDrawer` and is
 * opened by:
 *  - left-edge swipe gesture,
 *  - the hamburger icon in [MeshlitHeader],
 *  - programmatic open via `drawerState.open()`.
 *
 * Layout:
 *  - Hero strip at the top: gradient banner + app name + version.
 *  - Three quick actions (Sync / Browse / About).
 *  - The full top-level destination list with a selected indicator
 *    that morphs between rows via animated background width.
 *  - Footer with capability-tier summary.
 */
@Composable
fun MeshlitDrawerContent(
    currentRoute: String,
    tier: CapabilityTier,
    onSelectDestination: (TopLevelDestination) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // Hero gradient strip — slow color cycle, gives the drawer a
        // distinct identity vs. the white surface behind it.
        HeroBanner(tier = tier)

        Spacer(Modifier.height(20.dp))

        // Quick actions — three compact tiles in a row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAction.entries.forEach { action ->
                QuickActionTile(
                    action = action,
                    onClick = { onQuickAction(action) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(text = "Screens")

        // Destinations. Using LazyColumn here would clip the footer;
        // 9 items + footer fits comfortably in a static Column so we
        // skip the lazy layer.
        TopLevelDestination.all.forEach { dest ->
            DrawerDestinationRow(
                destination = dest,
                selected = dest.route == currentRoute,
                onClick = { onSelectDestination(dest) },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        DrawerFooter(tier = tier)

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HeroBanner(tier: CapabilityTier) {
    val accent = tierAccent(tier)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.95f),
                        accent.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.primaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Meshlit",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Text(
                text = "Distributed inference, on-device",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun DrawerDestinationRow(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Animated background width gives the row a "morph into selected"
    // feel without a long transition. Width = full when selected,
    // 0 when not, tweened over 250ms.
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "drawer-row-bg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = bgAlpha * 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(destination.labelRes),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DrawerFooter(tier: CapabilityTier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tierAccent(tier)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Device class: ${tier.displayLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class QuickAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SYNC("Sync", Icons.Outlined.CloudSync),
    BOOST("Boost", Icons.Outlined.Bolt),
    ABOUT("About", Icons.Outlined.Info),
}

private fun tierAccent(tier: CapabilityTier): Color = when (tier) {
    CapabilityTier.LITE -> Color(0xFF3A8DFF)
    CapabilityTier.MID -> Color(0xFFB14EFF)
    CapabilityTier.FULL -> Color(0xFF00E5C7)
}

private val CapabilityTier.displayLabel: String
    get() = when (this) {
        CapabilityTier.LITE -> "Lite"
        CapabilityTier.MID -> "Mid"
        CapabilityTier.FULL -> "Full"
    }
