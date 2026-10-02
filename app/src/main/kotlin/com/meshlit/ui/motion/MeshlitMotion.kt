package com.meshlit.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Project-wide motion vocabulary. Anything that animates a Compose
 * value should pull its [AnimationSpec] from here so the timing
 * stays coherent across screens. Three generations of presets:
 *
 * **v1 (legacy — kept for v1 build consumption):**
 *  - [Short] — 250 ms `tween`. Used for color/scale toggles
 *    (active/idle, icon scale, accent fades). Matches the rhythm
 *    already in use at `MeshlitBottomBar.kt:128-143` and
 *    `MeshlitDrawer.kt:204`.
 *  - [Medium] — 400 ms `tween`. Used for status-card swaps and
 *    state-driven `AnimatedContent` transitions where a faster
 *    snap feels jumpy.
 *  - [Springy] — `spring(MediumBouncy, MediumLow)`. Used for
 *    sheets, cards, and expand-collapse. Feels physical without
 *    overshooting visibly.
 *
 * **v2 (Meshlit v2 build — purpose-driven motion):**
 *  - [PageEnter] — 180 ms `tween(FastOutSlowIn)`. Used by
 *    `MeshlitAppV2`'s `AnimatedContent` between top-level
 *    destinations. Easing matches Material 3's `MotionTokens`.
 *  - [Modal] — 220 ms `tween(FastOutSlowIn)`. Used by modal
 *    bottom sheets in the v2 build (QuickActionSheet, etc.).
 *  - [CardHover] — 300 ms `tween(FastOutSlowIn)`. Used by
 *    `MeshlitLeadBar` accent and `MeshlitEndpointCard` press
 *    feedback. Slightly longer than PageEnter so a tap registers
 *    as deliberate, not accidental.
 *  - [Pulse] — 2.2 s `infiniteRepeatable(LinearEasing)`. Used by
 *    `MeshlitPulseGradient` and `MeshlitMark` (orbital ring
 *    alpha). The exact timing is duplicated in `rememberMeshlitPulsePhase()`
 *    in `MeshlitPulseGradient.kt` so the brush drift and the
 *    Compose state machine drift stay in lockstep.
 *
 * If a future animation needs a different curve, add it here rather
 * than inlining a `tween(...)` call. We want one place to tune the
 * project's motion language.
 */
object MeshlitMotion {
    val Short: AnimationSpec<Float> = tween(durationMillis = 250)
    val Medium: AnimationSpec<Float> = tween(durationMillis = 400)
    val Springy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ---- v2 --------------------------------------------------------------
    val PageEnter: AnimationSpec<Float> = tween(
        durationMillis = 180,
        easing = FastOutSlowInEasing,
    )
    val Modal: AnimationSpec<Float> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )
    val CardHover: AnimationSpec<Float> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing,
    )
    val Pulse: AnimationSpec<Float> = infiniteRepeatable(
        animation = tween(durationMillis = 2200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    )
}
