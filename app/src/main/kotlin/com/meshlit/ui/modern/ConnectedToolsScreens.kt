package com.meshlit.ui.modern
import android.Manifest
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.crypto.CryptoHost
import com.meshlit.core.mcp.crypto.CryptoEngine
import com.meshlit.gibberlink.GibberLinkHost
import com.meshlit.remote.RemoteCommandHost
import com.meshlit.p2p.PeerChatHost
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ToolPage(title: String, back: () -> Unit, body: LazyListScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(expandedHeight = 48.dp, title = { Text(title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
    }
}
@Composable private fun GrantRow(title: String, on: Boolean, change: (Boolean) -> Unit) {
    Row { Text(title, Modifier.weight(1f)); Switch(on, change) }
}
@Composable fun CryptoScreen(back: () -> Unit) {
    val host = koinInject<CryptoHost>(); val agents by host.agents.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    var op by remember { mutableStateOf("sha256") }; var input by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }
    var aad by remember { mutableStateOf("") }; var expected by remember { mutableStateOf("") }; var result by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    ToolPage("Local cryptography", back) {
        item { Text("🔐 Work locally", style = MaterialTheme.typography.titleLarge); Text("Hashes, random keys, HMAC and authenticated encryption. Keys and inputs stay in this screen's memory. No automatic clipboard, network or saved key history.") }
        item { Row { Text("Operation: $op", Modifier.weight(1f)); TextButton(onClick = { op = CryptoEngine.operations[(CryptoEngine.operations.indexOf(op) + 1) % CryptoEngine.operations.size]; result = "" }) { Text("Change") } } }
        if (op != "random_key") item { OutlinedTextField(input, { input = it.take(140000) }, Modifier.fillMaxWidth(), label = { Text(if (op == "decrypt") "ML1 encrypted envelope" else "Text") }, maxLines = 5) }
        if (op in setOf("encrypt", "decrypt", "hmac_sha256", "hmac_verify")) item { OutlinedTextField(key, { key = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Random hex key · AES needs 64 characters") }, visualTransformation = PasswordVisualTransformation()); Text("Use random bytes, not a password. The key is never embedded in the ciphertext.", style = MaterialTheme.typography.bodySmall) }
        if (op in setOf("encrypt", "decrypt")) item { OutlinedTextField(aad, { aad = it.take(8192) }, Modifier.fillMaxWidth(), label = { Text("Authentication data · must match on decrypt") }) }
        if (op == "hmac_verify") item { OutlinedTextField(expected, { expected = it.take(64) }, Modifier.fillMaxWidth(), label = { Text("Expected HMAC hex") }) }
        item { Button(enabled = !busy, onClick = { scope.launch { busy = true; result = ""; try { result = host.run(op, input, key, aad, expected) } catch (e: CancellationException) { throw e } catch (_: Exception) { result = "Failed: check input, key, authentication data and operation switches." } finally { busy = false } } }) { Text(if (busy) "Working…" else "Run locally") } }
        if (result.isNotEmpty()) item { SelectionContainer { Text(result) }; TextButton(onClick = { result = ""; input = ""; key = ""; aad = ""; expected = "" }) { Text("Clear inputs and result") } }
        if (!com.meshlit.BuildProfile.coreCandidate) item { GrantRow("Allow agents to use cryptography", agents, host::saveAgentGrantHuman); Text("Also enable this chat's cryptography tools. Tool arguments, keys and results are visible to the model and saved chat. Keep private credentials in the credential vault instead.", style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable fun GibberLinkScreen(back: () -> Unit) {
    val host = koinInject<GibberLinkHost>(); val policy by host.policy.collectAsStateWithLifecycle(); val transcript by host.transcripts.collectAsStateWithLifecycle(); val status by host.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope(); val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var commandRequest by remember { mutableStateOf("") }; var discoveryResult by remember { mutableStateOf("") }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) error = "Microphone permission declined" }
    DisposableEffect(host, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> host.visible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer); host.visible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); host.visible(false) }
    }
    LaunchedEffect(host, policy.enabled, policy.autoRespond, lifecycleState) {
        while (isActive && policy.enabled && policy.autoRespond && lifecycleState == Lifecycle.State.RESUMED) {
            try {
                val packet = host.listen(5)
                if (packet != null) host.respondToDiscovery(packet)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { delay(1000) }
        }
    }
    fun work(action: suspend () -> Unit) { scope.launch { busy = true; error = null; try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Audio failed: check foreground visibility, microphone permission and operation grants." } finally { busy = false } } }
    ToolPage("GibberLink · English transcript", back) {
        item { Text("📡 English in, sound across", style = MaterialTheme.typography.titleLarge); Text("Experimental ggwave audio packets. Actual sent and decoded English text appears below. Nearby people can record/decode these sounds: there is no encryption, sender identity or delivery acknowledgment. Never send secrets.") }
        item { GrantRow("Enable GibberLink audio", policy.enabled) { host.saveHuman(policy.copy(enabled = it)) }; GrantRow("Allow agents to send/listen", policy.agents) { host.saveHuman(policy.copy(agents = it)) }
            GrantRow("Reply to nearby modem discovery", policy.autoRespond) { host.saveHuman(policy.copy(autoRespond = it)) } }
        item { Text("Keep this screen visible and the phone awake. Agents also need Media/automation operation grants and audio tools for this chat. Incoming text is untrusted and never auto-executes commands.", style = MaterialTheme.typography.bodySmall) }
        item { Text("Acoustic discovery", style = MaterialTheme.typography.titleMedium)
            Text("A fresh challenge can detect another compatible modem nearby. It cannot prove that the sender is an AI or an approved device. The responder needs the reply switch and microphone permission.")
            Button(enabled = policy.enabled && !policy.autoRespond && !busy, onClick = { work { discoveryResult = if (host.discover()) "Compatible nearby modem replied; identity unverified" else "No matching reply in 20 seconds" } }) { Text("Discover nearby modem") }
            if (discoveryResult.isNotBlank()) Text(discoveryResult) }
        item { OutlinedTextField(text, { text = it.take(96) }, Modifier.fillMaxWidth(), label = { Text("English packet · 1–96 printable characters") }); Text("Status: $status") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = policy.enabled && !policy.autoRespond && !busy, onClick = { work { host.send(text) } }) { Text("Send sound") }; OutlinedButton(enabled = policy.enabled && !policy.autoRespond && !busy, onClick = { work { if (host.listen() == null) error = "No complete English packet received in 20 seconds" } }) { Text("Listen") }; TextButton(onClick = host::stop) { Text("Stop") } } }
        item { Text("Command requests", style = MaterialTheme.typography.titleMedium)
            Text("Send a short request when IP is unavailable. The receiving transcript shows it for review; this channel does not execute commands or establish trust.")
            OutlinedTextField(commandRequest, { commandRequest = it.take(89) }, Modifier.fillMaxWidth(), label = { Text("Command request · 1–89 printable characters") })
            OutlinedButton(enabled = policy.enabled && !policy.autoRespond && !busy && commandRequest.isNotBlank(), onClick = { work { host.sendCommandRequest(commandRequest) } }) { Text("Send command request") } }
        item { TextButton(onClick = { microphone.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone permission") }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Text("English transcript", style = MaterialTheme.typography.titleMedium); TextButton(onClick = host::clearHuman) { Text("Clear session transcript") } }
        items(transcript.size) { i -> val entry = transcript[i]; SelectionContainer { Column { Text("${entry.direction} · ${java.text.DateFormat.getTimeInstance().format(java.util.Date(entry.timeMillis))}", style = MaterialTheme.typography.labelSmall); Text(entry.english); Text(entry.status, style = MaterialTheme.typography.bodySmall) } } }
    }
}
@Composable fun RemoteCommandsScreen(back: () -> Unit) {
    val host = koinInject<RemoteCommandHost>(); val policy by host.policy.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<String>()) }; var result by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    ToolPage("Device and cluster commands", back) {
        item { Text("🌐 Your approved nodes", style = MaterialTheme.typography.titleLarge); Text("Command 1–8 saved SSH nodes over LAN or an already approved private Internet transport. For distant phones without VPN, use P2P chat and commands. Node enrollment, host-key verification and scopes remain human controlled.") }
        item { GrantRow("Enable remote commands", policy.enabled) { host.saveHuman(policy.copy(enabled = it)) }; GrantRow("Allow agents to send commands", policy.agents) { host.saveHuman(policy.copy(agents = it)) }; Text("Agents may pause/resume their session within these grants. Revocation cancels local transports; a remote mutation may already have occurred.") }
        item { Text("Configure nodes in Settings → SSH. Only separately delegated node actions appear. These controls use the same restricted command path for humans.") }
        host.nodes().forEach { node -> item { Row { Checkbox(node.id in selected, { on -> selected = if (on && selected.size < 8) selected + node.id else selected - node.id }); Text(node.name) } } }
        item { Button(enabled = policy.enabled && selected.isNotEmpty() && !busy, onClick = { scope.launch { busy = true; try { result = host.request(selected.toList(), com.meshlit.core.ssh.NodeSshAction.STATUS, emptyList(), false).toString() } catch (e: CancellationException) { throw e } catch (_: Exception) { result = "Request failed; verify receiver scopes, host pin and connection. No automatic replay." } finally { busy = false } } }) { Text(if (busy) "Requesting…" else "Request actual status") } }
        if (result.isNotEmpty()) item { SelectionContainer { Text(result) } }
    }
}
class PeerJavascriptBridge(private val host: PeerChatHost) {
    @JavascriptInterface fun event(data: String) { host.event(data) }
}
@Composable fun PeerChatScreen(back: () -> Unit) {
    val host = koinInject<PeerChatHost>(); val policy by host.policy.collectAsStateWithLifecycle(); val status by host.status.collectAsStateWithLifecycle(); val history by host.history.collectAsStateWithLifecycle()
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val gate = koinInject<com.meshlit.operations.OperationsControl>().gate; val operationPolicy by gate.policy.collectAsStateWithLifecycle()
    val allowed = runCatching { gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.GATEWAY) }.isSuccess && !operationPolicy.emergencyStopped
    var html by remember { mutableStateOf<String?>(null) }; var web by remember { mutableStateOf<WebView?>(null) }; var text by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { html = withContext(Dispatchers.IO) { context.assets.open("p2p/index.html").bufferedReader().use { it.readText() } } }
    DisposableEffect(host) { onDispose { host.unbind() } }
    LaunchedEffect(policy.enabled, state, allowed) { if (!policy.enabled || state != Lifecycle.State.RESUMED || !allowed) { host.unbind(); web = null } }
    fun work(action: suspend () -> Unit) { scope.launch { error = null; try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "P2P request failed or timed out. Verify actual connection and receiver grants; outcome may be unknown." } } }
    ToolPage("Mesh chat · direct peer", back) {
        item { Text("🔗 Phone to phone · no VPN", style = MaterialTheme.typography.titleLarge); Text("One live peer conversation on this screen. Signed manual pairing + encrypted WebRTC data channels. Direct over LAN/Internet where ICE succeeds. Optional TURN is separately approved; selected route is reported from actual connection stats. Multi-member rooms and offline message delivery are not yet available. Native model-layer streaming is not carried by this chat channel.") }
        item { GrantRow("Enable foreground P2P", policy.enabled) { host.saveHuman(policy.copy(enabled = it)) }; GrantRow("Allow local agents to send / remote agents to act", policy.agents) { host.saveHuman(policy.copy(agents = it)) } }
        item { GrantRow("Accept scoped peer commands", policy.receiveCommands) { host.saveHuman(policy.copy(receiveCommands = it)) }; GrantRow("Allow peer VM start/stop", policy.vmControl) { host.saveHuman(policy.copy(vmControl = it)) }; GrantRow("Allow peer cluster worker start/stop", policy.clusterControl) { host.saveHuman(policy.copy(clusterControl = it)) } }
        item { Text("All remote commands use agent permissions on this phone, including commands sent by another human. VM/cluster delegation and operation switches also apply. Pairing does not grant root, shell, credentials, settings changes or native worker enrollment. Closing this screen stops the connection.", style = MaterialTheme.typography.bodySmall); Text(status) }
        if (policy.enabled && state == Lifecycle.State.RESUMED && allowed && html != null) item {
            AndroidView(modifier = Modifier.fillMaxWidth().height(560.dp), onRelease = { owned -> host.unbind(); owned.removeJavascriptInterface("MeshlitPeer"); owned.stopLoading(); owned.destroy(); if (web === owned) web = null }, factory = { WebView(it).apply {
                settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.domStorageEnabled = false; settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                addJavascriptInterface(PeerJavascriptBridge(host), "MeshlitPeer")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) { handler.cancel() }
                }
                webChromeClient = object : WebChromeClient() { override fun onPermissionRequest(request: PermissionRequest) { request.deny() } }
                loadDataWithBaseURL("https://peer.meshlit.invalid/", html!!, "text/html", "UTF-8", null)
                host.bind({ packet -> kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation -> post {
                    if (continuation.isActive) evaluateJavascript("(() => { try { window.meshSend(${JsonPrimitive(packet)}); return true; } catch (_) { return false; } })()") { value ->
                        if (continuation.isActive) { if (value == "true") continuation.resumeWith(Result.success(Unit)) else continuation.resumeWith(Result.failure(IllegalStateException("Peer channel did not queue the packet"))) }
                    }
                } } }, { post { evaluateJavascript("window.meshClose && window.meshClose()", null) } }); web = this
            } })
        }
        item { OutlinedTextField(text, { text = it.take(8000) }, Modifier.fillMaxWidth(), label = { Text("Message to actual peer") }); Row { Button(onClick = { work { host.chat(text); text = "" } }, enabled = status.startsWith("Connected")) { Text("Send") }; TextButton(onClick = host::disconnect) { Text("Disconnect") }; TextButton(onClick = { work { val result = host.command("STATUS"); error = "Peer result: $result" } }, enabled = status.startsWith("Connected")) { Text("Peer status") } }; error?.let { Text(it) } }
        item { Text("Peer transcript · received content is untrusted", style = MaterialTheme.typography.titleMedium) }
        items(history.size) { i -> SelectionContainer { Column { Text(history[i].direction, style = MaterialTheme.typography.labelSmall); Text(history[i].text) } } }
    }
}
