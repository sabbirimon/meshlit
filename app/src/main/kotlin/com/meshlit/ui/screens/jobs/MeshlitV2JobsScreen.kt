package com.meshlit.ui.screens.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.MeshlitApplication
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.net.MetricsSnapshot
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.MeshlitShimmerProgressBar
import com.meshlit.design.StitchPalette
import com.meshlit.design.ping
import kotlinx.coroutines.delay

/**
 * Stitch-parity Jobs screen — list of inference tasks with progress
 * shimmer bars.
 *
 * Wired to the live
 * [com.meshlit.inference.MetricsRegistry] + [com.meshlit.core.inference.InferenceCoordinator]
 * instead of a hardcoded 4-task list. We render:
 *
 *  - One "Current job" row that reflects the coordinator's actual
 *    state — `Loading` / `Ready` / `Generating` / `Error` / `Idle`.
 *    The model name comes from `loadedModel.modelPath`, so the row
 *    tracks whatever the user just loaded from the Models screen.
 *  - One row per entry in `MetricsRegistry.failureTags` (the per-tag
 *    failure breakdown the registry accumulates as jobs complete).
 *  - A small header summary row above the list: queue depth,
 *    cumulative success count, average t/s, total tokens generated.
 *
 * The metrics registry is sampled at 1 Hz to keep the snapshot in
 * sync — a higher cadence just churns recompositions without giving
 * the user anything visible to read.
 */
@Composable
fun MeshlitV2JobsScreen(palette: StitchPalette = StitchPalette.DARK) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val coordinatorState by app.inferenceCoordinator.state.collectAsState()
    var snapshot by remember { mutableStateOf(app.metricsRegistry.snapshot()) }
    LaunchedEffect(Unit) {
        // Tick once a second; the registry is itself a counter so
        // there's no Flow to subscribe to — pulling snapshot() on a
        // timer is the simplest reactive loop.
        while (true) {
            snapshot = app.metricsRegistry.snapshot()
            app.metricsRegistry.tickSparkline()
            delay(1_000L)
        }
    }

    val tasks = remember(coordinatorState, snapshot) {
        buildTasks(coordinatorState, snapshot)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = "Inference Jobs",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = jobsSummary(snapshot, coordinatorState),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No inference activity yet",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Load a model from the Models screen, then send a prompt to start streaming jobs here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.id }) { task -> JobRow(task = task, palette = palette) }
            }
        }
    }
}

/** Title-case the status for display, falling back to the raw tag. */
private fun statusLabel(state: JobStatus): String = state.name

/** Coordinator state + cumulative metrics → UI rows. */
private fun buildTasks(state: CoordinatorState, snapshot: MetricsSnapshot): List<JobTask> {
    val rows = mutableListOf<JobTask>()
    // ── 1. Current job — always shown so the user can see the live
    //    coordinator state even when no failure rows exist yet.
    val (title, status, model, tps, eta, progress, jobState) = when (state) {
        is CoordinatorState.Idle -> JobTaskRow(
            title = "Coordinator idle",
            status = "Idle",
            model = "No model loaded",
            tps = "—",
            eta = "—",
            progress = 0f,
            state = JobStatus.Queued,
        )
        is CoordinatorState.Loading -> JobTaskRow(
            title = "Loading model",
            status = "Loading",
            model = state.modelPath.substringAfterLast('/').ifBlank { state.modelPath },
            tps = "—",
            eta = "~${(state.runtime?.displayName ?: "loading")}",
            progress = 0.4f,
            state = JobStatus.Queued,
        )
        is CoordinatorState.WarmingUp -> JobTaskRow(
            title = "Warming up model",
            status = "Warming up",
            model = state.modelPath.substringAfterLast('/').ifBlank { state.modelPath },
            tps = "—",
            eta = "~${(state.runtime?.displayName ?: "warming up")}",
            progress = 0.5f,
            state = JobStatus.Queued,
        )
        is CoordinatorState.Ready -> JobTaskRow(
            title = "Ready for prompt",
            status = "Ready",
            model = state.model.modelPath.substringAfterLast('/').ifBlank { state.model.modelPath },
            tps = "—",
            eta = state.runtime?.displayName ?: "—",
            progress = 0f,
            state = JobStatus.Queued,
        )
        is CoordinatorState.Generating -> JobTaskRow(
            title = "Distributed text generation",
            status = "Running",
            model = state.runtime?.displayName ?: "Runtime",
            tps = if (snapshot.avgTokensPerSecond > 0f)
                "%.1f t/s".format(snapshot.avgTokensPerSecond) else "—",
            eta = "live",
            progress = 0.5f,
            state = JobStatus.Running,
        )
        is CoordinatorState.Error -> JobTaskRow(
            title = "Engine error",
            status = "Failed",
            model = state.runtime?.displayName ?: state.message,
            tps = "—",
            eta = "—",
            progress = 0f,
            state = JobStatus.Failed,
        )
    }
    rows += JobTask(title, status, model, tps, eta, progress, jobState)

    // ── 2. Failure breakdown — only render tags that fired at least
    //    once. This is the only place the user can see WHICH error
    //    tags are dominating.
    snapshot.failureTags.forEach { (tag, count) ->
        if (count > 0L) {
            rows += JobTask(
                title = "Failure: $tag",
                status = "Failed",
                model = "$count occurrence${if (count == 1L) "" else "s"}",
                tps = "—",
                eta = "—",
                progress = 0f,
                state = JobStatus.Failed,
            )
        }
    }
    return rows
}

