package com.meshlit.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Project-wide motion vocabulary. Anything that animates a Compose
 * value should pull its [AnimationSpec] from here so the timing
 * stays coherent across screens. The three presets:
 *
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
}
