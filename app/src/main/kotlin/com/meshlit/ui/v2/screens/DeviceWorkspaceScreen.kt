package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.*
import com.meshlit.core.discovery.LocalPeerDescriptor
import com.meshlit.core.trust.*
import com.meshlit.devices.PairingPayload
import com.meshlit.di.koinInject
import com.meshlit.disco.*
import com.meshlit.inference.PeerRegistry
import com.meshlit.peering.DeviceIdentity
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

@Composable
fun DeviceWorkspaceScreen(onNavigate: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val settings: SettingsRepository = koinInject()
    val peers: PeerRepository = koinInject()
    val identity: DeviceIdentity = koinInject()
    val registry: PeerRegistry = koinInject()
    val authority: PairingAuthority = koinInject()
    val incoming by authority.pending.collectAsStateWithLifecycle()
    val endpoints by settings.remoteEndpointsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by settings.activeEndpointIdFlow.collectAsStateWithLifecycle(initialValue = "")
    val scope = rememberCoroutineScope()
    val hosting by com.meshlit.inference.InferenceForegroundService.hostingState.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var add by rememberSaveable { mutableStateOf(false) }
    var qr by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var own by remember { mutableStateOf<PairingPayload?>(null) }
    var remove by remember { mutableStateOf<RemoteEndpoint?>(null) }
    LaunchedEffect(app) {
        while (app.nodeIdHex.isBlank()) delay(100)
        own = withContext(Dispatchers.IO) { PairingPayload(app.displayName, "http://${app.localIpAddress}:${app.httpServerPort}", app.nodeIdHex,
            app.capabilityTier.name, identity.get(app.nodeIdHex).publicKey) }
    }
    fun save(payload: PairingPayload) {
        scope.launch {
            try {
                require(payload.nodeId != app.nodeIdHex) { "pairing.self: This is your own device" }
                settings.upsertEndpoint(payload.toRemoteEndpoint())
                peers.ingest(payload.toPeerAdvertisement())
                add = false; qr = false
                notice = "Device saved. Verify its identity before using it for compute."
            } catch (e: Exception) { error = "pairing.invalid_payload: ${e.message}" }
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Better together.", style = MaterialTheme.typography.headlineLarge)
            Text("Pair devices, check their identity, and choose where work runs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ add = true }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add device") }
            OutlinedButton({ qr = true }, Modifier.weight(1f), enabled = own != null) { Icon(Icons.Default.QrCode, null); Text("QR / paste") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ onNavigate("scan") }) { Text("Scan nearby") }
            TextButton({ onNavigate("join") }) { Text("Join cluster") }
            TextButton({ onNavigate("relay") }) { Text("Host relay") }
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
        if (incoming.isNotEmpty()) item { Text("Pairing requests", style = MaterialTheme.typography.titleLarge) }
        items(incoming, key = { "request:${it.nodeId}" }) { request ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("Device ${request.nodeId.take(16)}", style = MaterialTheme.typography.titleMedium)
                Text("Fingerprint ${PeerCrypto.fingerprint(request.publicKey)}", style = MaterialTheme.typography.bodySmall)
                Text("Verify this fingerprint on the other device. Approval allows inference in scope ${request.requestedTier}.")
                Row {
                    TextButton(onClick = { scope.launch(Dispatchers.IO) {
                        val result = app.trustStore.upsert(DeviceTrustPolicy(request.nodeId,
                            TrustTier.fromTag(request.requestedTier) ?: TrustTier.LOCAL_SANDBOXED,
                            setOf(ClusterRoleTag.BRAIN), publicKeyFingerprint = PeerCrypto.fingerprint(request.publicKey)))
                        if (result is MeshlitResult.Success) authority.dismiss(request.nodeId)
                    } }) { Text("Approve compute") }
                    TextButton(onClick = { authority.dismiss(request.nodeId) }) { Text("Dismiss") }
                }
            } }
        }
        item { Text("Saved devices · ${endpoints.size}", style = MaterialTheme.typography.titleLarge) }
        if (endpoints.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
            Text("Start with another device", style = MaterialTheme.typography.titleMedium)
            Text("Use a pairing QR, paste a link, or enter an endpoint. Both devices need a reachable network.")
        } } }
        items(endpoints, key = { it.id }) { ep ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ep.name, style = MaterialTheme.typography.titleLarge)
                    Text(ep.baseUrl, style = MaterialTheme.typography.bodyMedium)
                    Text(if (ep.trusted) "Identity verified · compute approved" else "Saved · awaiting identity verification",
                        color = if (ep.trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ep.baseUrl.startsWith("http://")) Text("HTTP transport · traffic is not encrypted", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            busy = ep.id; error = null
                            scope.launch {
                                try {
                                    val key = ep.notes.substringAfter("identity.key=", "").substringBefore('\n')
                                    require(key.isNotBlank()) { "Scan this device's new QR to obtain its identity key" }
                                    val node = ep.id.removePrefix("node-")
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
                                    val uri = URI(ep.baseUrl)
                                    val local = isPrivateRFC1918(uri.host)
                                    require(uri.scheme == "https" || local) { "Use HTTPS for internet compute" }
                                    val result = withContext(Dispatchers.IO) { runHandshake(
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
                                    if (result is MeshlitResult.Failure) error("${result.error.tag}. Ask the peer owner to approve your request in Devices, then retry.")
                                    settings.trustEndpoint(ep.id, true)
                                    // Legacy forwarding supports only IPv4 on the default port.
                                    if (local && (uri.port == 8080)) registry.bindIdentity(uri.host, node)
                                    notice = "Identity verified. ${if (uri.scheme == "http") "Use a VPN for encrypted transport." else "HTTPS transport."}"
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = "pairing.verification_failed: ${e.message}" }
                                finally { busy = null }
                            }
                        }, enabled = busy == null) { Text(if (busy == ep.id) "Verifying…" else if (ep.trusted) "Renew pairing" else "Verify & approve") }
                        if (ep.trusted) TextButton({ scope.launch { settings.setActiveEndpoint(ep.id) } }) {
                            Text(if (ep.id == activeId) "Primary device" else "Use as primary")
                        }
                        IconButton({ remove = ep }) { Icon(Icons.Default.DeleteOutline, "Remove ${ep.name}") }
                    }
                }
            }
        }
        item { TextButton({ onNavigate("device_info") }) { Text("This device's identity and capabilities") } }
    }
    if (qr && own != null) QrPairingSheet(own!!, ::save, { qr = false })
    if (add) AddDeviceDialog({ add = false }, ::save)
    remove?.let { ep -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${ep.name}?") },
        text = { Text("This removes its endpoint and revokes its compute approval.") },
        confirmButton = { TextButton({ scope.launch {
            koinInject<com.meshlit.peering.PeerSessionStore>().remove(ep.baseUrl)
            settings.removeEndpoint(ep.id)
            val uri = URI(ep.baseUrl); registry.remove(uri.host)
            peers.forget(ep.id.removePrefix("node-")); remove = null
        } }) { Text("Remove") } }, dismissButton = { TextButton({ remove = null }) { Text("Cancel") } }) }
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
