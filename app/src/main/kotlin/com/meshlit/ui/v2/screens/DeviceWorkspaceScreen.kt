package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.*
import com.meshlit.core.discovery.LocalPeerDescriptor
import com.meshlit.core.trust.*
import com.meshlit.devices.PairingPayload
import com.meshlit.devices.endpointUrl
import com.meshlit.devices.PairingQrStore
import com.meshlit.di.koinInject
import com.meshlit.disco.*
import com.meshlit.inference.PeerRegistry
import com.meshlit.peering.DeviceIdentity
import com.meshlit.peering.PairingHistoryStore
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.modern.RadarMarker
import com.meshlit.ui.modern.RadarTopology
import com.meshlit.ui.modern.savedDeviceAddedAt
import com.meshlit.ui.modern.savedDeviceId
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

@Composable
fun DeviceWorkspaceScreen(onNavigate: (String) -> Unit, initialPeerNodeId: String? = null) {
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val clipboard = LocalClipboardManager.current
    val qrStore = remember(app) { PairingQrStore(app) }
    val settings: SettingsRepository = koinInject()
    val peers: PeerRepository = koinInject()
    val identity: DeviceIdentity = koinInject()
    val registry: PeerRegistry = koinInject()
    val authority: PairingAuthority = koinInject()
    val pairingHistory: PairingHistoryStore = koinInject()
    val deviceEvents by pairingHistory.entries.collectAsStateWithLifecycle()
    val lastReadAt by pairingHistory.lastReadAt.collectAsStateWithLifecycle()
    val discoveryMode: DiscoveryModeStore = koinInject()
    val mode by discoveryMode.mode.collectAsStateWithLifecycle()
    val nearbyState by peers.uiState.collectAsStateWithLifecycle()
    val incoming by authority.pending.collectAsStateWithLifecycle()
    val endpoints by settings.remoteEndpointsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by settings.activeEndpointIdFlow.collectAsStateWithLifecycle(initialValue = "")
    val scope = rememberCoroutineScope()
    val hosting by com.meshlit.inference.InferenceForegroundService.hostingState.collectAsStateWithLifecycle()
    val animateScan = LocalMeshlitThemeConfig.current.animationsEnabled
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var add by rememberSaveable { mutableStateOf(false) }
    var qr by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pairingStages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var progressEndpoint by remember { mutableStateOf<RemoteEndpoint?>(null) }
    var selectedRadarMarker by remember { mutableIntStateOf(-1) }
    var own by remember { mutableStateOf<PairingPayload?>(null) }
    var remove by remember { mutableStateOf<RemoteEndpoint?>(null) }
    var pendingPairing by remember { mutableStateOf<PairingPayload?>(null) }
    var reviewingId by remember { mutableStateOf<String?>(null) }
    var handledInitialPeer by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(app) {
        app.refreshDiscovery()
        peers.refreshAvailability()
        while (app.nodeIdHex.isBlank()) delay(100)
        while (app.localIpAddress.isBlank()) delay(2_000)
        own = withContext(Dispatchers.IO) { PairingPayload(app.displayName, endpointUrl(app.localIpAddress, app.httpServerPort), app.nodeIdHex,
            app.capabilityTier.name, identity.get(app.nodeIdHex).publicKey) }
        // Opening Devices makes the identity endpoint reachable for a nearby
        // owner. The handshake still waits for their explicit compute approval.
        if (hosting !is com.meshlit.inference.HostingStatus.Active &&
            hosting !is com.meshlit.inference.HostingStatus.Starting) {
            runCatching { com.meshlit.inference.InferenceForegroundService.startForInference(app) }
                .onFailure { error = "hosting.start_failed: ${it.message}" }
        }
    }
    fun verifyAndConnect(ep: RemoteEndpoint) {
        busy = ep.id; error = null
        progressEndpoint = ep
        pairingStages = pairingStages + (ep.id to "Checking device identity…")
        scope.launch {
            try {
                val key = ep.notes.substringAfter("identity.key=", "").substringBefore('\n')
                require(key.isNotBlank()) { "Scan this device's new QR to obtain its identity key" }
                val node = ep.id.removePrefix("node-")
                val keyFingerprint = PeerCrypto.fingerprint(key)
                pairingHistory.record(PairingHistoryEvent(node,keyFingerprint,"requested","outgoing",System.currentTimeMillis()))
                val nonce = PeerCrypto.randomHex()
                val proof = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                        .followRedirects(false).followSslRedirects(false).build()
                    client.newCall(Request.Builder().url("${ep.baseUrl.trimEnd('/')}/v1/identity?nonce=$nonce").build()).execute().use { response ->
                        require(response.isSuccessful) { "Peer returned HTTP ${response.code}. Start its inference service." }
                        val source = response.body?.source() ?: error("Empty identity response")
                        require(!source.request(8193)) { "Identity response too large" }
                        PeerCrypto.json.decodeFromString(IdentityProof.serializer(), source.readUtf8())
                    }
                }
                require(PeerCrypto.verify(proof, node, key, nonce)) { "Identity does not match the scanned key or challenge" }
                pairingStages = pairingStages + (ep.id to "Identity matched. Requesting owner approval…")
                val uri = URI(ep.baseUrl)
                val local = isPrivatePairingHost(uri.host)
                require(uri.scheme == "https" || local) { "Use HTTPS for internet compute" }
                suspend fun attemptHandshake() = withContext(Dispatchers.IO) { runHandshake(
                    transport = object : HandshakeTransport {
                        override suspend fun postJson(baseUrl: String, path: String, body: String): MeshlitResult<String> = withContext(Dispatchers.IO) {
                            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                            val media = "application/json".toMediaType()
                            val requestBody = body.toRequestBody(media)
                            client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + path).post(requestBody).build()).execute().use { response ->
                                val source = response.body?.source() ?: return@withContext MeshlitResult.Failure(MeshlitError.Network("handshake.empty_response"))
                                require(!source.request(16385)) { "Handshake response too large" }
                                val text = source.readUtf8()
                                if (response.isSuccessful) MeshlitResult.Success(text)
                                else MeshlitResult.Failure(MeshlitError.Auth(text.take(128)))
                            }
                        }
                    }, trustStore = app.trustStore, selfNodeId = NodeId(app.nodeIdHex), peerBaseUrl = ep.baseUrl,
                    requestedTier = if (local) TrustTier.LOCAL_TRUSTED else TrustTier.WAN, nonce = PeerCrypto.randomHex(),
                    expectedPeerNodeId = node, expectedPeerPublicKey = key, selfIdentity = identity.get(app.nodeIdHex),
                    onSession = { koinInject<com.meshlit.peering.PeerSessionStore>().save(ep.baseUrl, it) }) }
                var result = attemptHandshake()
                var attempts = 0
                while (result is MeshlitResult.Failure && result.error.tag == "handshake.peer_approval_required" && attempts++ < 30) {
                    busy = "waiting:${ep.id}"
                    notice = "Identity checked. Waiting for the other device owner to approve compute in Devices…"
                    pairingStages = pairingStages + (ep.id to "Waiting for owner approval · ${attempts * 2}s")
                    delay(2_000)
                    result = attemptHandshake()
                }
                if (result is MeshlitResult.Failure) throw IllegalStateException(
                    if (result.error.tag == "handshake.peer_approval_required")
                        "Approval is still pending on the other device. Ask its owner to open Devices and approve the request."
                    else result.error.tag)
                pairingStages = pairingStages + (ep.id to "Approved. Saving connection…")
                settings.trustEndpoint(ep.id, true)
                pairingHistory.record(PairingHistoryEvent(node,keyFingerprint,"approved","connected_outgoing",System.currentTimeMillis()))
                // Legacy forwarding supports only IPv4 on the default port.
                val forwardingIssue = if (local && uri.port == 8080) runCatching {
                    registry.bindIdentity(uri.host, node)
                }.exceptionOrNull() else null
                error = null
                pairingStages = pairingStages + (ep.id to "Connected · identity verified and compute approved")
                notice = "Identity verified and connected. ${if (uri.scheme == "http") "Use a VPN for encrypted transport." else "HTTPS transport."}" +
                    if (forwardingIssue == null) "" else " Legacy forwarding setup needs a retry."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val reason = e.message ?: "Connection failed"
                pairingStages = pairingStages + (ep.id to "Could not connect · $reason")
                error = "pairing.verification_failed: $reason"
            }
            finally { busy = null }
        }
    }
    fun commitUnverified(payload: PairingPayload, connectNow: Boolean = false) {
        scope.launch {
            try {
                require(payload.nodeId != app.nodeIdHex) { "pairing.self: This is your own device" }
                val endpoint = payload.toRemoteEndpoint()
                settings.upsertEndpoint(endpoint)
                peers.ingest(payload.toPeerAdvertisement())
                add = false; qr = false
                if (connectNow) verifyAndConnect(endpoint)
                else notice = "Saved for later as unverified. Open this device and tap Pair securely when ready."
            } catch (e: Exception) { error = "pairing.invalid_payload: ${e.message}" }
        }
    }
    fun save(payload: PairingPayload) { pendingPairing=payload.validate();qr=false;add=false }
    fun reviewNearby(candidate: com.meshlit.core.discovery.PeerAdvertisement) {
        if(reviewingId!=null) return
        reviewingId=candidate.nodeId;error=null
        scope.launch {
            try {
                val url=endpointUrl(candidate.host,candidate.port)
                val uri=URI(url)
                require(uri.scheme=="https" || isPrivatePairingHost(uri.host)) { "Use HTTPS for an Internet device" }
                require(candidate.fingerprint.isNotBlank()) { "This advertisement has no identity fingerprint. Scan its QR instead." }
                val nonce=PeerCrypto.randomHex()
                val proof=withContext(Dispatchers.IO) {
                    val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
                        .followRedirects(false).followSslRedirects(false).build()
                    client.newCall(Request.Builder().url("${url.trimEnd('/')}/v1/identity?nonce=$nonce").build()).execute().use { response ->
                        require(response.isSuccessful) { "Peer returned HTTP ${response.code}" }
                        val source=response.body?.source() ?: error("Empty identity response")
                        require(!source.request(8193)) { "Identity response too large" }
                        PeerCrypto.json.decodeFromString(IdentityProof.serializer(),source.readUtf8())
                    }
                }
                require(PeerCrypto.verify(proof,candidate.nodeId,proof.publicKey,nonce)) { "Device identity proof failed" }
                require(PeerCrypto.fingerprint(proof.publicKey)==candidate.fingerprint) { "Advertised fingerprint changed. Scan the owner's QR." }
                pendingPairing=PairingPayload(candidate.displayName,url,candidate.nodeId,candidate.tier,proof.publicKey).validate()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { error="pairing.review_failed: ${e.message}" }
            finally { reviewingId=null }
        }
    }
    val unreadEvents = deviceEvents.count { it.atMs > lastReadAt }
    val nearby = nearbyState.grouped.values.flatten().map { it.advertisement }
        .filter { it.nodeId != app.nodeIdHex && it.host.isNotBlank() && it.port in 1..65535 &&
            it.transport in setOf("nsd", "ble", "lan") }
        .groupBy { it.nodeId }.values.map { copies -> copies.maxBy { candidate ->
            (if (candidate.displayName.isNotBlank()) 4 else 0) +
                (if (candidate.manufacturer.isNotBlank()) 2 else 0) +
                (if (candidate.model.isNotBlank()) 2 else 0) +
                (if (candidate.transport == "nsd") 1 else 0)
        } }
    LaunchedEffect(initialPeerNodeId,nearby) {
        val candidate=nearby.firstOrNull { it.nodeId==initialPeerNodeId && initialPeerNodeId!=handledInitialPeer }
        if(candidate!=null) {
            handledInitialPeer=candidate.nodeId
            if(endpoints.none { it.id=="node-${candidate.nodeId}" }) reviewNearby(candidate)
            else notice="This device is already saved. Review it in the list below."
        }
    }
    val radarMarkers = nearby.map { candidate ->
        val saved = endpoints.firstOrNull { it.id == "node-${candidate.nodeId}" }
        val key = saved?.notes?.substringAfter("identity.key=", "")?.substringBefore('\n').orEmpty()
        RadarMarker(0, saved?.trusted == true && key.isNotBlank() &&
            PeerCrypto.fingerprint(key) == candidate.fingerprint, false, false)
    }
    val radarNames=nearby.map { it.displayName.ifBlank { "Nearby Meshlit device" } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Better together.", style = MaterialTheme.typography.headlineLarge)
            Text("Pair devices, check their identity, and choose where work runs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { OutlinedButton(onClick = { showNotifications = true; pairingHistory.markRead() },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Notifications${if (incoming.isNotEmpty()) " · ${incoming.size} waiting" else if (unreadEvents > 0) " · $unreadEvents new" else ""}")
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ add = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add device") }
            OutlinedButton({ qr = true }, Modifier.fillMaxWidth(), enabled = own != null) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text("QR / paste") }
        } }
        item { OutlinedButton(onClick = { onNavigate("scan") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Devices, null); Spacer(Modifier.width(8.dp)); Text("Browse nearby devices")
        } }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pairing radar", style = MaterialTheme.typography.titleLarge)
                    RadarTopology(radarMarkers, nearbyState.scanning, animateScan, radarNames) {
                        selectedRadarMarker=it
                        nearby.getOrNull(it)?.let(::reviewNearby)
                    }
                    radarNames.getOrNull(selectedRadarMarker)?.let { name ->
                        Text("Selected: $name · review its row below to pair or manage it",style=MaterialTheme.typography.bodyMedium)
                    }
                    Text("${nearby.size} discovered now · ${endpoints.size} saved separately · green approved · red unverified",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { app.refreshDiscovery(); peers.refreshAvailability() },
                        modifier = Modifier.fillMaxWidth(), enabled = mode != DiscoveryMode.OFF) { Text("Refresh nearby scan") }
                    Text("Only current network discoveries appear as dots. Saved devices remain in the list below until you remove them.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (endpoints.isNotEmpty()) {
            item { Text("Already added · ${endpoints.size}", style = MaterialTheme.typography.titleLarge)
                Text("Reconnect a saved device here. You do not need to scan its QR again unless its identity key changed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(endpoints, key = { "quick:${it.id}" }) { ep ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ep.name, style = MaterialTheme.typography.titleMedium)
                    Text(ep.baseUrl, style = MaterialTheme.typography.bodySmall)
                    Text("ID ${savedDeviceId(ep)} · ${savedDeviceAddedAt(ep)}", style = MaterialTheme.typography.bodySmall)
                    Text(if (ep.trusted) "Identity verified · compute approved" else "Saved · needs identity verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ep.trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { verifyAndConnect(ep) }, modifier = Modifier.fillMaxWidth(), enabled = busy == null) {
                        Text(if (ep.trusted) "Renew pairing" else "Pair securely")
                    }
                } }
            }
        }
        item { OutlinedButton(onClick = { onNavigate("saved-qrs") }, modifier = Modifier.fillMaxWidth()) { Text("Saved QRs and device management") } }
        item { OutlinedButton(onClick = { onNavigate("sharding") }, modifier = Modifier.fillMaxWidth()) { Text("Worker and cluster dashboard") } }
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton({ onNavigate("join") },Modifier.fillMaxWidth()) { Text("Join cluster") }
            TextButton({ onNavigate("relay") },Modifier.fillMaxWidth()) { Text("Host relay") }
        } }
        own?.let { identityPayload -> item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This device · share to pair", style = MaterialTheme.typography.titleMedium)
                Text("Device ID: ${identityPayload.nodeId}", style = MaterialTheme.typography.bodySmall)
                Text("Endpoint: ${identityPayload.baseUrl}", style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(identityPayload.nodeId)); notice = "Device ID copied" },modifier=Modifier.fillMaxWidth()) { Text("Copy ID") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(identityPayload.baseUrl)); notice = "Endpoint copied" },modifier=Modifier.fillMaxWidth()) { Text("Copy endpoint") }
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(identityPayload.encode())); notice = "Pairing link copied" },
                    modifier = Modifier.fillMaxWidth()) { Text("Copy pairing link") }
                Text("Pairing link includes this device's public identity key. Hosting starts when you open Devices; you can stop it below.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
        item {
            OutlinedButton(onClick = {
                try {
                    if (hosting == com.meshlit.inference.HostingStatus.Active) com.meshlit.inference.InferenceForegroundService.stop(app)
                    else com.meshlit.inference.InferenceForegroundService.startForInference(app)
                } catch (e: Exception) { error = "hosting.start_failed: ${e.message}" }
            }, enabled = hosting != com.meshlit.inference.HostingStatus.Starting) {
                Text(when (hosting) { com.meshlit.inference.HostingStatus.Active -> "Stop device hosting"
                    com.meshlit.inference.HostingStatus.Starting -> "Starting device hosting…"
                    else -> "Start device hosting" })
            }
            (hosting as? com.meshlit.inference.HostingStatus.Failed)?.let { Text(it.reason, color = MaterialTheme.colorScheme.error) }
        }
        item { NfcPairingControls(own, ::save, { error = it }) }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        notice?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        item {
            Text("Nearby devices", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(when {
                mode == DiscoveryMode.OFF -> "Discovery is off in Security & privacy."
                nearbyState.scanning -> "Looking automatically on this network · ${nearbyState.total} found"
                else -> "Starting local discovery…"
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (nearby.isEmpty() && mode != DiscoveryMode.OFF) item {
            Text("No Meshlit devices found yet. Keep both apps open on the same network, or use QR / paste.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(nearby, key = { "nearby:${it.nodeId}" }) { candidate ->
            val saved = endpoints.firstOrNull { it.id == "node-${candidate.nodeId}" }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface)) { Row(Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.displayName.ifBlank { "Device ${candidate.nodeId.take(8)}" }, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (candidate.manufacturer.isNotBlank() || candidate.model.isNotBlank())
                        Text("Claimed ${listOf(candidate.manufacturer, candidate.model).filter(String::isNotBlank).joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Advertised compute tier: ${candidate.tier} · live resource capacity unknown",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${endpointUrl(candidate.host, candidate.port)} · ${candidate.transport}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(if (saved == null) "Nearby advertisement · identity unverified" else
                        "Nearby now · ${if (saved.trusted) "saved and locally approved" else "saved, identity unverified"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saved == null) Button(onClick = { reviewNearby(candidate) },enabled=reviewingId==null) {
                    Text(if(reviewingId==candidate.nodeId) "Checking…" else "Add")
                }
                else Text("Saved", style = MaterialTheme.typography.labelMedium)
            } }
        }
        if (incoming.isNotEmpty()) item { Text("Pairing requests", style = MaterialTheme.typography.titleLarge) }
        items(incoming, key = { "request:${it.nodeId}" }) { request ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Device ${request.nodeId.take(16)}", style = MaterialTheme.typography.titleMedium)
                Text("Fingerprint ${PeerCrypto.fingerprint(request.publicKey)}", style = MaterialTheme.typography.bodySmall)
                Text("Verify this fingerprint on the other device. Approval allows inference in scope ${request.requestedTier}.")
                Column {
                    TextButton(onClick = { scope.launch(Dispatchers.IO) {
                        val result = app.trustStore.upsert(DeviceTrustPolicy(request.nodeId,
                            TrustTier.fromTag(request.requestedTier) ?: TrustTier.LOCAL_SANDBOXED,
                            setOf(ClusterRoleTag.BRAIN), publicKeyFingerprint = PeerCrypto.fingerprint(request.publicKey)))
                        if (result is MeshlitResult.Success) authority.dismiss(request.nodeId)
                    } },modifier=Modifier.fillMaxWidth()) { Text("Approve compute") }
                    TextButton(onClick = { authority.dismiss(request.nodeId) },modifier=Modifier.fillMaxWidth()) { Text("Reject") }
                }
            } }
        }
        item { Text("Saved device controls · ${endpoints.size}", style = MaterialTheme.typography.titleLarge) }
        if (endpoints.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
            Text("Start with another device", style = MaterialTheme.typography.titleMedium)
            Text("Use a pairing QR, paste a link, or enter an endpoint. Both devices need a reachable network.")
        } } }
        items(endpoints, key = { it.id }) { ep ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ep.name, style = MaterialTheme.typography.titleLarge)
                    Text(ep.baseUrl, style = MaterialTheme.typography.bodyMedium)
                    Text("Device ID · ${savedDeviceId(ep)}", style = MaterialTheme.typography.bodySmall)
                    Text(savedDeviceAddedAt(ep), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(ep.id.removePrefix("node-"))) }) {
                        Text("Copy full device ID")
                    }
                    Text(if (ep.trusted) "Identity verified · compute approved" else "Saved · awaiting identity verification",
                        color = if (ep.trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    pairingStages[ep.id]?.let { stage -> Text(stage, style = MaterialTheme.typography.bodySmall,
                        color = if (stage.startsWith("Could not")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                    if (ep.baseUrl.startsWith("http://")) Text("HTTP transport · traffic is not encrypted", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { verifyAndConnect(ep) }, modifier = Modifier.fillMaxWidth(), enabled = busy == null) { Text(when(busy) {
                            ep.id -> "Verifying…"
                            "waiting:${ep.id}" -> "Waiting for owner…"
                            else -> if (ep.trusted) "Renew pairing" else "Pair securely"
                        }) }
                        if (ep.trusted) TextButton({ scope.launch { settings.setActiveEndpoint(ep.id) } },modifier=Modifier.fillMaxWidth()) {
                            Text(if (ep.id == activeId) "Preferred in device list" else "Mark as preferred")
                        }
                        TextButton({ remove = ep },modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Remove device") }
                    }
                }
            }
        }
        item { Text("Preferred marks your choice in the device list. It does not route model requests or assign a cluster coordinator yet.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { TextButton({ onNavigate("device_info") }) { Text("This device's identity and capabilities") } }
    }
    if (showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false },
        title = { Text("Device notifications") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (incoming.isEmpty()) "No pairing requests waiting." else
                "${incoming.size} pairing request${if (incoming.size == 1) "" else "s"} waiting for approval or rejection.")
            if (deviceEvents.isEmpty()) Text("No device activity yet.")
            deviceEvents.take(10).forEach { event ->
                HorizontalDivider()
                Text("${event.status.replaceFirstChar { it.uppercase() }} · ${event.nodeId.take(16)}",
                    style = MaterialTheme.typography.titleSmall)
                Text(java.time.Instant.ofEpochMilli(event.atMs).toString(), style = MaterialTheme.typography.bodySmall)
            }
        } },
        confirmButton = { TextButton(onClick = { showNotifications = false; onNavigate("saved-qrs") }) {
            Text("Manage requests & history")
        } },
        dismissButton = { TextButton(onClick = { showNotifications = false }) { Text("Close") } },
    )
    if (qr && own != null) QrPairingSheet(own!!, ::save, { qr = false }, onSaveOwn = { payload ->
        qrStore.save(payload)
        notice = "Pairing QR saved in Device management"
    })
    if (add) AddDeviceDialog({ add = false }, ::save)
    progressEndpoint?.let { ep -> AlertDialog(onDismissRequest={progressEndpoint=null},
        title={Text("Connecting ${ep.name}")},
        text={Text(pairingStages[ep.id] ?: "Preparing connection…")},
        confirmButton={if (busy == null && pairingStages[ep.id]?.startsWith("Could not") == true)
            TextButton(onClick={verifyAndConnect(ep)}) { Text("Retry") } else TextButton(onClick={progressEndpoint=null}) { Text("Close") }},
        dismissButton={if (busy != null || pairingStages[ep.id]?.startsWith("Could not") == true)
            TextButton(onClick={progressEndpoint=null}) { Text("Hide") }}) }
    pendingPairing?.let { payload -> AlertDialog(onDismissRequest={pendingPairing=null},
        title={Text("Connect this device?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${payload.nodeName.ifBlank { "Unnamed device" }} · ${payload.baseUrl}")
            Text("Device ID: ${payload.nodeId.ifBlank { "Unknown · manual address" }}")
            Text(if(payload.publicKey.isBlank()) "No identity key in this address. Save as unverified, then scan the owner's QR to obtain its key."
                else "Scanned key SHA-256: ${PeerCrypto.fingerprint(payload.publicKey)}")
            Text("Compare the fingerprint with the owner through a separate channel. Verify & connect checks the key, then waits for the other owner's approval. Save for later grants no compute access.")
            OutlinedButton(onClick={pendingPairing=null},modifier=Modifier.fillMaxWidth()) { Text("Reject device") }
            if (payload.publicKey.isNotBlank()) OutlinedButton(onClick = {
                runCatching { qrStore.save(payload) }
                    .onSuccess { notice = "Pairing QR saved in Device management" }
                    .onFailure { error = "Could not save QR: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save QR for later") }
        }},confirmButton={Button(onClick={pendingPairing=null;commitUnverified(payload,connectNow=true)},
            enabled=payload.publicKey.isNotBlank()){Text("Verify & connect now")}},
        dismissButton={TextButton(onClick={pendingPairing=null;commitUnverified(payload)}){Text("Save for later")}}) }
    remove?.let { ep -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${ep.name}?") },
        text = { Text("This removes its endpoint and revokes its compute approval.") },
        confirmButton = { TextButton({ scope.launch {
            koinInject<com.meshlit.peering.PeerSessionStore>().remove(ep.baseUrl)
            settings.removeEndpoint(ep.id)
            val uri = URI(ep.baseUrl); registry.remove(uri.host)
            peers.forget(ep.id.removePrefix("node-")); remove = null
        } }) { Text("Remove") } }, dismissButton = { TextButton({ remove = null }) { Text("Cancel") } }) }
}

