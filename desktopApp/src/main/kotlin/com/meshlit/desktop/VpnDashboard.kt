package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.*
import kotlinx.coroutines.*
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path

@Composable internal fun VpnDashboard(onHosts: () -> Unit, onAccess: () -> Unit) {
    val preferences = remember { VpnPreferences() }
    var enabled by remember { mutableStateOf(preferences.enabled()) }
    var executable by remember { mutableStateOf(preferences.path().ifBlank { TailscaleProbe.candidate() }) }
    var advanced by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<TailscaleSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var generation by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun clear() { generation++; job?.cancel(); job = null; snapshot = null; busy = false }
    fun save(value: Boolean, path: String) {
        clear()
        try { preferences.save(value, path); enabled = value; executable = path; error = null }
        catch(e: Exception) { error = "Cannot save VPN preferences" }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    LaunchedEffect(snapshot) { while(snapshot != null) { now = System.currentTimeMillis(); delay(5_000) } }
    fun openSetup() {
        clear()
        try { Desktop.getDesktop().browse(URI("https://tailscale.com/download")); error = null }
        catch(e: Exception) { error = "Open https://tailscale.com/download in your browser" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VPN & network", style = MaterialTheme.typography.h5)
        Text("Optional Tailscale companion", style = MaterialTheme.typography.subtitle1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(enabled, { save(it, executable) }); Text("Allow local Tailscale status reads")
        }
        Text("Refresh runs only the selected local CLI's status command. No login keys are requested and no network scan is performed. Connection and exit-node changes stay in Tailscale.", style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled && executable.isNotBlank() && !busy, onClick = {
                clear(); error = null; busy = true
                val epoch = generation; val selected = executable
                job = scope.launch {
                    try { val result = TailscaleProbe.status(selected); if(generation == epoch && enabled) { snapshot = result; now = System.currentTimeMillis() } }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { if(generation == epoch) error = "Status unavailable: check the installed CLI, daemon, login and permissions. Invalid, oversized and timed-out responses are rejected." }
                    finally { if(generation == epoch) busy = false }
                }
            }) { Text(if(busy) "Reading status…" else "Refresh status") }
            if(busy) TextButton(onClick = { clear() }) { Text("Cancel") }
            TextButton(onClick = ::openSetup) { Text("Tailscale setup") }
        }
        if(System.getProperty("os.name").startsWith("Mac") && java.nio.file.Files.isDirectory(Path.of("/Applications/Tailscale.app"))) {
            OutlinedButton(onClick = {
                clear()
                try { Desktop.getDesktop().open(Path.of("/Applications/Tailscale.app").toFile()); error = null }
                catch(e: Exception) { error = "Open the installed Tailscale client from Applications" }
            }) { Text("Open Tailscale client") }
        }
        if(!enabled) Text("Observation is off. Your VPN configuration is unchanged.")
        else if(executable.isBlank()) Text("Tailscale CLI not found in standard locations. Install it separately, or select your existing executable in Advanced.")
        else if(snapshot == null && !busy && error == null) Text("No observation yet. Refresh to read your installed client.")
        error?.let { Text(it, color = MaterialTheme.colors.error) }
        snapshot?.let { s ->
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(vpnSnapshotIsFresh(s.observedAt, now)) s.summary else "Snapshot expired · refresh status", style = MaterialTheme.typography.h6)
                Text("Observed ${java.time.Instant.ofEpochMilli(s.observedAt)} · local CLI", style = MaterialTheme.typography.caption)
                Text("Self control-plane presence: ${s.self?.online?.let { if(it) "online" else "offline" } ?: "unknown"}")
                Text("Assigned addresses: ${s.addresses.takeIf { it.isNotEmpty() }?.joinToString() ?: "not reported"}")
                Text("Exit node: ${s.exitNode?.let { "selected · ${it.online?.let { online -> if(online) "online" else "offline" } ?: "presence unknown"}" } ?: "none reported"}")
                Text("Peers: ${s.peers?.size ?: "unknown"} · control-plane online: ${s.peers?.count { it.online == true } ?: "unknown"}")
                Text("Reported health issues: ${s.healthIssues ?: "unknown"}. Open Tailscale for details.")
                Text("Daemon status and peer presence do not prove a model service is reachable or all internet traffic uses this VPN.", style = MaterialTheme.typography.caption)
            } }
        }
        OutlinedButton(onClick = onHosts) { Text("Test an approved model host") }
        TextButton(onClick = onAccess) { Text("Review Meshlit host access") }
        TextButton(onClick = { advanced = !advanced }) { Text(if(advanced) "Hide advanced details" else "Advanced details") }
        if(advanced) {
            Text("Selected CLI: ${executable.ifBlank { "none" }}", style = MaterialTheme.typography.caption)
            OutlinedButton(onClick = {
                val picker = javax.swing.JFileChooser().apply { dialogTitle = "Choose your installed Tailscale executable"; isAcceptAllFileFilterUsed = true }
                if(picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) save(false, picker.selectedFile.absolutePath)
            }) { Text("Choose installed CLI") }
            Text("Selecting a new executable turns observation off. Enable it only for a trusted Tailscale installation. Only status --json is invoked, without a shell, with a five-second deadline.", style = MaterialTheme.typography.caption)
            snapshot?.let { s ->
                Text("OS tunnel mode reported: ${s.kernelTunnel?.let { if(it) "kernel tunnel" else "userspace" } ?: "unknown"}")
                s.peers?.take(50)?.forEach { peer ->
                    Text("${peer.name ?: "Name not reported"} · ${peer.online?.let { if(it) "online" else "offline" } ?: "unknown"}\n${peer.addresses.joinToString().ifBlank { "No address reported" }}", style = MaterialTheme.typography.caption)
                }
                if((s.peers?.size ?: 0) > 50) Text("First 50 peers shown; open Tailscale for the full list.")
            }
        }
        Text("VPN membership does not grant Meshlit authentication, worker enrollment or agent permissions. Observations stay in memory; only your opt-in and selected CLI path are saved.", style = MaterialTheme.typography.caption)
    }
}
