package com.meshlit.ui.screens.cloud

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.sandbox.RuntimeHost
import com.meshlit.core.sandbox.VmState
import kotlinx.coroutines.*

@Composable
fun RuntimeDashboardCard() {
    val host: RuntimeHost = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(host.vm.state) }
    var allowed by remember { mutableStateOf(host.allowAgentVm()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(host) {
        while (isActive) { state = host.vm.state; delay(1000) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optional Linux runtime", style = MaterialTheme.typography.titleMedium)
            Text("Inference uses RunAnywhere on-device. A Linux VM starts only when requested.")
            val config = host.vmConfig()
            Text("VM: $state • ${config.memoryMb} MiB • ${config.cpus} CPU • 30-minute session limit")
            Text("Configure installed artifacts in Commands: vm help. Desktop requires a guest desktop image and a VNC viewer.")
            Text("Artifact preflight: "+runCatching { config.argv();"paths and limits accepted; actual boot/authentication still required" }.getOrElse { it.message ?: "unavailable" })
            Text("Disk: ${if(config.persistDisk) "persistent · Stop keeps writes" else "ephemeral · guest writes discarded"}. Outbound network: ${if(config.allowOutboundNetwork) "human enabled" else "restricted"}. Inbound ports stay loopback.")
            Text("APP shares app permissions; PRoot/chroot provide no strong arbitrary-code isolation. Agent execution uses only the configured guest. Missing Android-compatible QEMU/guest artifacts remain unavailable.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow agents to start, stop and run guest commands", Modifier.weight(1f))
                Switch(checked = allowed, onCheckedChange = { try { host.setAllowAgentVm(it); allowed = host.allowAgentVm() } catch(e:Exception) { allowed=host.allowAgentVm();message=e.message ?: "Permission could not be saved" } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && state !in listOf(VmState.RUNNING, VmState.SSH_READY, VmState.STARTING), onClick = {
                    busy = true
                    scope.launch {
                        try { host.startVm(); message = "VM launched; use vm wait to check SSH readiness" }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { message = error.message ?: "VM startup failed" }
                        finally { busy = false }
                    }
                }) { Text("Start VM") }
                OutlinedButton(enabled = !busy && state != VmState.STOPPED, onClick = {
                    scope.launch { host.stopVm(); state = host.vm.state; message = "VM stopped" }
                }) { Text("Stop VM") }
            }
            if (config.enableDesktop && state in listOf(VmState.RUNNING, VmState.SSH_READY)) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(host.vm.desktopUri())))
                    }.onFailure { message = "Install a VNC viewer to open the guest desktop" }
                }) { Text("Open desktop") }
            }
            (message ?: host.lastError)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