private fun isPrivatePairingHost(host:String):Boolean {
    if(isPrivateRFC1918(host) || isLinkLocal(host)) return true
    if(':' !in host) return false
    return runCatching {
        val bytes=java.net.InetAddress.getByName(host.removePrefix("[").removeSuffix("]")).address
        bytes.size==16 && (((bytes[0].toInt() and 0xfe)==0xfc) ||
            ((bytes[0].toInt() and 0xff)==0xfe && (bytes[1].toInt() and 0xc0)==0x80) ||
            (bytes.dropLast(1).all { it.toInt()==0 } && bytes.last().toInt()==1))
    }.getOrDefault(false)
}

@Composable
private fun AddDeviceDialog(onDismiss: () -> Unit, save: (PairingPayload) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }; var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a device") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(address, { address = it }, label = { Text("Endpoint or pairing link") },
                placeholder = { Text("http://192.168.1.20:8080") }, maxLines = 4)
            OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") })
            Text("Manual endpoints are saved without compute approval. Scan the device's QR to verify its identity.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        try {
            val raw = address.trim()
            val payload = if (raw.startsWith("meshlit:") || raw.startsWith("{")) PairingPayload.decode(raw)
                else PairingPayload(name, raw, "", "").validate()
            save(payload)
        } catch (e: Exception) { error = "pairing.invalid_payload: ${e.message}" }
    }) { Text("Save device") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
