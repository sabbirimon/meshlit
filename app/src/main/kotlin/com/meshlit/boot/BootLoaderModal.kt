package com.meshlit.boot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import kotlinx.coroutines.delay

/**
 * Phase 4.x — `Commit 31: BootLoaderModal`.
 *
 * Modal splash shown during cold-start bundled-model extraction.
 * Hides itself once extraction finishes (success *or* failure —
 * we never block the app on a missing bundled model).
 *
 * Visual structure:
 *   - Full-screen scrim (semi-transparent backdrop)
 *   - Centered Material card with:
 *       - Animated brand glyph (rotating diamond)
 *       - Title "Preparing Meshlit"
 *       - 3-step "checklist" (extract, register, warm)
 *       - Progress bar (determinate when bytesCopied > 0)
 *       - Stage caption (changes with `stage`)
 *
 * The modal lives at the root of `MeshlitApp` so it can
 * overlay any screen, including the SetupWizard. It is
 * driven entirely by `BootLoaderState`, which is a
 * `MutableStateFlow` on `MeshlitApplication` so any
 * coroutine (installer, FGS, deep-link) can update it.
 *
 * Failure mode: if [BootLoaderState.error] is non-null, we
 * show a single-line caption in red and dismiss after 2.5s
 * so the user is not stuck on a modal.
 */
@Composable
fun BootLoaderModal(
    app: MeshlitApplication,
    modifier: Modifier = Modifier,
) {
    val state by app.bootLoaderState.collectState()
    // Auto-dismiss on success after a brief pause so the
    // user actually sees the "ready" tick.
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        when (state.phase) {
            BootLoaderPhase.READY -> {
                delay(900L)
                dismissed = true
            }
            BootLoaderPhase.FAILED -> {
                delay(2500L)
                dismissed = true
            }
            BootLoaderPhase.IDLE -> dismissed = true
            BootLoaderPhase.RUNNING -> dismissed = false
        }
    }
    val visible = !dismissed &&
        state.phase != BootLoaderPhase.IDLE
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 260)),
        modifier = modifier,
    ) {
        BootLoaderScrim(state = state)
    }
}

@Composable
private fun BootLoaderScrim(state: BootLoaderState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .width(360.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            BootLoaderCardContent(state = state)
        }
    }
}

@Composable
private fun BootLoaderCardContent(state: BootLoaderState) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedBrandGlyph(phase = state.phase)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = titleForPhase(state.phase),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.stage.ifBlank { captionForPhase(state.phase) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Phase 4.x — B-022: on failure, surface the actual
        // error string in the modal body so the user has a hint
        // before the modal auto-dismisses. The bell notice
        // carries the longer recovery instructions.
        if (state.phase == BootLoaderPhase.FAILED && !state.error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        ProgressLine(state = state)
        Spacer(modifier = Modifier.height(20.dp))
        BootStepChecklist(state = state)
    }
}

/**
 * Rotating diamond + inner pulse. Three states:
 *   - RUNNING: outer ring rotates, inner dot pulses.
 *   - READY  : green check replaces the glyph.
 *   - FAILED : red exclamation replaces the glyph (red tinted).
 */
