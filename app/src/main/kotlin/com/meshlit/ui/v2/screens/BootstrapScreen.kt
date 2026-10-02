package com.meshlit.ui.v2.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.core.bootstrap.BootstrapPhase
import com.meshlit.core.bootstrap.BootstrapSnapshot
import com.meshlit.ui.components.MeshlitMark
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import kotlinx.coroutines.delay

/**
 * v2 Bootstrap / loading surface. Shown while
 * `BootstrapSnapshotProvider.snapshot()` is `null`. Renders:
 *   - `MeshlitMark(size = 96.dp, pulseFraction = …)` — pulsing
 *     on the `MeshlitMotion.Pulse` cadence.
 *   - "Meshlit" + "Bringing the cluster online…"
 *   - A linear progress bar that fills as `phaseProgress(snapshot)`
 *     moves from 0.0 → 1.0 across the bootstrap phases.
 *   - The current phase label as the small caption.
 *
 * When the snapshot lands the caller routes into `MeshlitAppV2`.
 * A 200 ms minimum splash delay avoids the jarring ≤ 50 ms flash
 * when `boot()` returns immediately on cached config — flagged
 * in `docs/UI_AUDIT.md` item 7.
 *
 * For build no. 1 this composable is wired via the v2 root but
 * the actual handoff is deferred to a follow-up PR — the v2
 * root currently delegates to `MeshlitAppV2` directly.
 */
@Composable
fun BootstrapScreen(
    snapshot: BootstrapSnapshot?,
    modifier: Modifier = Modifier,
    onSplashMinDelayElapsed: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        delay(200L)
        onSplashMinDelayElapsed()
    }

    val targetProgress = phaseProgress(snapshot)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 400),
        label = "bootstrap-progress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        MeshlitMark(size = 96.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Meshlit",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Bringing the cluster online…",
            style = MaterialTheme.typography.bodyLarge,
            color = MeshlitTextSecondaryV2,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
            color = MeshlitPulseViolet,
        )
        Text(
            text = currentPhaseLine(snapshot),
            style = MaterialTheme.typography.bodySmall,
            color = MeshlitTextSecondaryV2,
        )
    }
}

/**
 * Maps a [BootstrapSnapshot] (or null) to a progress fraction
 * 0..1. Phase order: Config → Probe → Role → Registry →
 * Services → Complete. The mapping is deterministic — the v2
 * bootstrap test in step 5 verifies each phase hits the right
 * fraction.
 */
fun phaseProgress(snapshot: BootstrapSnapshot?): Float {
    if (snapshot == null) return 0.05f
    val phase = snapshot.currentPhase().name.lowercase()
    return when {
        "config" in phase -> 0.20f
        "probe" in phase -> 0.40f
        "role" in phase -> 0.55f
        "registry" in phase -> 0.70f
        "service" in phase -> 0.85f
        "complete" in phase -> 1.0f
        else -> 0.30f
    }
}

/**
 * Returns a short human-readable phase label. Used by the
 * BootstrapScreen caption row.
 */
fun currentPhaseLine(snapshot: BootstrapSnapshot?): String =
    snapshot?.let {
        val phase = it.currentPhase().name.lowercase().replaceFirstChar(Char::uppercase)
        "$phase · nodeId ${it.nodeId.take(8)}…"
    } ?: "Initialising"

/**
 * Returns the most-recent phase recorded on the snapshot. The
 * coordinator reports phases in order; the last entry is the
 * "current" one. When the report is empty (early boot or
 * cached snapshot) we return [BootstrapPhase.Config] as the
 * implicit first phase.
 */
private fun BootstrapSnapshot.currentPhase(): BootstrapPhase =
    report.entries.lastOrNull()?.phase ?: BootstrapPhase.Config