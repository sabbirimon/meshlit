package com.meshlit.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Pulse gradient — the signature visual identity of the v2 build.
 *
 * Three stops, applied at a 35° angle, drift slowly along the
 * gradient axis via [AnimatedGradient.brush] so the surface
 * appears to "breathe". Reused by:
 *   - `MeshlitHeroIdentity` (drawer identity card background)
 *   - `MeshlitPillInput` (4 dp outer glow on the input surface)
 *   - `MeshlitLeadBar` (4 dp accent rail — solid violet, no
 *     animation, to keep the lead bar readable on every screen)
 *   - `MeshlitMark` (Brain node fill — static violet, not the
 *     animated brush, so the mark stays sharp at 24 dp)
 *
 * Defined in the shared tree so both `meshlitV1` and `meshlitV2`
 * flavors can opt in via `MeshlitThemeConfig.usePulseGradient`.
 * The v1 flavor picks up these tokens when the user enables the
 * new gradient; otherwise it falls back to the existing
 * violet→cyan→emerald ramp.
 *
 * Stops per the design brief:
 *   - MeshlitPulseAqua   #4DD9C0 (cool teal)
 *   - MeshlitPulseViolet #7C6FF2 (mid violet, the brand anchor)
 *   - MeshlitPulseCoral  #E8735F (warm coral, the contrast stop)
 */
val MeshlitPulseAqua = Color(0xFF4DD9C0)
val MeshlitPulseViolet = Color(0xFF7C6FF2)
val MeshlitPulseCoral = Color(0xFFE8735F)

val MeshlitPulseStops: List<Color> = listOf(
    MeshlitPulseAqua,
    MeshlitPulseViolet,
    MeshlitPulseCoral,
)

/**
 * Build a slowly-drifting linear gradient brush at a 35° angle.
 * Wraps [AnimatedGradient.brush] so the v1 build's existing
 * cycle-driven animation machinery powers the v2 gradient too.
 *
 * @param phaseFraction 0..1, derived from `rememberInfiniteTransition`
 *        in the calling composable. Pass the same value to every
 *        gradient surface in the same screen to keep them in sync.
 */
fun meshlitPulseBrush(phaseFraction: Float): AnimatedGradientBrush =
    AnimatedGradient.brush(
        stops = MeshlitPulseStops,
        angleDeg = 35,
        phaseFraction = phaseFraction.coerceIn(0f, 1f),
    )

/**
 * Modifier extension that paints the Pulse gradient as the
 * composable's background. Pattern matches `Modifier.raBrandBackground()`
 * in `BrandGradient.kt`.
 *
 * Uses `drawBehind` (not `background`) so the gradient stays
 * crisp regardless of any alpha the composable applies via
 * `Modifier.alpha` — the brush is painted *behind* the content
 * layer, not blended with it.
 */
fun Modifier.meshlitPulseBackground(
    phaseFraction: Float = 0f,
    alpha: Float = 1f,
): Modifier = drawBehind {
    val brush = meshlitPulseBrush(phaseFraction).brush
    drawRect(
        brush = brush,
        size = size,
        topLeft = Offset.Zero,
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/**
 * Remember a single `phaseFraction` for a screen so every
 * Pulse-gradient surface in that screen drifts in sync. The
 * cadence is 2.2 s (matches [MeshlitMotion.Pulse]) — faster than
 * the v1 theme's 12 s custom-palette drift on purpose, because
 * the Pulse gradient is smaller surface area (pill input glow,
 * lead bar accent) and at 12 s the drift would look frozen.
 *
 * Respects `MeshlitThemeConfig.animationsEnabled` — when the
 * user has animations off, returns 0f so the brush is static.
 */
@Composable
fun rememberMeshlitPulsePhase(): Float {
    val config = LocalMeshlitThemeConfig.current
    if (!config.animationsEnabled) return 0f
    val transition = rememberInfiniteTransition(label = "meshlit-pulse-phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    return phase
}