@Composable
private fun AnimatedBrandGlyph(phase: BootLoaderPhase) {
    val infinite = rememberInfiniteTransition(label = "boot-loader-glyph")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            BootLoaderPhase.RUNNING -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(64.dp)
                        .rotate(rotation),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(pulse),
                )
            }
            BootLoaderPhase.READY -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Ready",
                    tint = Color(0xFF1FA968),
                    modifier = Modifier.size(64.dp),
                )
            }
            BootLoaderPhase.FAILED -> {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp),
                )
            }
            BootLoaderPhase.IDLE -> {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(state: BootLoaderState) {
    when {
        state.error != null -> {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        state.fraction > 0f -> {
            LinearProgressIndicator(
                progress = { state.fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        state.phase == BootLoaderPhase.RUNNING -> {
            // Indeterminate strip when the installer hasn't
            // reported any bytes yet — many cold-starts emit
            // LoadProgress only once the file is partly on
            // disk.
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        else -> {
            // READY: empty bar (clean look after completion).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}

/**
 * Three-step checklist that ticks off as the boot advances.
 * The `step` field on [BootLoaderState] is 0..3 where 3 = done.
 */
@Composable
private fun BootStepChecklist(state: BootLoaderState) {
    val activeStep = state.step.coerceIn(0, 3)
    val steps = listOf(
        "Extract bundled model" to (activeStep >= 1),
        "Register inference engine" to (activeStep >= 2),
        "Warm token cache" to (activeStep >= 3),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, (label, done) ->
            val isCurrent = i + 1 == activeStep && !done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StepDot(done = done, current = isCurrent)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.onSurface
                            else if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepDot(done: Boolean, current: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
                when {
                    done -> Color(0xFF1FA968)
                    current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        } else if (current) {
            // Active dot — no glyph (the pulse on the brand glyph
            // is the visible "we're working" signal).
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private fun titleForPhase(phase: BootLoaderPhase): String = when (phase) {
    BootLoaderPhase.RUNNING -> "Preparing Meshlit"
    BootLoaderPhase.READY -> "Ready"
    // Phase 4.x — B-022: more descriptive than the generic "Boot
    // issue" of the previous version. The user almost always hits
    // this when the bundled extraction fails (low disk space,
    // corrupt asset, sha mismatch) — they need to know what to do
    // next without opening the bell sheet.
    BootLoaderPhase.FAILED -> "Bundled model install failed"
    BootLoaderPhase.IDLE -> ""
}

private fun captionForPhase(phase: BootLoaderPhase): String = when (phase) {
    BootLoaderPhase.RUNNING -> "Extracting the bundled model from the APK…"
    BootLoaderPhase.READY -> "Bundled model is loaded. You can start chatting."
    // The exact reason lands in [BootLoaderState.error]; the
    // modal renders that as the body so the user sees something
    // actionable instead of the canned "could not be installed"
    // string. The bell notice carries the longer-form body.
    BootLoaderPhase.FAILED -> "Open the bell (top-right) for details and recovery steps."
    BootLoaderPhase.IDLE -> ""
}

// ────────────────────────────────────────────────────────────
// State + Flow extensions. The flow lives on
// MeshlitApplication so any coroutine (installer, FGS,
// deep-link) can update it without coupling to Compose.
// ────────────────────────────────────────────────────────────

/**
 * Phase the boot loader is currently in.
 *
 *   IDLE    — modal is hidden.
 *   RUNNING — extraction is in progress; show the spinner.
 *   READY   — extraction succeeded; show the green check
 *             for ~900ms then auto-dismiss.
 *   FAILED  — extraction failed; show the red error and
 *             auto-dismiss after 2.5s.
 */
enum class BootLoaderPhase { IDLE, RUNNING, READY, FAILED }

/**
 * Snapshot consumed by [BootLoaderModal]. `step` is a
 * 0..3 progress checkpoint; `fraction` is the byte-level
 * progress (0f..1f, or 0f when unknown).
 */
data class BootLoaderState(
    val phase: BootLoaderPhase = BootLoaderPhase.IDLE,
    val step: Int = 0,
    val fraction: Float = 0f,
    val stage: String = "",
    val error: String? = null,
)

/**
 * Compose helper that bridges `MutableStateFlow<BootLoaderState>`
 * into a `State<BootLoaderState>`. We avoid pulling in
 * `collectAsStateWithLifecycle` here because the boot loader
 * lives at the app root and shouldn't depend on the lifecycle
 * artifact.
 */
@Composable
private fun kotlinx.coroutines.flow.StateFlow<BootLoaderState>.collectState():
    androidx.compose.runtime.State<BootLoaderState> = collectAsState(initial = BootLoaderState())
