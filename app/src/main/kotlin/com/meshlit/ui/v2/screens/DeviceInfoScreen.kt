package com.meshlit.ui.v2.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.R
import com.meshlit.core.bootstrap.BootstrapReport
import com.meshlit.core.probe.HardwareCapability
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.role.RolePolicy
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseAqua
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitLeadBar

/**
 * v2 Device Info screen. Wired to the `Info` card on the Devices
 * hub (and from the `Identity` / `QR identity` / `Export` cards via
 * the same `device_info` route). Renders five sections:
 *
 *   [ Identity ]      node id (copyable), display name (editable),
 *                     local IP, HTTP port, capability tier
 *   [ Hardware ]      CPU / memory / thermal / battery / network /
 *                     NPU, each as a `ScoreRow(score, rawValue)`
 *                     with a Re-probe hardware action
 *   [ Role ]          current role + confidence + an expandable
 *                     "Why this role?" listing the policy's
 *                     reason strings + score chips
 *   [ Bootstrap ]     per-phase outcomes (Config / Flags / Probe /
 *                     Role / Registry / Services) + each flag's
 *                     value as a chip + a Force re-bootstrap action
 *   [ Services ]      every registered `ServiceDescriptor` as a
 *                     row with its kind + health pill
 *
 * Commands:
 *  - Copy node id     → `ClipboardManager.setPrimaryClip` + Toast
 *  - Edit display     → inline `TextField` + Save button that calls
 *    name               `viewModel.onSaveDisplayName(value)`
 *  - Re-probe         → `viewModel.onReprobeHardware()`
 *    hardware
 *  - Force            → `viewModel.onForceRebootstrap()`
 *    re-bootstrap
 */
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceInfoViewModel = viewModel(factory = DeviceInfoViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val title = (uiState as? DeviceInfoUiState.Ready)?.let {
        "${it.role.role.name} · ${it.capabilityTier.name}"
    } ?: "Device info"

    Column(modifier = modifier.fillMaxSize()) {
        MeshlitLeadBar(
            headline = "Device info",
            subtitle = title,
        )
        when (val state = uiState) {
            DeviceInfoUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DeviceInfoUiState.Failure -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is DeviceInfoUiState.Ready -> {
                DeviceInfoReadyView(
                    state = state,
                    onCopyNodeId = {
                        val nodeId = viewModel.nodeIdForCopy()
                        if (nodeId.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.info_copy_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE,
                            ) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("node id", nodeId),
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.info_node_id_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onSaveDisplayName = { value ->
                        viewModel.onSaveDisplayName(value)
                        Toast.makeText(
                            context,
                            context.getString(R.string.info_display_name_saved),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onReprobeHardware = { viewModel.onReprobeHardware() },
                    onToggleLiveMonitor = { viewModel.onToggleLiveMonitor() },
                    onForceRebootstrap = { viewModel.onForceRebootstrap() },
                )
            }
        }
    }
    // Touch the back callback so the compiler doesn't drop the
    // parameter — the NavHost wires it into the system back
    // gesture via the destination registration.
    @Suppress("UNUSED_VARIABLE") val unusedBack = onBack
}

// -- section + row composables ----------------------------------------

@Composable
private fun DeviceInfoReadyView(
    state: DeviceInfoUiState.Ready,
    onCopyNodeId: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onReprobeHardware: () -> Unit,
    onToggleLiveMonitor: () -> Unit,
    onForceRebootstrap: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "identity") {
            IdentityCard(
                nodeIdHex = state.nodeIdHex,
                displayName = state.displayName,
                defaultDisplayName = state.defaultDisplayName,
                localIp = state.localIpAddress,
                httpPort = state.httpServerPort,
                capabilityTierName = state.capabilityTier.name,
                onCopyNodeId = onCopyNodeId,
                onSaveDisplayName = onSaveDisplayName,
            )
        }
        item(key = "hardware") {
            HardwareCard(
                hardware = state.hardware,
                ageMs = state.hardwareAgeMs,
                isReprobing = state.isReprobing,
                isLive = state.isLiveMonitor,
                onReprobeHardware = onReprobeHardware,
                onToggleLive = onToggleLiveMonitor,
                cpuSeries = state.cpuSeries,
                memorySeries = state.memorySeries,
                thermalSeries = state.thermalSeries,
                batterySeries = state.batterySeries,
                networkSeries = state.networkSeries,
                diskSeries = state.diskSeries,
            )
        }
        item(key = "role") {
            RoleCard(
                roleName = state.role.role.name,
                confidence = state.role.confidence,
                scores = state.role.scores,
                reasons = state.role.reasons,
                capability = state.hardware,
            )
        }
        item(key = "bootstrap") {
            BootstrapCard(
                report = state.bootstrapSnapshot.report,
                flags = state.bootstrapSnapshot.flags,
                isRebootstrapping = state.isRebootstrapping,
                onForceRebootstrap = onForceRebootstrap,
            )
        }
        item(key = "services") {
            ServicesCard(services = state.services)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MeshlitSurfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MeshlitTextPrimaryV2,
            )
            content()
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MeshlitTextSecondaryV2,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            color = MeshlitTextPrimaryV2,
        )
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.18f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun IdentityCard(
    nodeIdHex: String,
    displayName: String,
    defaultDisplayName: String,
    localIp: String,
    httpPort: Int,
    capabilityTierName: String,
    onCopyNodeId: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
) {
    var draft by remember(displayName) { mutableStateOf(displayName) }
    var showEditor by remember { mutableStateOf(false) }
    SectionCard(title = "Identity") {
        // Node id — copyable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Node ID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextSecondaryV2,
                )
                Text(
                    text = nodeIdHex,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
            }
            IconButton(onClick = onCopyNodeId) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy node id",
                    tint = MeshlitPulseViolet,
                )
            }
        }
        // Display name — inline editor
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Display name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextSecondaryV2,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeshlitTextPrimaryV2,
                )
                if (displayName != defaultDisplayName) {
                    Text(
                        text = "default: $defaultDisplayName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshlitTextTertiaryV2,
                    )
                }
            }
            IconButton(onClick = { showEditor = !showEditor }) {
                Icon(
                    imageVector = if (showEditor) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                    contentDescription = if (showEditor) "Hide name editor"
                        else "Edit name",
                    tint = MeshlitPulseViolet,
                )
            }
        }
        AnimatedVisibility(visible = showEditor) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(40) },
                    label = { Text("New display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { draft = displayName; showEditor = false },
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSaveDisplayName(draft.trim())
                            showEditor = false
                        },
                        enabled = draft.isNotBlank() && draft.trim() != displayName,
                    ) { Text("Save") }
                }
            }
        }
        KeyValueRow(label = "Local IP", value = localIp.ifBlank { "(offline)" })
        KeyValueRow(label = "HTTP port", value = httpPort.toString(), mono = true)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Pill(text = "Tier: $capabilityTierName", tint = MeshlitPulseViolet)
            Pill(text = "Build: V2", tint = MeshlitPulseAqua)
        }
    }
}