/** One-line header copy that summarizes the live counters. */
private fun jobsSummary(snapshot: MetricsSnapshot, state: CoordinatorState): String {
    val queue = "Queue ${snapshot.queueDepth}"
    val success = "Done ${snapshot.successJobs}/${snapshot.totalJobs}"
    val tps = if (snapshot.avgTokensPerSecond > 0f)
        "%.1f t/s".format(snapshot.avgTokensPerSecond) else "—"
    val live = when (state) {
        is CoordinatorState.Generating -> " · generating now"
        is CoordinatorState.Loading -> " · loading"
        is CoordinatorState.WarmingUp -> " · warming up"
        is CoordinatorState.Ready -> " · ready"
        is CoordinatorState.Error -> " · engine error"
        is CoordinatorState.Idle -> ""
    }
    return "$queue · $success · $tps · ${snapshot.totalTokensGenerated} tokens$live"
}

private data class JobTaskRow(
    val title: String,
    val status: String,
    val model: String,
    val tps: String,
    val eta: String,
    val progress: Float,
    val state: JobStatus,
)

private data class JobTask(
    val id: String,
    val title: String,
    val status: String,
    val model: String,
    val tps: String,
    val eta: String,
    val progress: Float,
    val state: JobStatus,
)

private fun JobTask(
    title: String, status: String, model: String, tps: String, eta: String,
    progress: Float, state: JobStatus,
) = JobTask(
    id = "$title|$model|$status",
    title = title, status = status, model = model,
    tps = tps, eta = eta, progress = progress, state = state,
)

private enum class JobStatus { Running, Queued, Complete, Failed }

@Composable
private fun JobRow(task: JobTask, palette: StitchPalette) {
    val statusColor: Color = when (task.state) {
        JobStatus.Running -> MeshlitDesignPalette.iridescentStart
        JobStatus.Queued -> MeshlitDesignPalette.Dark.textAmber
        JobStatus.Complete -> MeshlitDesignPalette.iridescentEnd
        JobStatus.Failed -> MeshlitDesignPalette.iridescentPink
    }
    val icon = when (task.state) {
        JobStatus.Running -> Icons.Outlined.Refresh
        JobStatus.Queued -> Icons.Outlined.Schedule
        JobStatus.Complete -> Icons.Outlined.CheckCircle
        JobStatus.Failed -> Icons.Outlined.Refresh
    }

    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .ping(color = statusColor, enabled = task.state == JobStatus.Running)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = task.model,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = task.status,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = task.status,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MeshlitShimmerProgressBar(progress = task.progress)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${(task.progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Text(
                    text = if (task.tps != "—") "${task.tps} · ETA ${task.eta}" else "ETA ${task.eta}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
