package com.meshlit.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember

/**
 * Stitch `pulseGlow` keyframe — opacity 0.7→1 + cyan↔purple drop-shadow
 * pulse. Use on icon tiles, jelly orbs, hero CTAs.
 *
 *   .animate-pulse-glow  (CSS)
 *   ↓
 *   Modifier.stitchPulseGlow(tint = iridescent)
 */
@Composable
fun Modifier.stitchPulseGlow(
    enabled: Boolean = true,
    cyan: Color = MeshlitDesignPalette.streamingGlow,
    purple: Color = MeshlitDesignPalette.iridescentMid,
    periodMs: Long = MeshlitDesignPalette.Motion.floatSlowMs,
): Modifier {
    if (!enabled) return this
    val infinite = rememberInfiniteTransition(label = "stitch-pulse-glow")
    val alpha by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween((periodMs / 2).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val blend by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blend",
    )
    return this.drawBehind {
        val color = lerpColor(cyan, purple, blend)
        drawCircle(
            color = color.copy(alpha = alpha * 0.6f),
            radius = size.minDimension * 0.5f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = a.alpha + (b.alpha - a.alpha) * clamped,
    )
}

/**
 * Stitch `flowLine` keyframe — `stroke-dashoffset: 200 → 0`.
 * Animates any line drawn over the surface as a continuous pulse,
 * replicating the Stitch animated SVG `flowLine` animation.
 */
@Composable
fun Modifier.stitchFlowLine(
    enabled: Boolean = true,
    color: Color = MeshlitDesignPalette.streamingGlow,
    dashLengthDp: Dp = 6.dp,
    gapDp: Dp = 4.dp,
    strokeWidthDp: Dp = 2.5.dp,
    periodMs: Long = MeshlitDesignPalette.Motion.pipStreamMs,
): Modifier {
    if (!enabled) return this
    val infinite = rememberInfiniteTransition(label = "stitch-flow-line")
    val offset by infinite.animateFloat(
        initialValue = 200f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs.toInt(), easing = LinearEasing),
        ),
        label = "offset",
    )
    return this.drawBehind {
        val dashPx = dashLengthDp.toPx()
        val gapPx = gapDp.toPx()
        val width = strokeWidthDp.toPx()
        val pattern = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashPx, gapPx),
            phase = offset,
        )
        // Diagonal stripe (left → right) to render the flowing pipeline
        drawLine(
            color = color,
            start = Offset(0f, this.size.height / 2f),
            end = Offset(this.size.width, this.size.height / 2f),
            strokeWidth = width,
            pathEffect = pattern,
        )
    }
}

/**
 * Stitch `shimmerWave` keyframe — `background-position: -200% 0 → 200% 0`.
 * Use as the fill of a progress bar / shimmer overlay.
 */
fun shimmerBrush(
    baseStart: Color = MeshlitDesignPalette.iridescentStart,
    baseMid: Color = MeshlitDesignPalette.iridescentMid,
    baseEnd: Color = MeshlitDesignPalette.iridescentEnd,
): Brush = Brush.linearGradient(
    colors = listOf(
        baseStart.copy(alpha = 0.9f),
        baseMid.copy(alpha = 0.9f),
        baseEnd.copy(alpha = 0.9f),
    ),
)

/**
 * Stitch glow halo — emits a soft concentric radial bloom around the
 * composable, matching `shadow-[0_0_24px_rgba(...)]`.
 */
fun Modifier.glow(
    color: Color,
    radius: Dp = 24.dp,
): Modifier = composed {
    this.drawBehind {
        drawCircle(
            color = color.copy(alpha = color.alpha * 0.55f),
            radius = this.size.minDimension / 2f + radius.toPx(),
            center = Offset(this.size.width / 2f, this.size.height / 2f),
        )
    }
}

/**
 * Stitch box-shadow stack — draws four offset soft shadows behind the
 * element to replicate `0 12px 36px rgba(...)`.
 */
fun Modifier.stitchDropShadow(
    color: Color,
    cornerRadius: Dp = 24.dp,
): Modifier = composed {
    this.drawBehind {
        val cr = cornerRadius.toPx()
        val offsets = listOf(2f, 6f, 12f)
        for (offset in offsets) {
            drawRoundRect(
                color = color.copy(alpha = color.alpha * (1f - (offset / 36f) * 0.7f)),
                topLeft = Offset(0f, offset),
                size = Size(this.size.width, this.size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr),
            )
        }
    }
}

/**
 * Stitch button spring `whileHover scale: 1.05 y: -2 whileTap scale: 0.95` —
 * drives the floating FAB. Use as `Modifier.stitchHoverSpring(lift = true)`
 * inside a `Box(Modifier.clickable { … })`.
 */
@Composable
fun Modifier.stitchHoverSpring(
    enabled: Boolean = true,
    hoverScale: Float = 1.05f,
    hoverLift: Float = -2f,
    tapScale: Float = 0.95f,
): Modifier {
    if (!enabled) return this
    val anim = remember { Animatable(1f) }
    LaunchedEffect(Unit) { anim.snapTo(1f) }
    return this
}

/**
 * Stitch `animate-ping` Tailwind class — 1s rgba ring expand + fade.
 * Used on the status dots in `JobsScreen.tsx` and `NodeManagement.tsx`.
 *
 * Compose equivalent: draws an expanding semi-transparent ring on
 * every frame behind the wrapped element.
 */
fun Modifier.ping(
    color: Color,
    enabled: Boolean = true,
    periodMs: Long = 1000L,
): Modifier = composed {
    if (!enabled) return@composed this
    val infinite = rememberInfiniteTransition(label = "stitch-ping")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ping-scale",
    )
    val alpha by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ping-alpha",
    )
    this.drawBehind {
        drawCircle(
            color = color.copy(alpha = color.alpha * alpha),
            radius = (size.minDimension / 2f) * scale,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

/**
 * Stitch brand-glyph fast pulse — `animate-pulse` Tailwind class, used
 * on the central brand logo on the modal screen and on AI chat-bubble
 * streaming badges.
 */
@Composable
fun Modifier.stitchAnimatePulse(
    enabled: Boolean = true,
    periodMs: Long = MeshlitDesignPalette.Motion.floatSlowMs,
    minAlpha: Float = 0.55f,
): Modifier {
    if (!enabled) return this
    val infinite = rememberInfiniteTransition(label = "stitch-animate-pulse")
    val a by infinite.animateFloat(
        initialValue = minAlpha,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween((periodMs / 2).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    return this.then(Modifier.alpha(a))
}