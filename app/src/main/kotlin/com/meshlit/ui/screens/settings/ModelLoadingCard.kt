package com.meshlit.ui.screens.settings

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.InferenceEvent
import java.io.File

/**
 * Loading-status card for the Models hub.
 *
 * The Models screen previously had no UX for "model is loading into
 * native memory, here's the status". A row's `ModelTrailingAction` is
 * a tiny spinner, and the bigger `ActiveModelBanner` only flips to
 * Ready once the load lands. This card sits between those two and
 * surfaces the coordinator's `Loading / WarmingUp / Ready / Error`
 * states plus the synthesised `LoadProgress` events the coordinator
 * emits from `loadModelInternal`.
 *
 * Visual:
 *  - 1.5 s ease-in-out pulsing radial gradient behind the card
 *    (`rememberInfiniteTransition` + `animateFloat` — no new deps).
 *  - Status icon (hourglass / check / error) on the leading edge.
 *  - Two-line status: title (`Validating…`, `Loading weights`,
 *    `Warming up…`, `Ready`, `Load failed`) + subtitle (file name +
 *    runtime + format).
 *  - `LinearProgressIndicator` showing the highest-seen
 *    `LoadProgress.fraction`. Resets to 0 on every fresh
 *    `LoadStarted` event for a new path.
 *  - "Cancel" outlined button that calls `vm.unload()` — the user
 *    can abort a stuck load without leaving the screen.
 *
 * The card is intentionally compact (one line of vertical space for
 * the body, one for the progress bar). It composes inside the
 * Models screen's `LazyColumn` so the layout flow is unchanged when
 * nothing is loading.
 */
@Composable
fun ModelLoadingCard(
    app: MeshlitApplication,
    vm: ModelSelectionViewModel,
    modifier: Modifier = Modifier,
) {
    val coordinator = remember(app) { app.inferenceCoordinator }
    val state by coordinator.state.collectAsState()
    val showCard = state is CoordinatorState.Loading ||
        state is CoordinatorState.WarmingUp ||
        state is CoordinatorState.Error
    if (!showCard) return

    // Track the highest-seen LoadProgress fraction. The coordinator
    // emits synthetic 0.10 / 0.30 / 0.90 ticks during
    // `loadModelInternal`; the RunAnywhere engine doesn't surface a
    // real progress stream, so these ticks are what the UI gets.
    // We latch the maximum so the bar only moves forward, never
    // backward, even if the coordinator re-emits a smaller fraction
    // (e.g. on re-entry into WarmingUp).
    var progress by remember { mutableFloatStateOf(0f) }
    var lastPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(coordinator) {
        coordinator.events.collect { ev ->
            when (ev) {
                is InferenceEvent.LoadStarted -> {
                    progress = 0f
                    lastPath = ev.modelPath
                }
                is InferenceEvent.LoadProgress -> {
                    if (lastPath == null || ev.modelPath == lastPath) {
                        lastPath = ev.modelPath
                        if (ev.fraction > progress) progress = ev.fraction
                    }
                }
                is InferenceEvent.LoadSucceeded -> progress = 1f
                is InferenceEvent.LoadFailed -> progress = 1f
                else -> Unit
            }
        }
    }

    val (icon, title, subtitle) = when (val s = state) {
        is CoordinatorState.Loading -> Triple(
            Icons.Filled.HourglassTop,
            "Validating model…",
            prettySubtitle(s.modelPath, s.format),
        )
        is CoordinatorState.WarmingUp -> Triple(
            Icons.Filled.HourglassTop,
            "Warming up engine…",
            prettySubtitle(s.modelPath, s.format),
        )
        is CoordinatorState.Ready -> Triple(
            Icons.Filled.CheckCircle,
            "Ready",
            s.model.modelName,
        )
        is CoordinatorState.Error -> Triple(
            Icons.Filled.ErrorOutline,
            "Load failed",
            s.message,
        )
        else -> Triple(Icons.Filled.HourglassTop, "Loading…", "")
    }

    // Pulsing radial-gradient backdrop. Slow cycle so it reads as
    // ambient motion, not a spinner. Phase-shifted alpha so the
    // gradient "breathes" between 0.5 and 0.9 saturation.
    val transition = rememberInfiniteTransition(label = "load-card-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "load-card-alpha",
    )
    val scheme = MaterialTheme.colorScheme
    val pulseBrush = remember(scheme.primary, scheme.secondaryContainer, pulse) {
        Brush.radialGradient(
            colors = listOf(
                scheme.primary.copy(alpha = pulse * 0.20f),
                scheme.secondaryContainer.copy(alpha = pulse * 0.10f),
                Color.Transparent,
            ),
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Backdrop. graphicsLayer keeps the gradient anchored to
            // the card even when the LazyColumn scrolls.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f }
                    .background(pulseBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = when (state) {
                            is CoordinatorState.Error -> scheme.error
                            is CoordinatorState.Ready -> scheme.primary
                            else -> scheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    OutlinedButton(onClick = { vm.unload() }) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                // Progress bar. The `progress` float is clamped to
                // [0,1]; the indeterminate branch fires when the
                // coordinator emits LoadStarted but no follow-up
                // ticks yet (the bar pulses until the first fraction
                // arrives).
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Build the subtitle for the [ModelLoadingCard] from a coordinator
 * state. Shows file basename + format + runtime so the user can see
 * exactly which model is being loaded. Centralised so the
 * `CoordinatorState.Loading` and `CoordinatorState.WarmingUp` branches
 * render the same shape.
 */
private fun prettySubtitle(modelPath: String, format: com.meshlit.core.inference.FileFormat?): String {
    val name = File(modelPath).nameWithoutExtension
    val fmt = when (format) {
        com.meshlit.core.inference.FileFormat.Gguf -> "GGUF"
        com.meshlit.core.inference.FileFormat.Onnx -> "ONNX"
        com.meshlit.core.inference.FileFormat.Task -> "LiteRT-LM"
        com.meshlit.core.inference.FileFormat.Coreml -> "CoreML"
        com.meshlit.core.inference.FileFormat.Mlx -> "MLX"
        com.meshlit.core.inference.FileFormat.Safetensors -> "SafeTensors"
        com.meshlit.core.inference.FileFormat.Tflite -> "TFLite"
        null -> ""
    }
    return listOf(name.takeIf { it.isNotBlank() }, fmt.takeIf { it.isNotBlank() })
        .filterNotNull()
        .joinToString(" · ")
}