package com.meshlit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.net.MetricsSnapshot
import com.meshlit.di.koinInject
import com.meshlit.inference.PeerHealthCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Phase M.3 — Grafana-style monitoring screen.
 *
 * Layout (top-to-bottom):
 *  1. **Queue gauge** — current `MetricsRegistry.queueDepth` plus the
 *     last 60 seconds as a sparkline.
 *  2. **Job counters** — total / success / failure plus tokens
 *     generated and average tokens/sec.
 *  3. **Failure breakdown** — a sorted list of `tag → count`.
 *  4. **Peer health pills** — every peer in `peerHealthCache.state`
 *     rendered as a green / yellow / red pill.
 *
 * The screen drives a 1-second tick that re-reads the
 * `MetricsRegistry.snapshot()`. Peers refresh on their own cadence
 * (every 30s); the screen only re-renders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = koinInject<MeshlitApplication>()
    val scope = rememberCoroutineScope()

    val registry = remember { app.metricsRegistry }
    val peerHealthSource: PeerHealthCache? = remember { app.activePeerHealthCache() }

    val queueDepth by registry.queueDepth.collectAsState()
    val failureTags by registry.failureTagCounts.collectAsState()
    val sparkline by registry.sparkline.collectAsState()
    val peerStateFlow = remember { peerHealthSource?.state ?: kotlinx.coroutines.flow.flowOf(emptyMap()) }
    val peerMap by peerStateFlow.collectAsState(initial = emptyMap())

    var snapshot by remember { mutableStateOf(registry.snapshot()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            snapshot = registry.snapshot()
            registry.tickSparkline()
            delay(1_000L)
        }
    }

    val isActive = snapshot.queueDepth > 0 || (snapshot.totalJobs > 0 && snapshot.failureTags.isNotEmpty())

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.metrics_title),
                subtitle = stringResource(R.string.metrics_subtitle),
                tier = app.capabilityTier,
                active = isActive,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { QueueCard(depth = queueDepth, sparkline = sparkline) }
            item { CountersCard(snapshot) }
            item { FailureBreakdownCard(failureTags) }
            item { PeerHealthSection(peerMap = peerMap) }
        }
    }
}

@Composable
private fun QueueCard(depth: Int, sparkline: List<Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.metrics_queue_depth),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = depth.toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = stringResource(R.string.metrics_sparkline_caption),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Sparkline(values = sparkline, modifier = Modifier.fillMaxWidth().height(64.dp))
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        drawLine(
            color = gridColor,
            start = Offset(0f, h - 1),
            end = Offset(w, h - 1),
            strokeWidth = 1f,
        )
        val maxV = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val stepX = if (values.size > 1) w / (values.size - 1).toFloat() else 0f
        val path = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - (v / maxV) * h
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(w, h)
        fill.close()
        drawPath(path = fill, color = fillColor)
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5f),
        )
    }
}

@Composable
private fun CountersCard(snapshot: MetricsSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TwoStatRow(
                labelLeft = stringResource(R.string.metrics_total_jobs),
                valueLeft = snapshot.totalJobs.toString(),
                labelRight = stringResource(R.string.metrics_uptime),
                valueRight = formatUptime(snapshot.uptimeSeconds),
            )
            TwoStatRow(
                labelLeft = stringResource(R.string.metrics_success_jobs),
                valueLeft = snapshot.successJobs.toString(),
                labelRight = stringResource(R.string.metrics_failure_jobs),
                valueRight = snapshot.failureTags.values.sum().toString(),
            )
            TwoStatRow(
                labelLeft = stringResource(R.string.metrics_tokens_generated),
                valueLeft = snapshot.totalTokensGenerated.toString(),
                labelRight = stringResource(R.string.metrics_tokens_per_second),
                valueRight = "%.2f".format(snapshot.avgTokensPerSecond),
            )
        }
    }
}

@Composable
private fun TwoStatRow(labelLeft: String, valueLeft: String, labelRight: String, valueRight: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatColumn(labelLeft, valueLeft, Modifier.weight(1f))
        StatColumn(labelRight, valueRight, Modifier.weight(1f))
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FailureBreakdownCard(failureTags: Map<String, Long>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.metrics_failure_breakdown),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failureTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.metrics_no_failures),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val sorted = failureTags.entries.sortedByDescending { it.value }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sorted.forEach { (tag, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerHealthSection(peerMap: Map<String, PeerHealthCache.PeerHealth>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.metrics_peers_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (peerMap.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stringResource(R.string.metrics_no_peers),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                peerMap.forEach { (ip, health) ->
                    PeerHealthPill(ip = ip, health = health)
                }
            }
        }
    }
}

@Composable
private fun PeerHealthPill(ip: String, health: PeerHealthCache.PeerHealth) {
    val (statusLabel, color, icon) = when {
        health.ok && health.modelLoaded -> Triple(
            stringResource(R.string.metrics_peer_ok),
            MaterialTheme.colorScheme.primary,
            Icons.Filled.CheckCircle,
        )
        health.ok -> Triple(
            stringResource(R.string.metrics_peer_model_unloaded),
            MaterialTheme.colorScheme.tertiary,
            Icons.Filled.Warning,
        )
        else -> Triple(
            stringResource(R.string.metrics_peer_stale),
            MaterialTheme.colorScheme.error,
            Icons.Filled.ErrorOutline,
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.18f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(text = ip, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600L
    val m = (seconds % 3600L) / 60L
    val s = seconds % 60L
    return if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%02dm %02ds".format(m, s)
}
