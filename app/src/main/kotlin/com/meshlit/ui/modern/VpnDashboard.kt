package com.meshlit.ui.modern

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meshlit.network.AndroidVpnObserver
import com.meshlit.workspace.*
import kotlinx.coroutines.*

@Composable internal fun VpnDashboard(navigate: (String) -> Unit) {
    val context = LocalContext.current
    val observer = remember { AndroidVpnObserver(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(observer.enabled()) }
    var snapshot by remember { mutableStateOf<VpnNetworkObservation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun refresh() {
        snapshot = null
        if(!enabled || !foreground) return
        try { snapshot = observer.read(); now = System.currentTimeMillis(); error = null }
        catch(e: Exception) { error = "Network observation unavailable; check Android access and refresh" }
    }
    DisposableEffect(lifecycle) {
        val listener = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) foreground = true
            if(event == Lifecycle.Event.ON_PAUSE) { foreground = false; snapshot = null }
        }
        lifecycle.addObserver(listener)
        onDispose { lifecycle.removeObserver(listener) }
    }
    LaunchedEffect(enabled, foreground) { if(enabled && foreground) refresh() else snapshot = null }
    LaunchedEffect(snapshot, foreground) { while(snapshot != null && foreground) { now = System.currentTimeMillis(); delay(5_000) } }
    fun open(intent: Intent?) {
        snapshot = null
        try { checkNotNull(intent); context.startActivity(intent); error = null }
        catch(e: Exception) { error = "This companion or settings screen is unavailable. Install/open Tailscale separately." }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VPN & network", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(enabled, { value ->
                try { observer.setEnabled(value); enabled = value; snapshot = null; error = null }
                catch(e: Exception) { error = "Cannot save observation preference" }
            }); Text("Show this app's network status")
        }
        Text("Optional, on-device observations. No account, scan, background polling or VPN configuration is added.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled && foreground, onClick = ::refresh) { Text("Refresh status") }
            OutlinedButton(onClick = { open(runCatching { observer.launchIntent() }.getOrNull()) }) { Text("Open Tailscale") }
        }
        TextButton(onClick = { open(Intent(Settings.ACTION_VPN_SETTINGS)) }) { Text("Android VPN settings") }
        if(!enabled) Text("Observation is off. Connect or disconnect in your VPN client.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        snapshot?.let { s ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(vpnSnapshotIsFresh(s.observedAt, now)) s.summary else "Snapshot expired · refresh status", style = MaterialTheme.typography.titleMedium)
                Text("Observed ${java.time.Instant.ofEpochMilli(s.observedAt)} · Android ConnectivityManager", style = MaterialTheme.typography.labelSmall)
                Text("Tailscale app: ${s.tailscaleInstalled?.let { if(it) "installed" else "not detected" } ?: "unknown"}")
                Text("Android internet validation: ${s.validated?.let { if(it) "validated" else "not validated" } ?: "unknown"}")
                Text("Metered network: ${s.metered?.let { if(it) "yes" else "no" } ?: "unknown"}")
                Text(s.provider, style = MaterialTheme.typography.bodySmall)
            } }
        }
        if(snapshot?.tailscaleInstalled != true) TextButton(onClick = { open(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://tailscale.com/download/android"))) }) { Text("Official Tailscale setup") }
        Text("An installed Tailscale app does not prove it is connected. Split tunneling can exclude Meshlit. Tailnet peers, login state and exit-node selection are available in the Tailscale client.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { navigate("external-services") }) { Text("Test an approved model service") }
        TextButton(onClick = { advanced = !advanced }) { Text(if(advanced) "Hide advanced details" else "Advanced details") }
        if(advanced) {
            snapshot?.let { s ->
                Text("Active interface: ${s.interfaceName ?: "unknown"}")
                Text("Assigned addresses: ${s.addresses.joinToString().ifBlank { "not reported" }}")
            }
            TextButton(onClick = { navigate("network") }) { Text("Network and pairing") }
            TextButton(onClick = { navigate("device-dashboard") }) { Text("Saved devices and access") }
            Text("Snapshots describe only the network Android exposes to this app at observation time, not every app or a guarantee of traffic routing. VPN membership does not enroll workers or grant Meshlit permissions.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
