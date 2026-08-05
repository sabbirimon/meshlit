package com.meshlit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.ui.nav.TopLevelDestination

/**
 * Compact bottom navigation bar.
 *
 * 9 destinations don't fit cleanly on a 1272dp-wide phone screen when
 * every item shows a label — labels wrap into "Devic\nes" / "Mode\nls"
 * etc. Instead, this bar uses:
 *
 *  - **Icon-only items by default**, 56dp wide each. Horizontally
 *    scrollable via [LazyRow] so the 9 destinations always fit even
 *    on narrow screens.
 *  - **Animated label pill on the selected item** — when an item is
 *    selected, the icon grows slightly and a small label pill fades
 *    in underneath. This keeps the "where am I" cue without wasting
 *    vertical space on every icon.
 *  - **Tier-aware accent color** — the selected pill matches the
 *    `CapabilityTier` accent so the bar visually rhymes with the
 *    [MeshlitHeader] tier pill on top.
 *
 * Pass the same list of [TopLevelDestination]s you'd give to
 * `NavigationBar`, plus the current route and a click handler. The
 * bar handles scroll state internally via `rememberLazyListState` in
 * [LazyRow].
 */
@Composable
fun MeshlitBottomBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String,
    accentColor: Color,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll so the currently-selected item is always visible.
    // With 9 destinations on a narrow phone, users would otherwise
    // have to scroll the bar horizontally every time they navigate to
    // a destination that's off-screen.
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            // Center the selected item so it has visual breathing room.
            val target = (selectedIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(destinations, key = { it.route }) { dest ->
                    val selected = currentRoute == dest.route
                    BottomBarItem(
                        destination = dest,
                        selected = selected,
                        accentColor = accentColor,
                        onClick = { onSelect(dest) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val animatedAccent by animateColorAsState(
        targetValue = if (selected) accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 350),
        label = "bottom-bar-bg",
    )
    val animatedFg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 350),
        label = "bottom-bar-fg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "bottom-bar-scale",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(animatedAccent)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (selected) animatedFg else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(if (selected) 22.dp else 20.dp),
            )
        }
        Text(
            text = destination.shortLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Tier pill accent. Mirror of the [MeshlitHeader] tier pill so
 * the bar feels like part of the same animated family.
 */
@Composable
fun tierAccentColor(tier: com.meshlit.core.common.CapabilityTier): Color = when (tier) {
    com.meshlit.core.common.CapabilityTier.LITE -> Color(0xFF3A8DFF)
    com.meshlit.core.common.CapabilityTier.MID -> Color(0xFFB14EFF)
    com.meshlit.core.common.CapabilityTier.FULL -> Color(0xFF00E5C7)
}

/** Convenience: the screen icon for the bar's compact layout. */
val TopLevelDestination.shortLabel: String
    get() = when (this) {
        TopLevelDestination.Devices -> "Devices"
        TopLevelDestination.Jobs -> "Jobs"
        TopLevelDestination.Agent -> "Agent"
        TopLevelDestination.Models -> "Models"
        TopLevelDestination.Advanced -> "Advanced"
        TopLevelDestination.Files -> "Files"
        TopLevelDestination.Sessions -> "Terminal"
        TopLevelDestination.Cluster -> "Cluster"
        TopLevelDestination.Network -> "Network"
        TopLevelDestination.Users -> "Users"
        TopLevelDestination.Settings -> "Settings"
        TopLevelDestination.Voice -> "Voice"
        TopLevelDestination.Structured -> "JSON"
        TopLevelDestination.Catalog -> "Catalog"
        TopLevelDestination.Vision -> "Vision"
        TopLevelDestination.Cloud -> "Cloud"
    }
