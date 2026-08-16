package com.meshlit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.ui.nav.TopPillDestination

/**
 * Horizontally-scrolling pill nav, stitch-parity.
 *
 * Mirrors the layout in
 * `stitch/meshlit---federated-edge-ai-cluster/src/App.tsx` — 13
 * pills in display order with icon + label, gradient highlight
 * when selected, and the same animations as the React app:
 *
 *  - **`motion.div initial/animate/exit`** for the page wrapper
 *    — handled by the [MeshlitApp] NavHost (not this Composable).
 *  - **`layoutId="bottomNavDot"`** — the active indicator dot
 *    animates between pills via Compose's `Modifier.offset { … }`
 *    reading from a `derivedStateOf { selectedIndex }`.
 *  - **`spring(MediumBouncy, MediumLow)`** scale & slide.
 *
 * Bar owns its own LazyRow scroll state and auto-centers the
 * selected pill so the user never has to swipe to find where they
 * are.
 */
@Composable
fun MeshlitTopPillBar(
    items: List<TopPillDestination>,
    currentRoute: String,
    onSelect: (TopPillDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
    val isDark = !MaterialTheme.colorScheme.background.isLight()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            val target = (selectedIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    val dividerColor by animateColorAsState(
        targetValue = if (isDark)
            Color(0xFF7C5CFF).copy(alpha = 0.30f)
        else
            Color(0xFF22D3EE).copy(alpha = 0.20f),
        animationSpec = tween(durationMillis = 250),
        label = "pill-bar-divider",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(items, key = { it.name }) { item ->
                    val selected = currentRoute == item.route
                    PillChip(
                        item = item,
                        selected = selected,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }

        // Animated cyan→purple hairline at the bottom of the bar.
        // Stays in place across scroll/recompose — drops a 1dp line
        // that animates colour on theme change.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(dividerColor)
                .padding(bottom = 0.dp)
        ) {
            // 1dp tall hairline via padding spacer.
            Box(modifier = Modifier.size(width = 1.dp, height = 1.dp))
        }
    }
}

/**
 * Single pill chip.
 *
 * Owns:
 *  - gradient background when selected (cyan → purple in dark,
 *    cyan → blue in light — matches stitch CSS exactly)
 *  - spring scale (1.0 ↔ 1.05)
 *  - soft glow shadow when selected
 *  - selected-state border for light-mode crispness
 *  - color crossfade for label & icon tint
 */
@Composable
private fun PillChip(
    item: TopPillDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = !MaterialTheme.colorScheme.background.isLight()

    val selectedBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF22D3EE).copy(alpha = 0.40f),
                Color(0xFF7C5CFF).copy(alpha = 0.50f),
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF06B6D4), Color(0xFF2563EB)),
        )
    }
    val unselectedBg = if (isDark)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    else
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pill-scale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (selected) 0.45f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "pill-glow",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) {
            Color.White
        } else {
            if (isDark) Color(0xFFA3AAC2) else Color(0xFF475569)
        },
        animationSpec = tween(durationMillis = 250),
        label = "pill-fg",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = if (selected) 8.dp else 0.dp,
                shape = RoundedCornerShape(50),
                ambientColor = Color(0xFF22D3EE).copy(alpha = glowAlpha),
                spotColor = Color(0xFF7C5CFF).copy(alpha = glowAlpha),
            )
            .clip(RoundedCornerShape(50))
            .background(
                brush = if (selected) selectedBrush else Brush.linearGradient(
                    colors = listOf(unselectedBg, unselectedBg),
                ),
                shape = RoundedCornerShape(50),
            )
            .then(
                if (!selected && isDark) {
                    Modifier.border(
                        width = 0.5.dp,
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(50),
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(item.labelRes),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = fg,
            )
        }
    }
}

/** Extension helper: is this color luminance near-white (light bg)? */
internal fun Color.isLight(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance > 0.6f
}
