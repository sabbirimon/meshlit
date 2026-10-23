package com.meshlit.ui.v2.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.discovery.*
import com.meshlit.devices.PairingPayload
import com.meshlit.di.koinInject
import com.meshlit.disco.*
import com.meshlit.peering.DeviceIdentity
import kotlinx.coroutines.*

@Composable
fun V2ScanScreen(onOpenQrPairing: () -> Unit = {}, onBack: () -> Unit = {}) {
    val repo: PeerRepository = koinInject()
    val discoveryMode=koinInject<DiscoveryModeStore>()
    val mode by discoveryMode.mode.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val state by repo.uiState.collectAsStateWithLifecycle()
    val ble by (repo.bleStatus ?: kotlinx.coroutines.flow.MutableStateFlow<TransportStatus>(TransportStatus.Unavailable("ble.not_registered")))
        .collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("All") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var own by remember { mutableStateOf<PairingPayload?>(null) }
    LaunchedEffect(app) {
        while (app.nodeIdHex.isBlank()) delay(100)
        while (app.localIpAddress.isBlank()) delay(2_000)
        own = withContext(Dispatchers.IO) { PairingPayload(app.displayName, com.meshlit.devices.endpointUrl(app.localIpAddress, app.httpServerPort),
            app.nodeIdHex, app.capabilityTier.name, koinInject<DeviceIdentity>().get(app.nodeIdHex).publicKey) }
    }
    LaunchedEffect(repo) { while (isActive) { repo.refreshAvailability(); delay(2000) } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) { repo.refreshAvailability(); repo.setTransportEnabled("ble", true) }
        else error = "ble.permission_required: BLE unavailable until permission is granted"
    }
    fun restart() {
        if (mode == DiscoveryMode.OFF) { error="Discovery is OFF in Security & privacy";return }
        val key = own ?: return
        repo.start(app.appScope, LocalPeerDescriptor(app.nodeIdHex, app.localIpAddress, app.httpServerPort,
            "local_sandboxed", com.meshlit.core.trust.PeerCrypto.fingerprint(key.publicKey), app.displayName,
            android.os.Build.MANUFACTURER.orEmpty(), android.os.Build.MODEL.orEmpty()))
    }
    val allPeers = state.grouped.values.flatten().groupBy { it.advertisement.nodeId }.values.map { copies ->
        copies.maxBy { item ->
            val adv = item.advertisement
            (if (adv.displayName.isNotBlank()) 4 else 0) +
                (if (adv.manufacturer.isNotBlank()) 2 else 0) +
                (if (adv.model.isNotBlank()) 2 else 0) +
                (if (adv.transport == "nsd") 1 else 0)
        }
    }
        .filter { it.advertisement.nodeId != app.nodeIdHex }
        .sortedWith(compareBy({ it.category.ordinal }, { it.advertisement.displayName.lowercase() }))
    val visiblePeers = allPeers.filter { classified ->
        val peer = classified.advertisement
        (categoryFilter == "All" || classified.category.name == categoryFilter) &&
            (search.isBlank() || listOf(peer.displayName, peer.manufacturer, peer.model, peer.host, peer.nodeId)
                .any { it.contains(search.trim(), ignoreCase = true) })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← Devices") }
            Text("Device list", style = MaterialTheme.typography.headlineLarge)
            Text("${allPeers.size} Meshlit ${if (allPeers.size == 1) "device" else "devices"} discovered · ${if (state.scanning) "Scanning" else "Paused"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Names and models are claimed by each device. Verify its QR before approval.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (state.scanning) repo.stop() else restart() }, modifier = Modifier.fillMaxWidth(), enabled = own != null && mode != DiscoveryMode.OFF) { Text(if (state.scanning) "Pause scan" else "Refresh scan") }
            OutlinedButton(onClick = onOpenQrPairing, modifier = Modifier.fillMaxWidth(), enabled = own != null) { Text("Pair by QR") }
        } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
            label = { Text("Search name, model, IP or ID") }, singleLine = true) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "LOCAL", "CLUSTER", "GROUP", "INTERNET").forEach { choice ->
                    FilterChip(selected = categoryFilter == choice, onClick = { categoryFilter = choice },
                        label = { Text(when (choice) { "LOCAL" -> "Local"; "CLUSTER" -> "Cluster";
                            "GROUP" -> "Group/VPN"; "INTERNET" -> "Remote"; else -> "All" }) })
                }
            }
        }
        item { Text("${visiblePeers.size} shown", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (visiblePeers.isEmpty()) item { Card(Modifier.fillMaxWidth()) {
            Text(if (search.isNotBlank() || categoryFilter != "All") "No devices match this filter."
                else "No Meshlit devices found. Keep both apps open on the same network, or pair by QR.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        items(visiblePeers, key = { it.advertisement.nodeId }) { classified ->
            val peer = classified.advertisement
            val expanded = expandedId == peer.nodeId
            Card(Modifier.fillMaxWidth().clickable { expandedId = if (expanded) null else peer.nodeId },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium) {
                            Text(if (peer.manufacturer.isNotBlank()) "▣" else "◇", Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(peer.displayName.ifBlank { "Device ${peer.nodeId.take(8)}" },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(peer.manufacturer, peer.model).filter(String::isNotBlank)
                                .joinToString(" · ").ifBlank { "Meshlit node" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(peer.host, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("Discovered · ${classified.category.displayName} · ${peer.transport.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Address: ${peer.host}:${peer.port}")
                        Text("Claimed device ID: ${peer.nodeId}", style = MaterialTheme.typography.bodySmall)
                        Text("Claimed fingerprint: ${peer.fingerprint}", style = MaterialTheme.typography.bodySmall)
                        Text("Advertisement only. Identity and reachability are unverified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onOpenQrPairing) { Text("Pair with owner's QR") }
                    }
                }
            }
        }
        item { HorizontalDivider(); Text("Discovery channels", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary) }
        if(mode==DiscoveryMode.OFF) item { Text("Discovery is off. Change it in Settings → Security & privacy. QR import remains available for manually shared pairing details.") }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Wi-Fi discovery", Modifier.weight(1f)); Switch("nsd" in state.transportsEnabled, { repo.setTransportEnabled("nsd", it) })
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Bluetooth discovery", Modifier.weight(1f)); Switch("ble" in state.transportsEnabled, { enabled ->
                if (enabled) permissions.launch(if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                else repo.setTransportEnabled("ble", false)
            })
        }
            Text(when (val status = ble) {
                TransportStatus.Active -> "BLE scanning"
                TransportStatus.Starting -> "Starting BLE…"
                TransportStatus.Stopped -> "BLE paused"
                is TransportStatus.MissingPermission -> "BLE unavailable · Permission required: ${status.permission.substringAfterLast('.')}"
                is TransportStatus.Error -> "BLE unavailable · ${status.message} (${status.code})"
                is TransportStatus.Unavailable -> "BLE unavailable · ${status.reason}"
            }, color = if (ble is TransportStatus.Unavailable || ble is TransportStatus.MissingPermission || ble is TransportStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}
