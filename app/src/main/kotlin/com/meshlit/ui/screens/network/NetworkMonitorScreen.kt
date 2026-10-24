package com.meshlit.ui.screens.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.core.net.capture.PacketCaptureRegistry
import com.meshlit.core.net.capture.PcapParser
import com.meshlit.network.pcapdroid.PcapdroidBridge
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException

/**
 * Android-native network diagnostics surface. It deliberately
 * separates Meshlit's own HTTP event list from device-wide packet
 * capture. Built-in VPN capture is disabled until packet forwarding is proven;
 * the external companion retains its own approval and Android VPN consent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onOpenTermuxIntegration: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var packets by remember { mutableStateOf(PacketCaptureRegistry.snapshot()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pcapRecords by remember { mutableStateOf<List<PcapParser.Record>>(emptyList()) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val previewFile = selectedFile
    DisposableEffect(previewFile) { onDispose { previewFile?.delete() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        PacketCaptureRegistry.clear()
                        packets = emptyList()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear packet list")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (com.meshlit.BuildConfig.PLAY_REVIEW) {
                Text("Device-wide VPN capture is unavailable in the Play review build.", Modifier.padding(16.dp))
            } else Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mobile packet analysis", style = MaterialTheme.typography.titleMedium)
                    Text("Use the optional PCAPdroid companion for capture, then open its PCAP file here or in desktop Wireshark. Built-in VPN capture is unavailable.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { selectedTab = 3 }) { Text("Open capture tools") }
                }
            }
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Meshlit HTTP", "Device packets", "External capture", "Tools").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            when (selectedTab) {
                0 -> HttpEmptyState()
                1 -> PacketList(packets)
                2 -> ExternalCapture(
                    context = context,
                    selectedFile = selectedFile,
                    records = pcapRecords,
                    error = fileError,
                    onFailure = { fileError = it },
                    onSelect = { file, parsed ->
                        selectedFile?.delete()
                        selectedFile = file
                        when (parsed) {
                            is PcapParser.Result.Ok -> { pcapRecords = parsed.records; fileError = null }
                            is PcapParser.Result.Invalid -> { pcapRecords = emptyList(); fileError = parsed.reason }
                        }
                    },
                )
                3 -> ExternalTools(context = context, onOpenTermuxIntegration = onOpenTermuxIntegration)
            }
        }
    }
}

@Composable
private fun HttpEmptyState() {
    EmptyState(
        title = "Meshlit HTTP",
        body = "OkHttp requests appear here when the network observer is attached to a Meshlit client.",
    )
}

@Composable
private fun PacketList(packets: List<PacketCaptureRegistry.Entry>) {
    if (packets.isEmpty()) {
        EmptyState(
            title = "No device packets yet",
            body = "No live packet source is connected. Import a PCAP file or open capture tools.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(packets.reversed()) { packet ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${packet.transport} ${packet.srcPort} → ${packet.dstPort}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("${packet.src} → ${packet.dst}", style = MaterialTheme.typography.bodyMedium)
                    Text("${packet.payloadLength} payload bytes", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ExternalCapture(
    context: Context,
    selectedFile: File?,
    records: List<PcapParser.Record>,
    error: String?,
    onSelect: (File, PcapParser.Result) -> Unit,
    onFailure: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !reading) scope.launch {
            reading = true
            var imported: File? = null
            var published = false
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val file = copyToCache(context, uri)
                    imported = file
                    PcapParser().parse(file)
                }
                onSelect(checkNotNull(imported), parsed)
                published = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onFailure(error.message ?: "Unable to read capture") }
            finally { if (!published) imported?.delete(); reading = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !reading, onClick = { picker.launch("*/*") }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text(if (reading) "Reading capture…" else "Open .pcap")
            }
            if (selectedFile != null) {
                Text(selectedFile.name, Modifier.align(Alignment.CenterVertically), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Classic PCAP preview · up to 16 MiB and 10,000 packets. Use desktop Wireshark for larger files or detailed protocol analysis.", style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (records.isEmpty() && error == null) {
            EmptyState(title = "No capture selected", body = "Open a .pcap exported by Meshlit, PCAPdroid, Termux, or tcpdump.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Text("${record.timestampMs} — ${record.data.size} bytes", Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalTools(
    context: Context,
    onOpenTermuxIntegration: () -> Unit,
) {
    var pcapInstalled by remember { mutableStateOf(PcapdroidBridge.isInstalled(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) pcapInstalled = PcapdroidBridge.isInstalled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Toast.makeText(context, if (result.resultCode == Activity.RESULT_OK)
            "PCAPdroid accepted the request. Review capture status and saved files in PCAPdroid."
            else "PCAPdroid request was cancelled or denied.", Toast.LENGTH_LONG).show()
    }
    fun requestCapture(start: Boolean) {
        try { captureLauncher.launch(PcapdroidBridge.captureIntent(context, start)) }
        catch (_: Exception) { Toast.makeText(context, "PCAPdroid control is unavailable. Open or update the companion app.", Toast.LENGTH_LONG).show() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("External capture tools", style = MaterialTheme.typography.titleLarge)
        Text(
            "These tools can capture traffic outside Meshlit's own HTTP observer. They are optional and remain under your control.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!com.meshlit.BuildConfig.PLAY_REVIEW) {
            ToolCard(
                title = "PCAPdroid",
                body = if (pcapInstalled) "Installed companion. Capture requests need its approval; files stay on this device."
                    else "Get the optional Android companion for rootless packet capture and Wireshark-compatible exports.",
                installed = pcapInstalled,
                onClick = {
                    val opened = if (pcapInstalled) PcapdroidBridge.openApp(context) else PcapdroidBridge.openInstall(context)
                    if (!opened) Toast.makeText(context, "No compatible app or store could be opened.", Toast.LENGTH_LONG).show()
                },
            )
            if (pcapInstalled) {
                Text("Capture Meshlit traffic only. Root and TLS decryption are off. Export is capped at 16 MiB.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestCapture(true) }) { Text("Request capture") }
                    OutlinedButton(onClick = { requestCapture(false) }) { Text("Request stop") }
                }
            }
        } else Text("Companion capture controls are unavailable in Play Review. You can still open a user-selected classic PCAP file.")
        OutlinedButton(onClick = {
            if (!PcapdroidBridge.openPage(context, "https://emanuele-f.github.io/PCAPdroid/quick_start#14-packet-analysis"))
                Toast.makeText(context, "No browser could open the guide.", Toast.LENGTH_LONG).show()
        }) { Text("Wireshark workflow guide") }
        // The legacy Termux tile here used to call `TermuxBridge.startCapture`,
        // which is gone — Termux support now lives behind a full
        // Settings → Integrations → Termux screen (with probe, allowlist,
        // audit, and approval). We deep-link into that screen instead
        // so users land on the real, working flow rather than a fake
        // one-button shortcut.
        if (!com.meshlit.BuildConfig.PLAY_REVIEW) ToolCard(
            title = "Termux",
            body = "Optional external shell integration. Open Settings → Integrations → Termux for setup, the agent tool, and the audit trail.",
            installed = false,
            actionLabel = "Set up",
            onClick = onOpenTermuxIntegration,
        )
    }
}

@Composable
private fun ToolCard(title: String, body: String, installed: Boolean, actionLabel: String = if (installed) "Open" else "Get", onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun copyToCache(context: Context, uri: android.net.Uri): File {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File.createTempFile("import-", ".pcap", dir)
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open the selected file")
        input.use { source -> file.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= PcapParser.MAX_FILE_BYTES) { "Capture exceeds 16 MiB; use desktop Wireshark for larger files" }
                output.write(buffer, 0, count)
            }
        } }
        return file
    } catch (error: Exception) { file.delete(); throw error }
}
