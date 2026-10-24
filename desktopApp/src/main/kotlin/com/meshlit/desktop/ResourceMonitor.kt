package com.meshlit.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import oshi.SystemInfo
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane

internal data class ProcessReading(val pid: Int, val name: String, val user: String, val rss: Long,
    val cpu: Double?, val started: Instant?, val handle: ProcessHandle?)
internal data class ResourceReading(val at: Instant, val cpu: Double?, val total: Long, val available: Long,
    val processes: List<ProcessReading>, val processCount: Int, val batteries: List<String>, val gpus: List<String>)
internal class ResourceReader {
    private val info by lazy { SystemInfo() }
    private var ticks: LongArray? = null
    private var previous = emptyMap<Int, OSProcess>()
    fun sample(): ResourceReading {
        val cpu = info.hardware.processor
        val load = ticks?.let { cpu.getSystemCpuLoadBetweenTicks(it) }?.takeIf { it.isFinite() && it in 0.0..1.0 }
        ticks = cpu.systemCpuLoadTicks
        val os = info.operatingSystem
        val processes = os.getProcesses(null, OperatingSystem.ProcessSorting.RSS_DESC, 2048)
        val readings = processes.map { process ->
            val old = previous[process.processID]?.takeIf { it.startTime == process.startTime }
            val handle = runCatching { ProcessHandle.of(process.processID.toLong()).orElse(null) }.getOrNull()
            ProcessReading(process.processID, process.name.take(150), process.user.take(100), process.residentMemory,
                old?.let { process.getProcessCpuLoadBetweenTicks(it) / cpu.logicalProcessorCount }?.takeIf { it.isFinite() && it >= 0 },
                handle?.info()?.startInstant()?.orElse(null), handle)
        }
        previous = processes.associateBy { it.processID }
        return ResourceReading(Instant.now(), load, info.hardware.memory.total, info.hardware.memory.available,
            readings, os.processCount,
            runCatching { info.hardware.powerSources.map { power ->
                "${power.name}: ${"%.0f".format(power.remainingCapacityPercent * 100)}% · ${if (power.isCharging) "charging" else "not charging"} · " +
                    (if (power.powerUsageRate > 0 && power.powerUsageRate.isFinite()) "%.2f W reported".format(power.powerUsageRate / 1000) else "power sensor unavailable")
            } }.getOrDefault(emptyList()),
            runCatching { info.hardware.graphicsCards.map { "${it.name} · hardware-reported VRAM ${formatMemory(it.vRam)} · inference driver not qualified here" } }.getOrDefault(emptyList()))
    }
}
internal fun formatMemory(bytes: Long) = if (bytes < 0) "unknown" else "%.2f GiB".format(bytes / 1073741824.0)
internal fun canTerminate(row: ProcessReading): Boolean = row.pid > 1 && row.pid.toLong() != ProcessHandle.current().pid() &&
    row.user == System.getProperty("user.name") && row.started != null && row.handle?.isAlive == true
internal fun terminateProcess(row: ProcessReading, humanEnabled: AtomicBoolean): Boolean {
    check(humanEnabled.get() && canTerminate(row))
    val handle = checkNotNull(row.handle)
    check(handle.info().startInstant().orElse(null) == row.started) { "Process identity changed" }
    return handle.destroy() // Graceful only; the next real sample reports whether it exited.
}

