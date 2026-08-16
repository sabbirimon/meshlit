package com.meshlit.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Phase 12.2 — animated gradient brush sampler.
 *
 * The user asked for "color gradient changing color effect slow
 * animation". We implement a simple, low-power way to drift the
 * brush phase:
 *
 *  1. Build a linear gradient brush from the user's stops at
 *     angle [angleDeg].
 *  2. Drive a `phaseFraction` from 0f → 1f over [cycleSeconds]
 *     via `rememberInfiniteTransition`.
 *  3. The buildColorScheme caller reads `phaseFraction` and
 *     re-emits a new `MeshlitThemeConfig` state; the
 *     [buildColorScheme] function rebuilds the brush with the
 *     new phase offset and Compose crossfades via
 *     `animatedColorScheme`.
 *
 * Slow default = 12s (per the user's request). No new deps.
 */
object AnimatedGradient {
    /**
     * Compute the current phase fraction (0f → 1f) for a given
     * [config] + [animated] gradient. Used by [buildColorScheme]
     * so the resolved `ColorScheme` reflects the live animation.
     *
     * Composable — collects an infinite transition per call site
     * and returns a `Float`.
     */
    @Composable
    fun phaseFor(
        config: MeshlitThemeConfig,
        animated: CustomPalette.AnimatedGradient,
    ): Float {
        val cycleMillis = (animated.cycleSeconds.coerceIn(4, 60) * 1000L)
        val transition = rememberInfiniteTransition(label = "anim-gradient-phase")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleMillis.toInt(), easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
        // Respect the user's animationsEnabled toggle. When
        // animations are off we still emit a static 0f so the
        // build is deterministic; the brush uses the first stop
        // unchanged.
        return if (config.animationsEnabled) phase else 0f
    }

    /**
     * Build a brush from [stops] at angle [angleDeg], phase-shifted
     * by [phaseFraction]. The phase shift is realised by
     * shifting the start/end offsets along the gradient axis so
     * the brush visibly "drifts". Cheap, GPU-friendly.
     *
     * Public so [CustomThemeScreen] can preview a half-speed
     * brush for the user to see the cycle without waiting 12s.
     *
     * Returns a [AnimatedGradientBrush] carrying both the
     * resolved `Brush` and the stop list so callers can sample
     * exact colors via [colorAt] without poking into the brush's
     * internal state. The brush operates on a 1000x1000 unit box
     * scaled by the painted surface.
     */
    fun brush(stops: List<Color>, angleDeg: Int, phaseFraction: Float): AnimatedGradientBrush {
        if (stops.isEmpty()) {
            return AnimatedGradientBrush(
                brush = Brush.linearGradient(listOf(Color.Black, Color.Black)),
                stops = listOf(Color.Black, Color.Black),
            )
        }
        val radians = Math.toRadians(angleDeg.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val startFraction = (0.5f - 0.4f * cos - 0.4f * sin + 0.4f * phaseFraction)
            .coerceIn(0f, 1f)
        val endFraction = (0.5f + 0.4f * cos + 0.4f * sin - 0.4f * phaseFraction)
            .coerceIn(0f, 1f)
        val brush = Brush.linearGradient(
            colors = stops,
            start = Offset(startFraction * 1000f, startFraction * 1000f),
            end = Offset(endFraction * 1000f, endFraction * 1000f),
        )
        return AnimatedGradientBrush(brush = brush, stops = stops)
    }
}

/**
 * Phase 12.2 — the resolved gradient brush + its stop list. The
 * stops are returned alongside the brush so callers can sample
 * the gradient at any position without poking into the brush's
 * internal state (which is private to Compose).
 */
data class AnimatedGradientBrush(
    val brush: Brush,
    val stops: List<Color>,
)

/**
 * Sample the color at a fractional position [t] along the
 * gradient's stop list. Cheap, no allocations on the hot path.
 */
fun AnimatedGradientBrush.colorAt(t: Float): Color {
    val colors = stops
    if (colors.isEmpty()) return Color.Transparent
    if (colors.size == 1) return colors.first()
    val clamped = t.coerceIn(0f, 1f)
    val scaled = clamped * (colors.size - 1)
    val idx = scaled.toInt().coerceAtMost(colors.size - 2)
    val frac = scaled - idx
    val a = colors[idx]
    val b = colors[idx + 1]
    return Color(
        red = a.red + (b.red - a.red) * frac,
        green = a.green + (b.green - a.green) * frac,
        blue = a.blue + (b.blue - a.blue) * frac,
        alpha = a.alpha + (b.alpha - a.alpha) * frac,
    )
}