@Composable
private fun HardwareCard(
    hardware: com.meshlit.core.probe.HardwareCapability?,
    ageMs: Long,
    isReprobing: Boolean,
    isLive: Boolean,
    onReprobeHardware: () -> Unit,
    onToggleLive: () -> Unit,
    cpuSeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    memorySeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    thermalSeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    batterySeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    networkSeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    diskSeries: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
) {
    SectionCard(title = "Hardware") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Resource Monitor · 60s window · 1 Hz",
                style = MaterialTheme.typography.labelSmall,
                color = MeshlitTextTertiaryV2,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The "Live" pill pulses when the monitor is running
                // so the user gets a clear cue that values update
                // without them having to tap anything.
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isLive) MeshlitPulseViolet.copy(alpha = 0.18f)
                            else MeshlitSurfaceHigh,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isLive) MeshlitPulseViolet else MeshlitTextTertiaryV2,
                                    shape = RoundedCornerShape(50),
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isLive) "Live" else "Paused",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (isLive) MeshlitPulseViolet else MeshlitTextTertiaryV2,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onToggleLive,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp,
                    ),
                ) {
                    Text(
                        text = if (isLive) "Pause" else "Resume",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (hardware == null) {
            Text(
                text = "No capability snapshot yet — bootstrap is still running.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeshlitTextSecondaryV2,
            )
        } else {
            // Windows 11 Resource Monitor style: each metric is a
            // row with a Y-axis label, a tinted line chart, and a
            // bold "Current" value on the right. Gridlines mark
            // 25/50/75/100% so the user can read absolute values at
            // a glance.
            ResourceMonitorRow(
                label = "CPU",
                unit = "%",
                series = cpuSeries,
                currentPct = (hardware.cpu.score ?: 0f),
                currentRaw = hardware.cpu.rawValue.ifBlank { "—" },
                color = MeshlitPulseViolet,
            )
            ResourceMonitorRow(
                label = "Memory",
                unit = "%",
                series = memorySeries,
                currentPct = (hardware.memory.score ?: 0f),
                currentRaw = hardware.memory.rawValue.ifBlank { "—" },
                color = MeshlitPulseAqua,
                secondaryLine = hardware.totalRamMb?.let { "${it}MB total" },
            )
            ResourceMonitorRow(
                label = "Thermal",
                unit = "°C",
                series = thermalSeries,
                currentPct = (hardware.thermal.score ?: 0f),
                currentRaw = hardware.thermal.rawValue.ifBlank { "—" },
                color = MeshlitPulseCoral,
            )
            ResourceMonitorRow(
                label = "Battery",
                unit = "%",
                series = batterySeries,
                currentPct = (hardware.battery.score ?: 0f),
                currentRaw = hardware.battery.rawValue.ifBlank { "—" },
                color = MeshlitPulseViolet,
            )
            ResourceMonitorRow(
                label = "Network",
                unit = "KB/s",
                series = networkSeries,
                currentPct = (hardware.network.score ?: 0f),
                currentRaw = hardware.network.rawValue.ifBlank { "—" },
                color = MeshlitPulseAqua,
            )
            ResourceMonitorRow(
                label = "Disk",
                unit = "KB/s",
                series = diskSeries,
                currentPct = diskSeries.lastOrNull()?.value ?: 0f,
                currentRaw = diskSeries.lastOrNull()?.raw ?: "0 KB/s",
                color = MeshlitPulseCoral,
            )
            ScoreRow(label = "NPU", sample = hardware.npu, suffix = if (hardware.hasNpu) " · available" else " · unknown")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Staleness: ${formatAge(ageMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshlitTextTertiaryV2,
                )
                OutlinedButton(
                    onClick = onReprobeHardware,
                    enabled = !isReprobing,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp,
                    ),
                ) {
                    if (isReprobing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MeshlitPulseViolet,
                        )
                    } else {
                        Text(
                            text = "Re-probe",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Windows 11 Resource Monitor-style chart row.
 *
 * Layout:
 * ```
 * �───────────────────────────────────────┐
 * │ CPU  %                        80 %    │  ← label + unit + current
 * │ ┌───────────────────────────────────┐ │
 * │ │      .────────────────.          │ │  ← tinted line + fill
 * │ │   .─'                   '─.      │ │     with gridlines
 * │ │ .─                          '─.  │ │
 * │ └───────────────────────────────────┘ │
 * │ 60 s ago                       now    │  ← time axis
 * └───────────────────────────────────────┘
 * ```
 *
 * - `label` + `unit` form the top-left axis label.
 * - `currentPct` (0..1) drives the chart Y position + the right
 *   "current" badge. `currentRaw` is the human string for the
 *   badge (e.g. "35%" or "arm64-v8a" or "3542 / 4096MB").
 * - `secondaryLine` adds a small caption under the badge
 *   (used for memory total etc.).
 *
 * Designed to look like the Resmon tab in Windows 11's Task
 * Manager: dark glassmorphic surface, subtle gridlines, tinted
 * accent matching the metric family.
 */
@Composable
private fun ResourceMonitorRow(
    label: String,
    unit: String,
    series: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    currentPct: Float,
    currentRaw: String,
    color: androidx.compose.ui.graphics.Color,
    secondaryLine: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentRaw,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = color,
                )
                if (secondaryLine != null) {
                    Text(
                        text = secondaryLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshlitTextTertiaryV2,
                    )
                }
            }
        }
        ResourceMonitorChart(
            series = series,
            color = color,
            currentPct = currentPct.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(top = 6.dp),
        )
    }
}

/**
 * The chart itself: 4 horizontal gridlines (0/25/50/75/100),
 * a tinted line + translucent fill underneath, and a dot on
 * the latest sample. Renders nothing when the series is empty
 * (first frame after the screen opens).
 */
@Composable
private fun ResourceMonitorChart(
    series: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    color: androidx.compose.ui.graphics.Color,
    currentPct: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 4 gridlines at 25/50/75/100% — same look as Task Manager.
        val gridColor = androidx.compose.ui.graphics.Color(0x33FFFFFF)
        for (i in 1..4) {
            val y = h - (i / 4f) * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }
        if (series.size < 2) return@Canvas
        val n = series.size
        val stepX = w / (n - 1).coerceAtLeast(1).toFloat()
        val pts = series.mapIndexed { idx, p ->
            Offset(
                x = idx * stepX,
                y = h - (p.value.coerceIn(0f, 1f) * h),
            )
        }
        // Line path
        val path = Path()
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        // Fill underneath
        val fill = Path().apply {
            addPath(path)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(path = fill, color = color.copy(alpha = 0.18f))
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5f),
        )
        // Current-value dot
        drawCircle(color = color, radius = 4f, center = pts.last())
        // Vertical "now" line at the rightmost sample.
        drawLine(
            color = color.copy(alpha = 0.45f),
            start = Offset(pts.last().x, 0f),
            end = Offset(pts.last().x, h),
            strokeWidth = 1f,
        )
    }
}