@Composable internal fun ResourceMonitor(onStopModel: () -> Unit) {
    val reader = remember { ResourceReader() }
    var snapshot by remember { mutableStateOf<ResourceReading?>(null) }
    var history by remember { mutableStateOf(emptyList<ResourceReading>()) }
    var sampling by remember { mutableStateOf(true) }; var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }; var sort by remember { mutableStateOf("RAM") }
    val humanTermination = remember { AtomicBoolean(false) }; var allowTermination by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("") }
    LaunchedEffect(sampling) {
        while (sampling && isActive) {
            try {
                val value = withContext(Dispatchers.IO) { reader.sample() }
                snapshot = value; history = (history + value).takeLast(60); failed = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
            delay(2000)
        }
    }
    DisposableEffect(Unit) { onDispose { humanTermination.set(false) } }
    Text("Resource monitor & task manager", style = MaterialTheme.typography.h6)
    Row {
        TextButton({ sampling = !sampling }) { Text(if (sampling) "Pause live updates" else "Resume live updates") }
        TextButton(onStopModel) { Text("Stop Meshlit's model") }
    }
    Text("Refreshes every 2 seconds while open; stores at most 60 samples. Local OS readings, no telemetry. Restricted processes or sensors may be unavailable.", style = MaterialTheme.typography.caption)
    if (failed) Text("Reading failed; previous values are stale. OS permissions or native adapter may be unavailable.", color = MaterialTheme.colors.error)
    snapshot?.let { data ->
        Text("Measured at ${data.at}", style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Gauge("CPU", data.cpu, Modifier.weight(1f))
            Gauge("Physical memory", if (data.total > 0) 1 - data.available.toDouble() / data.total else null, Modifier.weight(1f))
        }
        Text("RAM available ${formatMemory(data.available)} / ${formatMemory(data.total)}")
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        Text("Java heap ${formatMemory(heap.used)} / ${formatMemory(heap.max)}; native model RSS appears in the process list.")
        HistoryChart("CPU history · %", history.map { it.cpu })
        HistoryChart("Physical memory used · %", history.map { if (it.total > 0) 1 - it.available.toDouble() / it.total else null })
        hostReadings().filter { it.first.startsWith("Home volume") || it.first.startsWith("Host") }.forEach { Text("${it.first}: ${it.second}") }
        Text("Power & hardware", style = MaterialTheme.typography.h6)
        data.batteries.ifEmpty { listOf("Battery/power sensors unavailable") }.forEach { Text(it) }
        data.gpus.ifEmpty { listOf("Graphics hardware inventory unavailable") }.forEach { Text(it) }
        Text("No pooled memory, remote health, electricity tariff or GPU load is inferred from these readings.", style = MaterialTheme.typography.caption)
        Divider()
        Text("Processes · ${data.processCount} reported · showing ${data.processes.size.coerceAtMost(2048)}", style = MaterialTheme.typography.h6)
        OutlinedTextField(query, { query = it.take(200) }, label = { Text("Find process, user or PID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row { listOf("RAM", "CPU", "Name").forEach { item -> TextButton({ sort = item }) { Text((if (sort == item) "✓ " else "") + item) } } }
        Row { Checkbox(allowTermination, { allowTermination = it; humanTermination.set(it) }); Text("Allow me to stop my own processes · ask each time") }
        if (action.isNotBlank()) Text(action)
        val ordered = data.processes.filter { "${it.name} ${it.user} ${it.pid}".contains(query, true) }.let { rows ->
            when (sort) { "CPU" -> rows.sortedByDescending { it.cpu ?: -1.0 }; "Name" -> rows.sortedBy { it.name.lowercase() }; else -> rows.sortedByDescending { it.rss } }
        }
        // The enclosing scroll area remains bounded; only the first 200 matches render.
        Text("Showing ${ordered.size.coerceAtMost(200)} of ${ordered.size} matches. Narrow search for the rest.", style = MaterialTheme.typography.caption)
        ordered.take(200).forEach { row -> Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${row.name} · PID ${row.pid} · ${row.user}")
                    Text("RSS ${formatMemory(row.rss)} · CPU ${row.cpu?.let { "%.1f%%".format(it * 100) } ?: "sampling"}", style = MaterialTheme.typography.caption)
                }
                TextButton({
                    if (JOptionPane.showConfirmDialog(null, "Stop ${row.name} (PID ${row.pid})? Unsaved work may be lost.", "Stop process", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        action = runCatching { if (terminateProcess(row, humanTermination)) "Stop requested; check the next live sample for exit." else "OS rejected the stop request." }.getOrElse { "Stop blocked: permission, scope or process identity changed." }
                    }
                }, enabled = allowTermination && canTerminate(row)) { Text("Stop") }
            }
        } }
    } ?: Text("Reading hardware and processes…")
}

@Composable private fun Gauge(label: String, fraction: Double?, modifier: Modifier) {
    val color = MaterialTheme.colors.primary; val track = MaterialTheme.colors.onSurface.copy(alpha = .1f)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width.coerceAtMost(size.height); val offset = Offset((size.width - width) / 2, 0f)
            drawArc(track, 140f, 260f, false, topLeft = offset, size = androidx.compose.ui.geometry.Size(width, width), style = Stroke(10f))
            fraction?.let { drawArc(color, 140f, (260 * it.coerceIn(0.0, 1.0)).toFloat(), false, topLeft = offset, size = androidx.compose.ui.geometry.Size(width, width), style = Stroke(10f)) }
        }
        Text("$label · ${fraction?.let { "%.1f%%".format(it * 100) } ?: "sampling / unavailable"}")
    }
}
@Composable private fun HistoryChart(label: String, samples: List<Double?>) {
    val color = MaterialTheme.colors.primary; val grid = MaterialTheme.colors.onSurface.copy(alpha = .12f)
    Text(label, style = MaterialTheme.typography.caption)
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        (0..4).forEach { i -> val y = size.height * i / 4; drawLine(grid, Offset(0f, y), Offset(size.width, y)) }
        if (samples.size > 1) {
            val path = Path(); var connected = false
            samples.forEachIndexed { i, value ->
                if (value == null) connected = false else {
                    val x = size.width * i / (samples.size - 1); val y = (size.height * (1 - value.coerceIn(0.0, 1.0))).toFloat()
                    if (!connected) path.moveTo(x, y) else path.lineTo(x, y); connected = true
                }
            }
            drawPath(path, color, style = Stroke(2.5f))
        }
    }
}
