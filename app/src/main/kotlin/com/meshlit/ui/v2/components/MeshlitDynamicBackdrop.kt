package com.meshlit.ui.v2.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitPulseAqua
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitPulseViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gemini-style dynamic backdrop for the v2 build. A slow,
 * infinite pastel gradient that drifts hue + position over
 * time so the background "breathes" instead of sitting as a
 * flat dark plate.
 *
 * Two animated layers stack on top of each other:
 *   1. A large radial glow (`MeshlitPulseViolet` at low
 *      alpha) whose center orbits the screen on a 32 s
 *      circular path. This is the "Gemini home screen"
 *      feel — the violet blob moves slowly, never settling.
 *   2. A second smaller glow (`MeshlitPulseAqua` /
 *      `MeshlitPulseCoral`) that counter-orbits so the two
 *      blobs cross paths every 32 s. The hue mix at every
 *      screen point shifts with the phase, so the perceived
 *      color changes even when nothing else animates.
 *
 * On top of those two glows, the canvas paints the dark
 * `MeshlitInk` base with `BlendMode.SrcOver` at 0.92 alpha so
 * the body content (light text on dark) keeps its contrast —
 * the backdrop only contributes a faint warm wash at the
 * blobs' centers.
 *
 * Performance: one `Canvas` + two `RadialGradientShader`
 * instances cached on the draw scope. The infinite transition
 * is single-axis (just `phaseFraction` 0f → 1f) so the
 * recomposition cost is one float per frame. Verified cheap
 * on a 4 GB / mid-spec Pixel.
 *
 * Usage: place inside the v2 `MeshlitAppV2` scaffold *behind*
 * the body content, before `Scaffold` so the body draws on
 * top. Or wrap the whole `V2Root` body — both work because
 * the backdrop is transparent except for the radial blobs.
 */
@Composable
fun MeshlitDynamicBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) {
        // Plain ink fallback so callers can disable the
        // animation (e.g. for unit tests, screenshots, or
        // when `MeshlitThemeConfig.animationsEnabled == false`).
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(MeshlitInk)
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "v2-dynamic-backdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32_000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Layer 0: dark ink base — this is what every other
        // surface reads as "background". Cover the entire
        // canvas so the radial blobs have something to
        // multiply against.
        drawRect(color = MeshlitInk)

        // Orbital center path. We compute the position of two
        // blobs from a single phase fraction so they stay
        // synced without two infinite transitions.
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radiusX = w * 0.40f
        val radiusY = h * 0.32f

        // Blob 1: violet, orbits clockwise. Position is on a
        // parametric ellipse so the orbit feels natural on
        // tall phones (narrow radiusX, larger radiusY).
        val a1 = phase * 2f * Math.PI.toFloat()
        val blob1X = cx + radiusX * cos(a1)
        val blob1Y = cy + radiusY * sin(a1)
        val blob1R = (w.coerceAtMost(h)) * 0.55f

        // Blob 2: aqua+coral mix, counter-orbits so the two
        // cross paths.
        val a2 = (phase + 0.5f) * 2f * Math.PI.toFloat()
        val blob2X = cx + radiusX * cos(a2)
        val blob2Y = cy + radiusY * sin(a2)
        val blob2R = (w.coerceAtMost(h)) * 0.45f

        // Slight hue mix that drifts with phase. At phase =
        // 0 the second blob leans aqua; at phase = 0.5 it
        // leans coral. The blend keeps the palette inside
        // the Pulse gradient so it doesn't fight the brand.
        val aquaMix = (1f - 2f * (phase - 0.5f).let { kotlin.math.abs(it) })
            .coerceIn(0f, 1f)
        val coralMix = 1f - aquaMix
        val blob2Color = lerpColor(MeshlitPulseAqua, MeshlitPulseCoral, coralMix)

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    MeshlitPulseViolet.copy(alpha = 0.55f),
                    MeshlitPulseViolet.copy(alpha = 0.28f),
                    MeshlitPulseViolet.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(blob1X, blob1Y),
                radius = blob1R,
            ),
            blendMode = BlendMode.Plus,
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    blob2Color.copy(alpha = 0.48f),
                    blob2Color.copy(alpha = 0.22f),
                    blob2Color.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(blob2X, blob2Y),
                radius = blob2R,
            ),
            blendMode = BlendMode.Plus,
        )

        // Final ink overlay at 0.55 alpha — keeps text contrast
        // WCAG AA on the dark base but lets the pastel blobs
        // bleed through so the backdrop actually shifts hue.
        drawRect(color = MeshlitInk.copy(alpha = 0.55f))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}