/**
 * Minimal Compose sparkline for a `List<SamplePoint>`. Renders the
 * 0..1 normalised scores as a polyline across the row width with
 * a baseline at zero. Designed to live directly under each
 * [ScoreRow] in the Hardware card so the user sees the trend
 * (CPU/memory/thermal/battery/network) over the last ~60 s.
 *
 * Castro's sparkline is the visual analogue — a flat bar graph
 * with the latest value highlighted. We render a smoothed
 * `Path` so the trend reads as a line, not a histogram.
 */
@Composable
private fun Sparkline(
    series: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    color: androidx.compose.ui.graphics.Color,
) {
    if (series.size < 2) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val w = size.width
        val h = size.height
        val n = series.size
        val stepX = w / (n - 1).coerceAtLeast(1).toFloat()
        val path = androidx.compose.ui.graphics.Path()
        val pts = series.mapIndexed { idx, p ->
            androidx.compose.ui.geometry.Offset(
                x = idx * stepX,
                y = h - (p.value.coerceIn(0f, 1f) * h),
            )
        }
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        // Filled area under the line for a Castro-style "fill".
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(path)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(
            path = fill,
            color = color.copy(alpha = 0.18f),
        )
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
        )
        // Dot at the latest sample so the user can see where the
        // "now" point is.
        drawCircle(
            color = color,
            radius = 3.5f,
            center = pts.last(),
        )
    }
}

