package com.meshlit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitPulseAqua
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.rememberMeshlitPulsePhase

/**
 * The "three-ring mark" — the Meshlit brand glyph.
 *
 * Geometry mirrors the adaptive launcher icon at
 * `drawable/ic_launcher_foreground.xml`:
 *   - Central Brain node (violet, radial gradient fill)
 *   - Three satellite nodes (top = aqua, bottom-left = coral,
 *     bottom-right = violet)
 *   - Light beams connecting the Brain to each satellite
 *   - Faint orbital ring around the whole assembly
 *
 * Drawn on a `Canvas` rather than via `Image(painter = ...)` so:
 *   - The Pulse gradient can flow through the Brain node when
 *     the caller wants the live breathing effect.
 *   - The orbital ring can pulse on `MeshlitMotion.Pulse` at
 *     any composable size.
 *   - There's no drawable XML coupling — the v2 build can
 *     re-theme the satellite colors without touching res/.
 *
 * Static usage (no animation):
 *   `MeshlitMark(size = 64.dp)` — Brain node is solid violet,
 *   orbital ring is static at 30% alpha. Cheap to recompose.
 *
 * Animated usage:
 *   `MeshlitMark(size = 88.dp, pulseFraction = phase)` — the
 *   orbital ring's alpha pulses at `phase` (0..1). For
 *   continuous animation, pass `pulseFraction = rememberMeshlitPulsePhase()`
 *   which keeps the cadence in sync with
 *   `MeshlitPulseGradient` and `MeshlitMotion.Pulse`.
 */
@Composable
fun MeshlitMark(
    size: Dp = 64.dp,
    pulseFraction: Float? = null,
    modifier: Modifier = Modifier,
    brainColor: Color = MeshlitPulseViolet,
    satelliteTop: Color = MeshlitPulseAqua,
    satelliteLeft: Color = MeshlitPulseCoral,
    satelliteRight: Color = MeshlitPulseViolet,
    ringColor: Color = MeshlitPulseViolet,
) {
    val resolvedPulse = pulseFraction ?: rememberMeshlitPulsePhase()
    Canvas(modifier = modifier.size(size)) {
        drawMeshlitMark(
            brainColor = brainColor,
            satelliteTop = satelliteTop,
            satelliteLeft = satelliteLeft,
            satelliteRight = satelliteRight,
            ringColor = ringColor,
            pulseAlpha = 0.18f + 0.22f * resolvedPulse, // 0.18 → 0.40
        )
    }
}

/**
 * Internal draw routine — extracted so future `Modifier.drawWithCache`
 * call sites can skip the Canvas allocation. Keep the math identical
 * to the drawable XML for visual parity (108 dp = 1.0 in normalized
 * units; here we map `size.minDimension` → 108).
 */
private fun DrawScope.drawMeshlitMark(
    brainColor: Color,
    satelliteTop: Color,
    satelliteLeft: Color,
    satelliteRight: Color,
    ringColor: Color,
    pulseAlpha: Float,
) {
    val side = size.minDimension
    val center = Offset(size.width / 2f, size.height / 2f)
    // Normalize to a 108×108 design grid, then scale to the
    // requested Dp size. This keeps the geometry ratios identical
    // to the adaptive launcher icon at every size.
    val unit = side / 108f

    // --- orbital ring ----------------------------------------------------
    drawCircle(
        color = ringColor.copy(alpha = pulseAlpha.coerceIn(0f, 1f)),
        radius = 40f * unit,
        center = center,
        style = Stroke(width = 1.5f * unit),
    )

    // --- light beams (Brain → satellites) --------------------------------
    // Beam alpha is low so the satellites and Brain stay readable.
    val beamAlpha = 0.32f
    val beamTop = Offset(center.x, center.y - 30f * unit)
    val beamLeft = Offset(center.x - 26f * unit, center.y + 18f * unit)
    val beamRight = Offset(center.x + 26f * unit, center.y + 18f * unit)
    drawLine(
        color = ringColor.copy(alpha = beamAlpha),
        start = center,
        end = beamTop,
        strokeWidth = 1.8f * unit,
    )
    drawLine(
        color = ringColor.copy(alpha = beamAlpha),
        start = center,
        end = beamLeft,
        strokeWidth = 1.8f * unit,
    )
    drawLine(
        color = ringColor.copy(alpha = beamAlpha),
        start = center,
        end = beamRight,
        strokeWidth = 1.8f * unit,
    )

    // --- Brain node (central, radial gradient) ---------------------------
    val brainRadius = 14f * unit
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                brainColor.copy(alpha = 1f),
                brainColor.copy(alpha = 0.85f),
            ),
            center = center,
            radius = brainRadius,
        ),
        radius = brainRadius,
        center = center,
    )

    // --- satellite nodes -------------------------------------------------
    val satelliteRadius = 7f * unit
    drawCircle(
        color = satelliteTop,
        radius = satelliteRadius,
        center = beamTop,
    )
    drawCircle(
        color = satelliteLeft,
        radius = satelliteRadius,
        center = beamLeft,
    )
    drawCircle(
        color = satelliteRight,
        radius = satelliteRadius,
        center = beamRight,
    )
}

// At 24 dp the geometry above aliases visibly. Callers wanting a
// sharper small mark (e.g. inside the pill input's leading slot)
// should pass `MeshlitMark(size = 24.dp)` only after the geometry
// has been re-tuned for the small size — flagged in `docs/UI_AUDIT.md`
// as item 5. For now, 32 dp and up render crisply.
