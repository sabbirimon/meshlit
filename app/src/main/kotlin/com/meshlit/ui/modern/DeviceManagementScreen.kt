package com.meshlit.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.control.WebBridgeHost
import com.meshlit.core.mcp.control.EnrollmentState
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.devices.PairingQrStore
import com.meshlit.devices.QrCodec
import com.meshlit.di.koinInject
import com.meshlit.disco.DiscoveryMode
import com.meshlit.disco.DiscoveryModeStore
import com.meshlit.disco.PeerRepository
import com.meshlit.inference.PeerRegistry
import com.meshlit.p2p.PeerChatHost
import com.meshlit.peering.PeerSessionStore
import com.meshlit.peering.PairingHistoryStore
import com.meshlit.peering.RelayManager
import com.meshlit.pipeline.PipelineHost
import com.meshlit.pipeline.WorkerAssessment
import com.meshlit.core.inference.pipeline.ClusterNegotiation
import com.meshlit.settings.SettingsRepository
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import com.meshlit.core.trust.PairingAuthority
import com.meshlit.core.trust.PeerCrypto
import com.meshlit.core.trust.TrustTier
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.ClusterRoleTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.net.URI
import java.io.File

/** One inventory for different device relationships; revocation keeps their trust scopes separate. */
@Composable internal fun DeviceManagementScreen(onBack:()->Unit,onOpen:(String)->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as MeshlitApplication
    val clipboard=LocalClipboardManager.current
    val qrStore=remember(context){PairingQrStore(context)}
    val savedQrs by qrStore.entries.collectAsStateWithLifecycle()
    val settings=koinInject<SettingsRepository>()
    val endpoints by settings.remoteEndpointsFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val pipeline=koinInject<PipelineHost>()
    val pipelineStatus by pipeline.status.collectAsStateWithLifecycle()
    val relay=koinInject<RelayManager>()
    val relayState by relay.state.collectAsStateWithLifecycle()
    val peerChat=koinInject<PeerChatHost>()
    val peerStatus by peerChat.status.collectAsStateWithLifecycle()
    val peerSince by peerChat.connectedSinceMs.collectAsStateWithLifecycle()
    val discovery=koinInject<DiscoveryModeStore>()
    val discoveryMode by discovery.mode.collectAsStateWithLifecycle()
    val repository=koinInject<PeerRepository>()
    val scan by repository.uiState.collectAsStateWithLifecycle()
    val currentDiscovered=scan.grouped.values.flatten().count { it.advertisement.transport in setOf("nsd","ble","lan") }
    val currentNodeIds=scan.grouped.values.flatten().map { it.advertisement.nodeId }.toSet()
    val externalServices=remember(context) { readExternalServices(context).size }
    val webHost=koinInject<WebBridgeHost>()
    var webClients by remember { mutableStateOf(webHost.directory.read().devices) }
    LaunchedEffect(webHost) { while(isActive) { webClients=withContext(Dispatchers.IO) { webHost.directory.read().devices };delay(2000) } }
    val registry=koinInject<PeerRegistry>()
    val sessions=koinInject<PeerSessionStore>()
    val authority=koinInject<PairingAuthority>()
    val pending by authority.pending.collectAsStateWithLifecycle()
    val activeSessions by authority.activeSessionCount.collectAsStateWithLifecycle()
    val pairingHistory=koinInject<PairingHistoryStore>()
    val history by pairingHistory.entries.collectAsStateWithLifecycle()
    val requestAlerts by pairingHistory.alertsEnabled.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var selected by remember { mutableStateOf<RemoteEndpoint?>(null) }
    var viewingQr by remember { mutableStateOf<PairingQrStore.SavedQr?>(null) }
    var exportingQr by remember { mutableStateOf<PairingQrStore.SavedQr?>(null) }
    var deletingQr by remember { mutableStateOf<PairingQrStore.SavedQr?>(null) }
    var clearHistory by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var assessments by remember { mutableStateOf<List<WorkerAssessment>>(emptyList()) }
    var assessedAt by remember { mutableLongStateOf(0L) }
    var assessing by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf(pipeline.activeGroup()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { authority.prunePending();delay(15000);now=System.currentTimeMillis() } }
    fun age(start:Long):String = if(start<=0L || start>now) "unknown" else {
        val seconds=(now-start)/1000;when { seconds<60->"${seconds}s";seconds<3600->"${seconds/60}m";else->"${seconds/3600}h ${seconds%3600/60}m" }
    }
    val workers=pipeline.peers()
    fun run(action:suspend ()->Unit) { scope.launch {
        try { error=null;notice=null;action() } catch(e:CancellationException) { throw e }
        catch(e:Exception) { error=e.message ?: "Device action failed" }
    } }
    fun shareQr(qr:PairingQrStore.SavedQr) = run {
        val file=withContext(Dispatchers.IO) {
            File(context.cacheDir,"exports/captures/pairing-share-${qr.id}.png").apply {
                parentFile?.mkdirs()
                outputStream().use { stream -> QrCodec.encode(qr.payload.encode(),512).compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream) }
            }
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type="image/png";putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_TEXT,qr.payload.encode())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"Share pairing QR"))
    }
    val saveQrPng=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val qr=exportingQr;exportingQr=null
        if(uri!=null && qr!=null) run {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    check(QrCodec.encode(qr.payload.encode(),512).compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream))
                } ?: error("Cannot write the selected file")
            }
            notice="QR image saved to the selected folder"
        }
    }
    deletingQr?.let { qr -> AlertDialog(onDismissRequest={deletingQr=null},title={Text("Delete saved QR?")},
        text={Text("This removes the saved copy of ${qr.payload.nodeName.ifBlank { qr.payload.nodeId }}. It does not revoke a paired device; use Revoke separately.")},
        confirmButton={TextButton(onClick={qrStore.delete(qr.id);deletingQr=null}){Text("Delete")}},
        dismissButton={TextButton(onClick={deletingQr=null}){Text("Cancel")}}) }
    if(clearHistory) AlertDialog(onDismissRequest={clearHistory=false},title={Text("Clear pairing history?")},
        text={Text("This deletes the local event list. It does not revoke active device access.")},
        confirmButton={TextButton(onClick={pairingHistory.clear();clearHistory=false}){Text("Clear")}},
        dismissButton={TextButton(onClick={clearHistory=false}){Text("Cancel")}})
    viewingQr?.let { qr ->
        var image by remember(qr.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(qr.id) { image=withContext(Dispatchers.Default) { QrCodec.encode(qr.payload.encode(),384) } }
        AlertDialog(onDismissRequest={viewingQr=null},title={Text(qr.payload.nodeName.ifBlank { "Pairing QR" })},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(qr.expired) Text("Expired temporary QR · refresh it on the owner device",color=MaterialTheme.colorScheme.error)
                image?.let { androidx.compose.foundation.Image(it.asImageBitmap(),"Saved pairing QR",Modifier.fillMaxWidth().height(270.dp)) }
                Text("Device ID: ${qr.payload.nodeId}",style=MaterialTheme.typography.bodySmall)
                Text("Endpoint: ${qr.payload.baseUrl}",style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton(onClick={viewingQr=null}){Text("Close")}},
            dismissButton={TextButton(onClick={clipboard.setText(AnnotatedString(qr.payload.encode()));notice="Pairing link copied"}){Text("Copy link")}})
    }
    selected?.let { ep -> AlertDialog(onDismissRequest={selected=null},title={Text("Revoke ${ep.name}?")},
        text={Text("Remove the saved endpoint, its local session and compute approval. The other device may keep its own approval; revoke there too if needed.")},
        confirmButton={TextButton(onClick={selected=null;run {
            if(ep.id.startsWith("node-")) {
                val node=ep.id.removePrefix("node-")
                val result=app.trustStore.revoke(NodeId(node))
                if(result is MeshlitResult.Failure) throw IllegalStateException(result.error.tag)
                authority.revoke(node)
            }
            sessions.remove(ep.baseUrl)
            settings.trustEndpoint(ep.id,false)
            settings.removeEndpoint(ep.id)
            val host=runCatching { URI(ep.baseUrl).host }.getOrNull()
            if (host!=null) registry.remove(host)
            repository.forget(ep.id.removePrefix("node-"))
            notice="Local access revoked for ${ep.name}"
        }}) { Text("Revoke local access") }},dismissButton={TextButton(onClick={selected=null}) { Text("Cancel") }}) }
    confirmStop?.let { action -> AlertDialog(onDismissRequest={confirmStop=null},title={Text("Stop $action?")},
        text={Text(when(action) { "cluster"->"Stops the active layer pipeline and unloads its model.";"worker"->"Stops accepting layer work on this phone.";"relay"->"Ends hosted relay membership for connected members.";else->"Disconnects this live peer channel." })},
        confirmButton={TextButton(onClick={confirmStop=null;run {
            when(action) { "cluster"->pipeline.stopPipeline();"worker"->pipeline.stopWorker();"relay"->relay.disable();"chat"->peerChat.disconnect() }
            notice="$action stopped"
        }}) { Text("Stop") }},dismissButton={TextButton(onClick={confirmStop=null}) { Text("Cancel") }}) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("device-management-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back to settings") };Text("Device management",style=MaterialTheme.typography.headlineSmall)
            Text("Review each relationship before you approve, revoke or stop it. Discovery, relay membership, endpoint trust, layer workers and peer chat have separate permissions.")
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)};notice?.let{Text(it,color=MaterialTheme.colorScheme.primary)} }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Discovery · $discoveryMode",style=MaterialTheme.typography.titleMedium)
            Text("$currentDiscovered current network discoveries · ${if(scan.scanning) "scanning" else "paused"}. Advertisements are unverified.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onOpen("radar")}) { Text("Open radar") }
                TextButton(onClick={discovery.saveHuman(if(discoveryMode==DiscoveryMode.OFF) DiscoveryMode.AUTO else DiscoveryMode.OFF);app.refreshDiscovery()}) {
                    Text(if(discoveryMode==DiscoveryMode.OFF) "Resume automatic discovery" else "Pause discovery")
                }
            }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Non-Meshlit services · $externalServices",style=MaterialTheme.typography.titleMedium)
            Text("LAN, VPN or HTTPS endpoints are tracked separately from paired Meshlit devices. Task work needs a compatible MCP, model API or SSH adapter and its own approval.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onOpen("external-services")}) { Text("Manage external services") }
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Web/API clients · ${webClients.size}",style=MaterialTheme.typography.titleMedium)
            Text("${webClients.count { it.state==EnrollmentState.PENDING }} waiting · ${webClients.count { it.state==EnrollmentState.APPROVED && it.expiresAtMs>System.currentTimeMillis() }} active · ${webClients.count { it.state==EnrollmentState.APPROVED && it.expiresAtMs<=System.currentTimeMillis() }} expired",
                style=MaterialTheme.typography.bodySmall)
            Text(webClients.groupingBy { it.kind }.eachCount().entries.joinToString { "${it.key.name.lowercase()} ${it.value}" }.ifBlank { "No enrolled browser or API clients" },
                style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onOpen("web-bridge")}) { Text("Manage invitations and access") }
        } } }
        item { Text("Saved endpoints · ${endpoints.size}",style=MaterialTheme.typography.titleMedium)
            Text("Only rows marked approved have this phone's compute approval. Remote reachability still needs a live check.")
            OutlinedButton(onClick={onOpen("network")}) { Text("Pair or verify a device") } }
        if(endpoints.isEmpty()) item { Text("No saved device endpoints.") }
        items(endpoints,key={it.id}) { ep -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(ep.name,style=MaterialTheme.typography.titleSmall);Text(ep.baseUrl)
            Text("Device ID · ${savedDeviceId(ep)}",style=MaterialTheme.typography.bodySmall)
            Text(savedDeviceAddedAt(ep),style=MaterialTheme.typography.bodySmall)
            Text(if(ep.id.removePrefix("node-") in currentNodeIds) "Seen in current discovery · identity still requires verification"
                else "Not seen in current discovery",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={clipboard.setText(AnnotatedString(ep.id.removePrefix("node-")));notice="Full device ID copied"}) {
                Text("Copy full device ID")
            }
            Text(if(ep.trusted) "Compute approved on this phone" else "Saved, not compute approved",style=MaterialTheme.typography.bodySmall)
            Text(if(ep.lastSeenMs>0) "Last recorded contact ${age(ep.lastSeenMs)} ago; current online state unknown" else "No recorded contact; online duration unknown",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={selected=ep}) { Text("Revoke / remove") }
        } } }
        item { Text("Pairing inbox · ${pending.size}",style=MaterialTheme.typography.titleMedium)
            Text("$activeSessions active approved sessions. New requests wait here independently; accepting one does not disconnect another.")
            if(pending.isEmpty()) Text("No requests waiting for owner approval.",style=MaterialTheme.typography.bodySmall) }
        items(pending,key={"pending:${it.nodeId}"}) { request -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("${request.nodeId} · ${request.requestedTier}",style=MaterialTheme.typography.titleSmall)
            Text("Key fingerprint: ${PeerCrypto.fingerprint(request.publicKey)}",style=MaterialTheme.typography.bodySmall)
            Text("Waiting ${age(authority.queuedAt(request.nodeId))}. Compare this fingerprint with the owner before approval.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={run {
                    val tier=TrustTier.fromTag(request.requestedTier) ?: throw IllegalStateException("Unknown requested scope")
                    val result=withContext(Dispatchers.IO) { app.trustStore.upsert(DeviceTrustPolicy(request.nodeId,tier,
                        setOf(ClusterRoleTag.BRAIN),publicKeyFingerprint=PeerCrypto.fingerprint(request.publicKey))) }
                    if(result is MeshlitResult.Failure) throw IllegalStateException(result.error.tag)
                    authority.dismiss(request.nodeId)
                    notice="Approved ${request.nodeId.take(8)}; its waiting request can now complete"
                }}) { Text("Approve") }
                TextButton(onClick={authority.dismiss(request.nodeId)}) { Text("Dismiss") }
            }
        } } }
        item { Text("Pairing history · ${history.size}",style=MaterialTheme.typography.titleMedium)
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Pairing notifications",modifier=Modifier.weight(1f))
                Switch(checked=requestAlerts,onCheckedChange=pairingHistory::setAlertsEnabled)
            }
            Text("Notifications are rate limited for new waiting requests and successful pairing. Android notification permission also applies.",style=MaterialTheme.typography.bodySmall)
            if(history.size>20) TextButton(onClick={showAllHistory=!showAllHistory}) { Text(if(showAllHistory) "Show recent 20" else "Show all ${history.size}") }
            if(history.isNotEmpty()) TextButton(onClick={clearHistory=true}) { Text("Clear local history") }
        }
        items(if(showAllHistory) history else history.take(20),key={"pair-history:${it.nodeId}:${it.atMs}:${it.status}"}) { event ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${event.status.replaceFirstChar { it.uppercase() }} · ${event.nodeId.take(16)}",style=MaterialTheme.typography.titleSmall)
                Text("${java.time.Instant.ofEpochMilli(event.atMs)} · ${event.tier}",style=MaterialTheme.typography.bodySmall)
                Text("Fingerprint ${event.fingerprint}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={onOpen("network")}) { Text("Open Devices to reconnect") }
            } }
        }
        item { Text("Saved pairing QRs",style=MaterialTheme.typography.titleMedium)
            Text("A saved code is a shareable identity record, not compute approval. Save a new code in Devices → QR / paste.",style=MaterialTheme.typography.bodyMedium) }
        listOf(false,true).forEach { temporary ->
            val group=savedQrs.filter { it.temporary==temporary }
            item { Text("${if(temporary) "Temporary" else "Reusable"} · ${group.size}",style=MaterialTheme.typography.titleMedium) }
            if(group.isEmpty()) item { Text("No ${if(temporary) "temporary" else "reusable"} QRs saved.",style=MaterialTheme.typography.bodySmall) }
            items(group,key={"saved-qr:${it.id}"}) { qr -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(qr.payload.nodeName.ifBlank { "Device ${qr.payload.nodeId.take(8)}" },style=MaterialTheme.typography.titleMedium)
                Text(qr.payload.baseUrl,style=MaterialTheme.typography.bodySmall)
                Text(if(qr.expired) "Expired · request a refreshed QR" else if(temporary) "Expires ${java.time.Instant.ofEpochMilli(qr.payload.expiresAtEpochMs!!)}" else "Reusable",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick={viewingQr=qr}){Text("View")}
                    TextButton(onClick={shareQr(qr)},enabled=!qr.expired){Text("Share")}
                    TextButton(onClick={exportingQr=qr;saveQrPng.launch("Meshlit-${qr.payload.nodeId.take(8)}-QR.png")},enabled=!qr.expired){Text("Save PNG")}
                    TextButton(onClick={deletingQr=qr}){Text("Delete")}
                }
            } } }
        }
        item { Text("Layer groups · ${pipeline.groups().size}",style=MaterialTheme.typography.titleMedium)
            Text("Active group: ${pipeline.activeGroup()} · ${workers.size} saved workers · coordinator ${if(pipelineStatus.coordinator) "running" else "off"}")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { pipeline.groups().forEach { group ->
                FilterChip(selectedGroup==group,onClick={run { pipeline.setActiveGroup(group);selectedGroup=group;assessments=emptyList();assessedAt=0L }},
                    enabled=!pipelineStatus.coordinator && !pipelineStatus.starting,label={Text(group)})
            } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onOpen("sharding")}) { Text("Manage groups and workers") }
                if(pipelineStatus.coordinator) TextButton(onClick={confirmStop="cluster"}) { Text("Stop cluster") }
            }
            OutlinedButton(enabled=!assessing && pipeline.groupPeers().isNotEmpty(),onClick={run { assessing=true;try { assessments=pipeline.assessGroup();assessedAt=System.currentTimeMillis() } finally { assessing=false } }}) { Text(if(assessing) "Assessing…" else "Refresh live worker metrics") }
            Text(if(assessedAt>0) "Verified assessment ${age(assessedAt)} ago. Refresh for current status." else "No live assessment in this view. Saved workers may be offline.",style=MaterialTheme.typography.bodySmall)
            if(pipelineStatus.worker) TextButton(onClick={confirmStop="worker"}) { Text("Stop this worker") } }
        items(pipeline.groups(),key={"group:$it"}) { group -> Text("$group · ${workers.count{it.group==group}} saved workers",style=MaterialTheme.typography.bodyMedium) }
        items(assessments,key={"assessment:${it.peer.host}:${it.peer.port}"}) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${row.peer.host}:${row.peer.port}",style=MaterialTheme.typography.titleSmall)
            val offer=row.offer
            if(offer==null) Text("No verified live offer · ${row.reason}",style=MaterialTheme.typography.bodySmall)
            else {
                Text("${offer.deviceClass} · ${offer.abi} · ${offer.backend} · ${offer.cpuThreads} worker threads")
                Text("Free RAM ${formatBytes(offer.freeMemoryBytes)} · storage ${formatBytes(offer.freeDiskBytes)} · estimated layer budget ${formatBytes(ClusterNegotiation.budget(offer))}")
                Text("Thermal ${offer.thermalStatus} · verified control RTT ${row.latencyMs ?: "unknown"} ms · admission risk ${row.riskScore}/100")
                Text("Worker online for ${offer.workerStartedAtMs?.let(::age) ?: "unknown"} at assessment; ${row.reason}",style=MaterialTheme.typography.bodySmall)
            }
        } } }
        item { Text("Membership relay",style=MaterialTheme.typography.titleMedium)
            Text("${relayState.members.size} current hosted members · ${if(relayState.enabled) "hosting" else "host off"} · ${if(relayState.joinedUrl!=null) "joined a relay" else "not joined"}")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onOpen("network")}) { Text("Manage relay") }
                if(relayState.enabled) TextButton(onClick={confirmStop="relay"}) { Text("Stop relay") }
                if(relayState.joinedUrl!=null) TextButton(onClick={relay.leave();notice="Left relay"}) { Text("Leave relay") }
            } }
        items(relayState.members,key={"relay:${it.nodeId}"}) { member -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text(member.name);Text("Node ${member.nodeId}",style=MaterialTheme.typography.bodySmall)
            Text("Joined ${age(member.joinedAtMs)} ago · last heartbeat ${age(member.lastSeenMs)} ago",style=MaterialTheme.typography.bodySmall)
            Text("Membership only · no automatic compute or chat permission",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={relay.revoke(member.nodeId);notice="Relay membership revoked for ${member.name}"}) { Text("Revoke relay session") }
        } } }
        item { Text("Peer chat",style=MaterialTheme.typography.titleMedium);Text(peerStatus)
            if(peerSince!=null) Text("Connected for ${age(peerSince!!)} · one live channel")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onOpen("meshchat")}) { Text("Open Mesh chat") }
                if(peerStatus.startsWith("Connected")) TextButton(onClick={confirmStop="chat"}) { Text("Disconnect") }
            }
            Text("Current direct chat supports one foreground peer; multi-member rooms are not yet available.",style=MaterialTheme.typography.bodySmall) }
    }
}