@Composable
private fun ScoreRow(
    label: String,
    sample: com.meshlit.core.probe.ProfileSample,
    suffix: String = "",
) {
    val pct = ((sample.score ?: 0f) * 100).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MeshlitTextSecondaryV2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (sample.score == null) "—"
                    else "${pct}% · ${sample.rawValue}$suffix",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MeshlitTextPrimaryV2,
            )
        }
    }
}

@Composable
private fun RoleCard(
    roleName: String,
    confidence: Float,
    scores: Map<com.meshlit.core.role.Role, Float>,
    reasons: List<String>,
    capability: com.meshlit.core.probe.HardwareCapability?,
) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(title = "Role") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current role",
                    style = MaterialTheme.typography.labelMedium,
                    color = MeshlitTextSecondaryV2,
                )
                Text(
                    text = roleName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
            }
            Pill(
                text = "${(confidence * 100).toInt()}% confidence",
                tint = roleTint(roleName),
            )
        }
        // Score chips — show the top 3 roles by score
        val sortedScores = scores.entries
            .sortedByDescending { it.value }
            .take(3)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sortedScores) { (role, score) ->
                Pill(
                    text = "${role.name} · ${(score * 100).toInt()}%",
                    tint = if (role.name == roleName) MeshlitPulseViolet
                        else MeshlitOutlineV2,
                )
            }
        }
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide reasons" else "Why this role?",
                    tint = MeshlitTextSecondaryV2,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Why this role?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MeshlitTextSecondaryV2,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val explainReasons = if (capability != null)
                    RolePolicy.explain(capability, com.meshlit.core.role.Role.valueOf(roleName))
                else reasons
                if (explainReasons.isEmpty()) {
                    Text(
                        text = "No specific reasons — role suggested by baseline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeshlitTextTertiaryV2,
                    )
                } else {
                    explainReasons.forEach { reason ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MeshlitPulseAqua,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeshlitTextPrimaryV2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootstrapCard(
    report: BootstrapReport,
    flags: Map<String, Boolean>,
    isRebootstrapping: Boolean,
    onForceRebootstrap: () -> Unit,
) {
    SectionCard(title = "Bootstrap health") {
        report.entries.forEach { entry ->
            KeyValueRow(
                label = entry.phase.name,
                value = "${entry.outcome.name} · ${entry.durationMs}ms",
            )
        }
        if (flags.isNotEmpty()) {
            Text(
                text = "Flags",
                style = MaterialTheme.typography.labelMedium,
                color = MeshlitTextSecondaryV2,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(flags.entries.toList()) { (name, value) ->
                    Pill(
                        text = if (value) "$name ✓" else "$name · off",
                        tint = if (value) MeshlitPulseAqua else MeshlitOutlineV2,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onForceRebootstrap,
            enabled = !isRebootstrapping,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRebootstrapping) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MeshlitPulseViolet,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MeshlitPulseViolet,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Force re-bootstrap")
            }
        }
    }
}

@Composable
private fun ServicesCard(services: List<ServiceDescriptor>) {
    SectionCard(title = "Registered services") {
        if (services.isEmpty()) {
            Text(
                text = "No services registered.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeshlitTextTertiaryV2,
            )
        } else {
            services.forEach { svc ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = svc.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MeshlitTextPrimaryV2,
                        )
                        Text(
                            text = "${svc.kind.name} · ${svc.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeshlitTextTertiaryV2,
                        )
                    }
                    Pill(
                        text = healthLabel(svc.health),
                        tint = healthTint(svc.health),
                    )
                }
            }
        }
    }
}

private fun healthLabel(state: HealthState): String = when (state) {
    HealthState.Healthy -> "healthy"
    is HealthState.Degraded -> "degraded · ${state.reason}"
    is HealthState.Unreachable -> "unreachable · ${state.reason}"
    HealthState.Unknown -> "unknown"
}

private fun healthTint(state: HealthState): Color = when (state) {
    HealthState.Healthy -> MeshlitPulseAqua
    is HealthState.Degraded -> MeshlitPulseViolet
    is HealthState.Unreachable -> MeshlitPulseCoral
    HealthState.Unknown -> MeshlitOutlineV2
}

private fun roleTint(roleName: String): Color = when (roleName) {
    "Brain" -> MeshlitPulseViolet
    "Tool" -> MeshlitPulseAqua
    "Monitor" -> MeshlitPulseCoral
    "Relay" -> MeshlitPulseCoral
    else -> MeshlitOutlineV2
}

private fun formatAge(ageMs: Long): String {
    if (ageMs == Long.MAX_VALUE) return "—"
    val s = ageMs / 1000
    return if (s < 60) "${s}s ago" else "${s / 60}m ago"
}
