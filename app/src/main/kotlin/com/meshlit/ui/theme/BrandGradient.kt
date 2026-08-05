package com.meshlit.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RunAnywhere-style brand gradient + small helper composers.
 *
 * The palette is exported from `Color.kt` as [RaGradientStart],
 * [RaGradientMid], [RaGradientEnd] so other modules can build
 * their own brushes (e.g. `Brush.linearGradient(listOf(
 * RaGradientStart, RaGradientEnd))`) without depending on this
 * file. The helpers here cover the common cases:
 *
 *  - [raBrandGradient] — 45° orange → gold linear gradient
 *  - [raBrandGradientHorizontal] — left-to-right sweep
 *  - [raBrandGradientVertical] — top-down sweep
 *  - [raBrandGradientBrush] — raw `Brush` for callers who want
 *    to compose it into a `Modifier.background(brush = …)` or a
 *    `TextStyle` foreground.
 *  - [RaBrandStrip] — a fixed-height `Box` painted with the
 *    gradient; the canonical hero strip used by the Agent
 *    Terminal banner and Cloud Hub header.
 */

private val BrandStops = listOf(
    RaGradientStart,
    RaGradientMid,
    RaGradientEnd,
)

/**
 * Raw brand gradient as a [Brush]. Default direction is
 * top-left → bottom-right (45°). Pass parameters through
 * `Brush.linearGradient` if you need a different angle.
 */
fun raBrandGradientBrush(
    start: Color = RaGradientStart,
    end: Color = RaGradientEnd,
    mid: Color? = RaGradientMid,
): Brush = if (mid == null) {
    Brush.linearGradient(
        colors = listOf(start, end),
    )
} else {
    Brush.linearGradient(
        colors = listOf(start, mid, end),
    )
}

fun raBrandGradientHorizontal(
    start: Color = RaGradientStart,
    end: Color = RaGradientEnd,
): Brush = Brush.horizontalGradient(
    colors = listOf(start, end),
)

fun raBrandGradientVertical(
    start: Color = RaGradientStart,
    end: Color = RaGradientEnd,
): Brush = Brush.verticalGradient(
    colors = listOf(start, end),
)

/**
 * Background brush modifier — apply on a `Box` to paint the
 * gradient at the canonical 45° angle.
 */
fun Modifier.raBrandBackground(): Modifier = this.background(
    brush = raBrandGradientBrush(),
)

/**
 * Strip composable. Renders the brand gradient at a fixed
 * height with `fillMaxWidth()`. Use as the visual accent for
 * banner rows, the agent-terminal header, and the provider
 * detail header.
 */
@Composable
fun RaBrandStrip(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    horizontal: Boolean = false,
) {
    val brush = if (horizontal) {
        raBrandGradientHorizontal()
    } else {
        raBrandGradientBrush()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}

