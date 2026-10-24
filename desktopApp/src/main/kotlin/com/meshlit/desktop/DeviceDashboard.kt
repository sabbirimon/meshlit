package com.meshlit.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.time.Instant
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** A saved address is never presented as a currently connected or approved worker. */
@Composable internal fun DeviceDashboardPanel(onNodes: () -> Unit, onMonitor: () -> Unit) {
    val store = remember { NodeStore() }
    var snapshot by remember { mutableStateOf(runCatching { store.load() }) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val nodes = snapshot.getOrDefault(emptyList())
    val categories = listOf("All") + nodes.map { it.category.label }.distinct().sorted()
    val visibleNodes = nodes.filter { node ->
        (category == "All" || node.category.label == category) &&
            (query.isBlank() || listOf(node.name, node.endpoint, node.category.label)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    Text("Device and cluster dashboard", style = MaterialTheme.typography.h6)
    Text("${nodes.size} saved addresses · ${nodes.count { it.lastResult == "ok" }} previously reached · current remote online status unknown")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ snapshot = runCatching { store.load() } }) { Text("Refresh saved devices") }
        TextButton(onNodes) { Text("Manage and test") }
        TextButton(onMonitor) { Text("Local resources") }
    }
    if (snapshot.isFailure) Text("Saved device list is unavailable; check local storage.", color = MaterialTheme.colors.error)
    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Search saved device, type or address") }, singleLine = true)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { choice ->
            OutlinedButton(onClick = { category = choice }, enabled = category != choice) { Text(choice) }
        }
    }
    Text("${visibleNodes.size} shown · saved endpoints only", style = MaterialTheme.typography.caption)
    visibleNodes.forEach { node ->
        Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${node.name} · ${node.category.label}")
                Text(node.endpoint, style = MaterialTheme.typography.caption)
                Text("Last check: ${node.lastResult ?: "never"} · ${node.lastConnectedAtMs?.let { Instant.ofEpochMilli(it).toString() } ?: "no verified contact"}", style = MaterialTheme.typography.caption)
                Text("Live load, uptime, capacity, worker approval and cluster membership: unavailable from this saved address", style = MaterialTheme.typography.caption)
            }
        }
    }
    if (nodes.isEmpty()) Text("Add a device or cluster HTTPS inference endpoint in Nodes, then test and authenticate it.")
    else if (visibleNodes.isEmpty()) Text("No saved devices match this search or type.")
    Divider()
    StudioPairingCodesPanel()
    Divider()
    Text("This desktop build can select a saved host for inference. It does not enroll workers, split model layers, discover nearby devices, or transport multi-device chat.", style = MaterialTheme.typography.caption)
}

@Composable private fun StudioPairingCodesPanel() {
    val store=remember { DesktopPairingCodeStore() }
    val clipboard=LocalClipboardManager.current
    var codes by remember { mutableStateOf(runCatching { store.load() }.getOrDefault(emptyList())) }
    var pasted by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DesktopPairingCode?>(null) }
    fun addLink(link:String) {
        runCatching {
            val parsed=DesktopPairingLinks.parse(link)
            val next=(codes.filterNot { it.link==parsed.link }+parsed)
            store.save(next);codes=next;pasted=""
        }.onSuccess { notice="Pairing QR saved; no device access was approved" }
            .onFailure { notice="Cannot save QR: ${it.message}" }
    }
    Text("Pairing QRs · ${codes.size}",style=MaterialTheme.typography.h6)
    Text("Save, copy, export or remove phone pairing codes. Codes do not enroll a worker or approve compute.",style=MaterialTheme.typography.caption)
    OutlinedTextField(pasted,{pasted=it.take(8192)},modifier=Modifier.fillMaxWidth(),label={Text("Paste meshlit://pair link")},maxLines=4)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(onClick={addLink(pasted)},enabled=pasted.isNotBlank()){Text("Save code")}
        OutlinedButton(onClick={
            val chooser=JFileChooser().apply { fileFilter=FileNameExtensionFilter("PNG or JPEG QR image","png","jpg","jpeg") }
            if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)
                runCatching { DesktopPairingLinks.decodeImage(chooser.selectedFile.toPath()) }
                    .onSuccess(::addLink).onFailure { notice="Cannot read QR image: ${it.message}" }
        }){Text("Import QR image")}
    }
    notice?.let { Text(it,color=if(it.startsWith("Cannot")) MaterialTheme.colors.error else MaterialTheme.colors.primary) }
    listOf(false,true).forEach { temporary ->
        val group=codes.filter { it.temporary==temporary }
        Text("${if(temporary) "Temporary" else "Reusable"} pairing QRs · ${group.size}",style=MaterialTheme.typography.subtitle1)
        group.forEach { qr -> Surface(elevation=1.dp,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(qr.name.ifBlank { "Device ${qr.nodeId.take(8)}" })
            Text("${qr.endpoint} · ID ${qr.nodeId}",style=MaterialTheme.typography.caption)
            Text(if(qr.expired) "Expired — request a refreshed QR" else qr.expiresAtMs?.let { "Expires ${Instant.ofEpochMilli(it)}" } ?: "Reusable",style=MaterialTheme.typography.caption)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={clipboard.setText(AnnotatedString(qr.link));notice="Pairing link copied"},enabled=!qr.expired){Text("Copy link")}
                TextButton(onClick={
                    val chooser=JFileChooser().apply { selectedFile=java.io.File("meshlit-pairing-${qr.nodeId.take(8)}.png") }
                    if(chooser.showSaveDialog(null)==JFileChooser.APPROVE_OPTION)
                        runCatching { DesktopPairingLinks.exportPng(qr.link,chooser.selectedFile.toPath()) }
                            .onSuccess { notice="QR PNG exported" }.onFailure { notice="Cannot export QR: ${it.message}" }
                },enabled=!qr.expired){Text("Export QR PNG")}
                TextButton(onClick={deleting=qr}){Text("Delete")}
            }
        } } }
    }
    deleting?.let { qr -> AlertDialog(onDismissRequest={deleting=null},title={Text("Delete saved QR?")},
        text={Text("This removes the Studio copy. It does not revoke a device.")},
        confirmButton={TextButton(onClick={runCatching { val next=codes.filterNot { it.id==qr.id };store.save(next);codes=next }
            .onFailure { notice="Cannot delete QR: ${it.message}" };deleting=null}){Text("Delete")}},
        dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}}) }
}
