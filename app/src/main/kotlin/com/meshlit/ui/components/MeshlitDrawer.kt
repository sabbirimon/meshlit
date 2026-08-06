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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * Layout (v2 — tile grid, matches the user-provided screenshot):
 *  - Hero strip at the top: gradient banner + app name + tagline.
 *  - Quick actions row — three compact tiles (Sync / Boost / About).
 *  - **Tiles** grid for every destination: 3 columns × N rows.
 *    Each tile is icon-on-top + label-below, with the selected
 *    tile highlighted via primaryContainer. Replaces the v1 list-
 *    of-rows layout (which didn't scale past 5 items and didn't
 *    match the RunAnywhere-style aesthetic).
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

        // 3-column tile grid. LazyVerticalGrid is required here
        // because 15 destinations overflow the static Column budget
        // on short screens — the footer would be pushed off-screen.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TopLevelDestination.all, key = { it.route }) { dest ->
                DrawerDestinationTile(
                    destination = dest,
                    selected = dest.route == currentRoute,
                    onClick = { onSelectDestination(dest) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
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

/**
 * One destination tile — square aspect ratio, icon centered above
 * the label, primaryContainer background when selected. Aspect
 * ratio keeps the grid square on phones of any width while
 * letting the label breathe underneath the icon.
 */
@Composable
private fun DrawerDestinationTile(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "drawer-tile-bg",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f + 0.5f * (1f - bgAlpha),
                    )
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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
