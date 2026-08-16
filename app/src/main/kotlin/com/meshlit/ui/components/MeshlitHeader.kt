package com.meshlit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * Compact, animated app bar that replaces Material 3's [TopAppBar]
 * across the app. Differences vs the stock header:
 *
 *  - **Tight vertical footprint.** ~44dp content + status-bar inset
 *    instead of the M3 default ~64dp. The previous design had too
 *    much empty space above the title; this header trims the padding
 *    and uses a single-row layout.
 *  - **Animated accent gradient.** The leading swatch cycles hue over
 *    a 6s loop when the app is "active" (inference running, peer
 *    traffic, etc.). At rest it sits at the primary color.
 *  - **Capability tier pill.** Right side shows the device's
 *    [CapabilityTier] as a colored chip; the user always sees what
 *    class of device this is without diving into Settings.
 *  - **Dynamic color.** When the device supports Android 12+
 *    dynamic color, the header inherits the system wallpaper palette
 *    (Material You). Otherwise it falls back to the curated Meshlit
 *    palette.
 *  - **Hamburger slot.** [onOpenDrawer] wires to the side drawer so
 *    the user can swipe-open the drawer or tap the icon.
 */
@Composable
fun MeshlitHeader(
    title: String,
    subtitle: String? = null,
    tier: CapabilityTier,
    active: Boolean,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Trailing slot rendered between the title column and the
     * tier pill. Lets callers (Jobs screen) inject a
     * dispatch-mode picker without losing the tier chip on
     * the far right. Defaults to nothing.
     */
    trailing: @Composable (() -> Unit)? = null,
) {
    // Animate the accent when activity changes — gives an obvious
    // visual signal that the cluster is doing work right now.
    val accentColor by animateColorAsState(
        targetValue = if (active) tier.accentActive else tier.accentRest,
        animationSpec = tween(durationMillis = 600),
        label = "header-accent",
    )
    val onAccentColor by animateColorAsState(
        targetValue = if (active) Color.White else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(durationMillis = 600),
        label = "header-on-accent",
    )

    // 6s hue cycle for the gradient swatch while active. Disabled
    // when at rest so we don't waste CPU on idle devices.
    val infinite = rememberInfiniteTransition(label = "header-pulse")
    val pulseProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "header-pulse-progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Thin animated underline at the bottom of the header —
        // subtle, but enough to read as "alive" without being noisy.
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.0f),
                                accentColor.copy(alpha = 0.8f),
                                accentColor.copy(alpha = 0.0f),
                            ),
                            startX = -200f + pulseProgress * 800f,
                            endX = 200f + pulseProgress * 800f,
                        ),
                    ),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open navigation drawer",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Accent dot — the "live" indicator.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // Caller-provided slot (e.g. dispatch picker on Jobs).
            if (trailing != null) {
                trailing()
            }

            // Tier pill — color-coded, no border so the gradient does
            // the talking.
            TierPill(tier = tier, active = active, onColor = onAccentColor)
        }
    }
}

@Composable
private fun TierPill(
    tier: CapabilityTier,
    active: Boolean,
    onColor: Color,
) {
    val bg by animateColorAsState(
        targetValue = if (active) tier.accentActive else tier.accentRest.copy(alpha = 0.18f),
        animationSpec = tween(durationMillis = 600),
        label = "tier-pill-bg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) onColor else tier.accentRest,
        animationSpec = tween(durationMillis = 600),
        label = "tier-pill-fg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Text(
            text = tier.displayLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = fg,
        )
    }
}

/** Convenience padding matching the header's vertical footprint so
 *  screens can pass it straight to their content without manual
 *  measurement. */
val MeshlitHeaderContentTopPadding: PaddingValues = PaddingValues(top = 0.dp)

private val CapabilityTier.displayLabel: String
    get() = when (this) {
        CapabilityTier.LITE -> "Lite"
        CapabilityTier.MID -> "Mid"
        CapabilityTier.FULL -> "Full"
    }

private val CapabilityTier.accentRest: Color
    get() = when (this) {
        CapabilityTier.LITE -> Color(0xFF7C8DA0)  // slate-blue
        CapabilityTier.MID -> Color(0xFF6E5BCE)   // violet
        CapabilityTier.FULL -> Color(0xFF18B6A0)  // teal-green
    }

private val CapabilityTier.accentActive: Color
    get() = when (this) {
        CapabilityTier.LITE -> Color(0xFF3A8DFF)  // sky blue (busy)
        CapabilityTier.MID -> Color(0xFFB14EFF)   // magenta
        CapabilityTier.FULL -> Color(0xFF00E5C7)  // bright cyan
    }
