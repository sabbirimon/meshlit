package com.meshlit.ui.screens.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
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
import androidx.core.content.ContextCompat
import com.meshlit.core.net.capture.MeshlitCaptureVpnService
import com.meshlit.core.net.capture.PacketCaptureRegistry
import com.meshlit.core.net.capture.PcapParser
import com.meshlit.network.pcapdroid.PcapdroidBridge
import com.meshlit.network.termux.TermuxBridge
import com.meshlit.ui.components.MeshlitHeader
import java.io.File

/**
 * Android-native network diagnostics surface. It deliberately
 * separates Meshlit's own HTTP event list from device-wide packet
 * capture: TLS payloads remain encrypted and the device capture is
 * opt-in behind the system VPN consent dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkMonitorScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var captureRunning by remember { mutableStateOf(false) }
    var packets by remember { mutableStateOf(PacketCaptureRegistry.snapshot()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pcapRecords by remember { mutableStateOf<List<PcapParser.Record>>(emptyList()) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            context.startService(
                Intent(context, MeshlitCaptureVpnService::class.java),
            )
            captureRunning = true
        }
    }

    LaunchedEffect(captureRunning) {
        if (captureRunning) {
            while (captureRunning) {
                packets = PacketCaptureRegistry.snapshot()
                kotlinx.coroutines.delay(500)
            }
        }
    }

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
            CaptureHeader(
                running = captureRunning,
                onStart = {
                    val intent = VpnService.prepare(context)
                    if (intent == null) {
                        context.startService(Intent(context, MeshlitCaptureVpnService::class.java))
                        captureRunning = true
                    } else {
                        vpnConsentLauncher.launch(intent)
                    }
                },
                onStop = {
                    context.startService(
                        Intent(context, MeshlitCaptureVpnService::class.java)
                            .setAction(MeshlitCaptureVpnService.ACTION_STOP),
                    )
                    captureRunning = false
                },
            )
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
                    onSelect = { file ->
                        selectedFile = file
                        val parsed = PcapParser().parse(file)
                        when (parsed) {
                            is PcapParser.Result.Ok -> {
                                pcapRecords = parsed.records
                                fileError = null
                            }
                            is PcapParser.Result.Invalid -> {
                                pcapRecords = emptyList()
                                fileError = parsed.reason
                            }
                        }
                    },
                )
                3 -> ExternalTools(context)
            }
        }
    }
}

@Composable
private fun CaptureHeader(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (running) "Capture running" else "Capture idle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (running) "Metadata is kept on this device and written to a .pcap file."
                        else "Meshlit HTTP is always listed separately; device packets require consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (running) {
                    OutlinedButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text("Start")
                    }
                }
            }
            Text(
                "TLS payloads are not decrypted. Use PCAPdroid or Termux for a full device capture when needed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
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
            body = "Start capture, then use a network-enabled feature. Packet metadata stays local.",
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
    onSelect: (File) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = copyToCache(context, uri)
            if (copied != null) onSelect(copied)
            else Toast.makeText(context, "Unable to read capture", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch("application/vnd.tcpdump.pcap") }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text("Open .pcap")
            }
            if (selectedFile != null) {
                Text(selectedFile.name, Modifier.align(Alignment.CenterVertically), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
private fun ExternalTools(context: Context) {
    val pcapInstalled = remember { PcapdroidBridge.isInstalled(context) }
    val termuxInstalled = remember { TermuxBridge.isInstalled(context) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("External capture tools", style = MaterialTheme.typography.titleLarge)
        Text(
            "These tools can capture traffic outside Meshlit's own HTTP observer. They are optional and remain under your control.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ToolCard(
            title = "PCAPdroid",
            body = if (pcapInstalled) "Installed — open its capture flow." else "Install PCAPdroid for a mature Android packet capture path.",
            installed = pcapInstalled,
            onClick = { if (pcapInstalled) PcapdroidBridge.startCapture(context) else PcapdroidBridge.openInstall(context) },
        )
        ToolCard(
            title = "Termux tcpdump",
            body = if (termuxInstalled) "Installed — run tcpdump and save to Downloads." else "Install Termux if you prefer command-line capture.",
            installed = termuxInstalled,
            onClick = { if (termuxInstalled) TermuxBridge.startCapture(context) else TermuxBridge.openInstall(context) },
        )
    }
}

@Composable
private fun ToolCard(title: String, body: String, installed: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.padding(2.dp))
                Text(if (installed) "Open" else "Install")
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

private fun copyToCache(context: Context, uri: android.net.Uri): File? = runCatching {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "import-${System.currentTimeMillis()}.pcap")
    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    file
}.getOrNull()
