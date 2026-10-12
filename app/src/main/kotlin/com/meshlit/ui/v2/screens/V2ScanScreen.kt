package com.meshlit.ui.v2.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.discovery.*
import com.meshlit.devices.PairingPayload
import com.meshlit.di.koinInject
import com.meshlit.disco.*
import com.meshlit.peering.DeviceIdentity
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*

@Composable
fun V2ScanScreen(onOpenQrPairing: () -> Unit = {}, onBack: () -> Unit = {}) {
    val repo: PeerRepository = koinInject(); val settings: SettingsRepository = koinInject()
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val state by repo.uiState.collectAsStateWithLifecycle()
    val ble by (repo.bleStatus ?: kotlinx.coroutines.flow.MutableStateFlow<TransportStatus>(TransportStatus.Unavailable("ble.not_registered")))
        .collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var own by remember { mutableStateOf<PairingPayload?>(null) }
    LaunchedEffect(app) {
        while (app.nodeIdHex.isBlank()) delay(100)
        own = withContext(Dispatchers.IO) { PairingPayload(app.displayName, "http://${app.localIpAddress}:${app.httpServerPort}",
            app.nodeIdHex, app.capabilityTier.name, koinInject<DeviceIdentity>().get(app.nodeIdHex).publicKey) }
    }
    LaunchedEffect(repo) { while (isActive) { repo.refreshAvailability(); delay(2000) } }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) { repo.refreshAvailability(); repo.setTransportEnabled("ble", true) }
        else error = "ble.permission_required: BLE unavailable until permission is granted"
    }
    fun restart() {
        val key = own ?: return
        repo.start(app.appScope, LocalPeerDescriptor(app.nodeIdHex, app.localIpAddress, app.httpServerPort,
            "local_sandboxed", com.meshlit.core.trust.PeerCrypto.fingerprint(key.publicKey)))
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Find your devices", style = MaterialTheme.typography.headlineLarge)
            Text(if (state.scanning) "Scanning · ${state.total} nearby or enrolled peers" else "Discovery paused") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (state.scanning) repo.stop() else restart() }, enabled = own != null) { Text(if (state.scanning) "Pause scan" else "Start scan") }
            OutlinedButton(onClick = { sheet = true }, enabled = own != null) { Text("QR / paste") }
        } }
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
        PeerCategory.entries.forEach { category ->
            item { Text(category.displayName, style = MaterialTheme.typography.titleLarge) }
            val peers = state.grouped[category].orEmpty()
            if (peers.isEmpty()) item { Text("No devices in this group", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(peers, key = { "${category.name}:${it.advertisement.nodeId}" }) { classified ->
                val peer = classified.advertisement
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(peer.nodeId.take(24), style = MaterialTheme.typography.titleMedium)
                    Text("${peer.host}:${peer.port} · ${peer.transport}")
                    Text("Discovered identity · approval required", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scope.launch {
                        try {
                            val host = if (peer.host.contains(':')) "[${peer.host}]" else peer.host
                            val payload = PairingPayload(peer.nodeId.take(16), "http://$host:${peer.port}", peer.nodeId, "")
                            settings.upsertEndpoint(payload.toRemoteEndpoint()); error = "Device saved. Scan its QR in Devices to verify its key."
                        } catch (e: Exception) { error = "pairing.invalid_endpoint: ${e.message}" }
                    } }) { Text("Save device") }
                } }
            }
        }
    }
    if (sheet && own != null) QrPairingSheet(own!!, { payload -> scope.launch {
        try { require(payload.nodeId != app.nodeIdHex) { "Cannot add this device to itself" }
            settings.upsertEndpoint(payload.toRemoteEndpoint()); repo.ingest(payload.toPeerAdvertisement()); sheet = false }
        catch (e: Exception) { error = "pairing.invalid_payload: ${e.message}" }
    } }, { sheet = false })
}
