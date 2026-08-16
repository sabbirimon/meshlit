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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.design.MeshlitDesignPalette

/**
 * Tiny "is the model actually ready" indicator that lives next
 * to the screen title in [MeshlitHeader]. It maps the live
 * [CoordinatorState] to a colour + short label so the user can
 * tell whether their prompt will be answered, queued, or
 * bounced — at a glance, from any top-level screen.
 *
 * Behaviour matrix (matches the previous in-app status light):
 *
 *  - **Idle** (no model loaded) → slate-gray dot · "Off"
 *  - **Loading** / **WarmingUp** → amber dot, pulsing · "Loading…"
 *  - **Ready** → emerald dot · "Ready"
 *  - **Generating** → cyan dot, pulsing · "Generating"
 *  - **Error** → pink dot · "Error"
 *
 * The pill animates between states via the same 220 ms tween
 * used by [MeshlitHeader]'s accent transition so the colour
 * change reads as part of the same family as the title pulse.
 *
 * Implementation note: we deliberately *don't* call `glow()` on
 * the dot — the surrounding header surface already has a soft
 * shadow and adding another halo would make the row look noisy
 * on small screens. The pulse animation is the "alive" signal.
 */
@Composable
fun EngineStatusLight(
    state: CoordinatorState,
    modifier: Modifier = Modifier,
) {
    val tag = engineStatusTag(state)
    val animatedColor by animateColorAsState(
        targetValue = engineStatusColor(state),
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "engine-status-color",
    )

    // Pulse the dot while the model is loading or generating so
    // the user can see "work is happening" without a number.
    val pulsing = state is CoordinatorState.Loading ||
        state is CoordinatorState.WarmingUp ||
        state is CoordinatorState.Generating
    val infinite = rememberInfiniteTransition(label = "engine-status-pulse")
    val pulse by infinite.animateFloat(
        initialValue = if (pulsing) 0.55f else 1f,
        targetValue = if (pulsing) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "engine-status-pulse-progress",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(animatedColor.copy(alpha = pulse)),
        )
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = animatedColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/** Map the coordinator state to its display colour. Pulls from
 *  the curated [MeshlitDesignPalette] so the light reads as
 *  part of the same family as the brand gradient and tier pill.
 */
private fun engineStatusColor(state: CoordinatorState): Color = when (state) {
    is CoordinatorState.Idle ->
        MeshlitDesignPalette.Dark.textQuaternary // slate-gray — "off"
    is CoordinatorState.Loading,
    is CoordinatorState.WarmingUp ->
        MeshlitDesignPalette.Dark.textAmber // amber — warm-up / load
    is CoordinatorState.Ready ->
        MeshlitDesignPalette.iridescentEnd // emerald — ready
    is CoordinatorState.Generating ->
        MeshlitDesignPalette.iridescentStart // cyan — generating
    is CoordinatorState.Error ->
        MeshlitDesignPalette.iridescentPink // pink — error
}

/** Short label that fits in the header pill without truncating
 *  on narrow phones. Max ~10 chars. */
private fun engineStatusTag(state: CoordinatorState): String = when (state) {
    is CoordinatorState.Idle -> "Off"
    is CoordinatorState.Loading -> "Loading…"
    is CoordinatorState.WarmingUp -> "Warming…"
    is CoordinatorState.Ready -> "Ready"
    is CoordinatorState.Generating -> "Generating"
    is CoordinatorState.Error -> "Error"
}