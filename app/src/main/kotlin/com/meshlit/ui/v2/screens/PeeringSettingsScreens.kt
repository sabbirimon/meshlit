package com.meshlit.ui.v2.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.trust.*
import com.meshlit.di.koinInject
import com.meshlit.peering.*
import kotlinx.coroutines.*

@Composable
fun RelaySettingsScreen() {
    val relay: RelayManager = koinInject(); val state by relay.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val scope = rememberCoroutineScope(); var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.enabled, state.expiresAtMs) {
        while (state.enabled) { now = System.currentTimeMillis(); delay(1000) }
    }
    val secondsLeft = ((state.expiresAtMs - now + 999) / 1000).coerceAtLeast(0)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Host a cluster", style = MaterialTheme.typography.headlineLarge)
            Text("This device manages enrollment and member presence. Keep it online and reachable.") }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable relay", Modifier.weight(1f))
            Switch(state.enabled, { enabled -> scope.launch(Dispatchers.IO) {
                try { if (enabled) relay.enable() else relay.disable() }
                catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } }
            } })
        } }
        if (state.enabled) {
            item { Text("http://${app.localIpAddress}:${relay.port}", style = MaterialTheme.typography.titleMedium)
                Text("Use HTTPS or a VPN for internet access. HTTP enrollment exposes traffic on the local network.") }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
                Text("Join code", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(if (secondsLeft > 0) state.code else "Refreshing…", style = MaterialTheme.typography.displayMedium)
                }
                Text("Refreshes automatically in %02d:%02d".format(java.util.Locale.ROOT, secondsLeft / 60, secondsLeft % 60),
                    color = MaterialTheme.colorScheme.primary)
                Text("Share the address and current code with your devices. An expired code cannot enroll new members.")
                TextButton(onClick = { scope.launch(Dispatchers.IO) { relay.rotateCode() } }) { Text("Create new code") }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val link = relay.invite()
                            withContext(Dispatchers.Main) {
                                (app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Cluster invite", link))
                            }
                        } catch (e: Exception) { withContext(Dispatchers.Main) { error = e.message } }
                    }
                }, enabled = secondsLeft > 0) { Text("Copy identity-pinned invite link") }
            } } }
            item { Text("Connected members · ${state.members.size}", style = MaterialTheme.typography.titleLarge) }
            items(state.members, key = { it.nodeId }) { member -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { Text(member.name.ifBlank { member.nodeId.take(12) })
                    Text("Identity ${PeerCrypto.fingerprint(member.publicKey).take(16)}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scope.launch(Dispatchers.IO) { relay.revoke(member.nodeId) } }) { Text("Disconnect member") } }
            } }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
fun RelayJoinScreen() {
    val relay: RelayManager = koinInject(); val state by relay.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Join your cluster", style = MaterialTheme.typography.headlineLarge)
        Text("Enter the relay address and its current six-digit code.")
        OutlinedTextField(url, { url = it }, label = { Text("Relay address or invite link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Join code") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { busy = true; error = null; scope.launch {
            try { relay.join(url, code); code = "" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        } }, enabled = !busy && (code.length == 6 || url.startsWith("meshlit://join")) && url.isNotBlank()) { Text(if (busy) "Joining…" else "Join cluster") }
        if (state.joinedUrl != null) {
            Text("Connected to ${state.joinedUrl}", color = MaterialTheme.colorScheme.primary)
            Text("The relay appears in the Internet peer list. Model execution approval is managed in Devices.")
            TextButton(onClick = relay::leave) { Text("Leave cluster") }
        }
        (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun RecoveryScreen() {
    val context = LocalContext.current; val app = context.applicationContext as MeshlitApplication
    val identity: DeviceIdentity = koinInject(); val scope = rememberCoroutineScope()
    var phrase by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        var current: android.content.Context = context
        while (current is android.content.ContextWrapper && current !is android.app.Activity) current = current.baseContext
        val window = (current as? android.app.Activity)?.window
        val wasSecure = window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var backup by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<RecoverableIdentity?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(backup.toByteArray()) } ?: error("Cannot open backup file") }
                message = "Backup saved. Keep it and your passphrase to restore after reinstalling."; backup = ""; phrase = ""
            } catch (e: Exception) { message = "recovery.export_failed: ${e.message}" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { backup = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use {
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(1024)
                while (true) { val count = it.read(buffer); if (count < 0) break
                    require(output.size() + count <= 16384) { "Backup too large" }; output.write(buffer, 0, count) }
                output.toString("UTF-8")
            } ?: error("Cannot read backup") }; message = "Backup imported. Enter its passphrase, then restore." }
            catch (e: Exception) { message = "recovery.import_failed: ${e.message}" }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Keep your identity", style = MaterialTheme.typography.headlineLarge)
            Text("Your encrypted backup contains the node ID and signing keys. Both the backup file and its passphrase are needed after uninstalling. Rewards and other settings are separate.") }
        item { OutlinedTextField(phrase, { phrase = it }, label = { Text("Recovery passphrase (12+ characters)") },
            visualTransformation = if (reveal) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = { phrase = PeerCrypto.randomHex(20).chunked(5).joinToString("-") }) { Text("Generate phrase") }
                TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide phrase" else "Show phrase") }
            }
        }
        item { Button(onClick = { busy = true; scope.launch {
            val chars = phrase.toCharArray()
            try { backup = withContext(Dispatchers.IO) { PeerCrypto.backup(identity.get(app.nodeIdHex), chars) }; export.launch("meshlit-identity-backup.txt") }
            catch (e: Exception) { message = "recovery.export_failed: ${e.message}" }
            finally { chars.fill('\u0000'); busy = false }
        } }, enabled = phrase.length >= 12 && !busy) { Text("Save encrypted backup") } }
        item { OutlinedButton(onClick = { import.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = !busy) { Text("Import backup") } }
        if (backup.isNotBlank()) item { OutlinedButton(onClick = { busy = true; scope.launch {
            val chars = phrase.toCharArray()
            try { restored = withContext(Dispatchers.IO) { PeerCrypto.restore(backup, chars) } }
            catch (e: Exception) { message = "recovery.restore_failed: Wrong passphrase or invalid backup" }
            finally { chars.fill('\u0000'); busy = false }
        } }, enabled = phrase.length >= 12 && !busy) { Text("Restore identity") } }
        message?.let { item { Text(it) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    restored?.let { key -> AlertDialog(onDismissRequest = { restored = null }, title = { Text("Restore this identity?") },
        text = { Text("Current: ${app.nodeIdHex}\nRecovered: ${key.nodeId}\nActive relay sessions will close.") },
        confirmButton = { TextButton(onClick = { scope.launch {
            try { withContext(Dispatchers.IO) {
                val relay: RelayManager = koinInject(); relay.leave(); relay.disable()
                identity.save(key)
                val config: com.meshlit.core.config.ConfigRepository = koinInject()
                require(config.set(com.meshlit.core.config.BuiltInConfigKeys.nodeId(), key.nodeId) is com.meshlit.core.common.MeshlitResult.Success)
                app.setStableNodeId(key.nodeId)
                val peers: com.meshlit.disco.PeerRepository = koinInject()
                peers.stop(); peers.start(app.appScope, com.meshlit.core.discovery.LocalPeerDescriptor(key.nodeId,
                    app.localIpAddress, app.httpServerPort, "local_sandboxed", PeerCrypto.fingerprint(key.publicKey),
                    app.displayName, android.os.Build.MANUFACTURER.orEmpty(), android.os.Build.MODEL.orEmpty()))
            }; message = "Identity restored: ${key.nodeId}"; backup = ""; phrase = ""; restored = null }
            catch (e: Exception) { message = "recovery.save_failed: ${e.message}" }
        } }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { restored = null }) { Text("Cancel") } }) }
}

@Composable
fun RewardsScreen(onBack: (() -> Unit)? = null) {
    val store: ContributionStore = koinInject(); val state by store.state.collectAsStateWithLifecycle()
    val metrics: com.meshlit.inference.MetricsRegistry = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMeasurements by rememberSaveable { mutableStateOf(false) }
    var measureTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(showMeasurements) { while(showMeasurements) { delay(2_000);measureTick++ } }
    if(showMeasurements) {
        val live=remember(measureTick) { metrics.snapshot() }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            TextButton(onClick={showMeasurements=false}) { Text("Back to rewards") }
            Text("Contribution measurements",style=MaterialTheme.typography.headlineMedium)
            Text("Durable receipts and live inference counters have different scopes. Counts reset on process restart; signed receipts remain on this device.",style=MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Signed local receipt ledger",style=MaterialTheme.typography.titleMedium)
                Text("${state.jobs} completed hosted jobs · ${state.credits} reported output tokens")
                Text("One receipt per job ID. Signatures detect changes to this device's ledger, but the device owner can still control its own measurements. This is not independent proof of service.",style=MaterialTheme.typography.bodySmall)
            } }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Current process · live",style=MaterialTheme.typography.titleMedium)
                Text("Accepted ${live.totalJobs} · succeeded ${live.successJobs} · failed ${live.failureTags.values.sum()} · queued ${live.queueDepth}")
                Text("Runtime-reported output tokens ${live.totalTokensGenerated}")
                Text("Average token rate: ${if(live.avgTokensPerSecond>0f) "%.1f tokens/s".format(java.util.Locale.ROOT,live.avgTokensPerSecond) else "unavailable"}")
                Text("Process uptime ${live.uptimeSeconds}s · resets on restart",style=MaterialTheme.typography.bodySmall)
            } }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("Reward proof and privacy",style=MaterialTheme.typography.titleMedium)
                Text("Participation is off unless you enable local receipts on the wallet page. No measurement or log is automatically uploaded from this screen.")
                Text("A future redeemable reward would require a requester-signed job receipt, independent verifier checks, bounded work and energy measurements, fraud review, and a dispute period. Participants would opt in to sharing redacted proof records with named verifiers; raw chats, secrets and full system logs must stay private.",style=MaterialTheme.typography.bodySmall)
                Text("Energy, CPU time, bytes transferred, requester confirmation and monetary value: not measured here yet.",style=MaterialTheme.typography.bodySmall)
            } }
        }
        return
    }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try { val data = store.exportReceipts(); context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("Cannot open receipt backup")
                withContext(Dispatchers.Main) { transferMessage = "Signed receipt backup saved" } }
            catch (e: Exception) { withContext(Dispatchers.Main) { transferMessage = "rewards.export_failed: ${e.message}" } }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        require(output.size() + n <= 16 * 1024 * 1024) { "Backup too large" }; output.write(buffer, 0, n) }
                    output.toString("UTF-8")
                } ?: error("Cannot read backup")
                store.importReceipts(raw)
                withContext(Dispatchers.Main) { transferMessage = "Receipts restored and signatures verified" }
            } catch (e: Exception) { withContext(Dispatchers.Main) { transferMessage = "rewards.import_failed: ${e.message}" } }
        }
    }
    LaunchedEffect(store) { withContext(Dispatchers.IO) { store.refresh() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mesh rewards", style = MaterialTheme.typography.headlineMedium)
                Text("Your local contribution record", style = MaterialTheme.typography.bodyMedium)
            }
            if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
        }
        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LOCAL CREDITS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${state.credits}", style = MaterialTheme.typography.displayMedium)
            Text("${state.jobs} completed hosted jobs", style = MaterialTheme.typography.bodyMedium)
            Text(if (state.walletId.isBlank()) "Loading wallet identity…" else "Wallet ${state.walletId.take(24)}", style = MaterialTheme.typography.bodySmall)
        } }
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Opt in to local receipts", style = MaterialTheme.typography.titleMedium)
                Text("Save signed records for completed hosted jobs. Off stops new records; existing records remain until you delete app data.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(state.enabled, { enabled -> scope.launch(Dispatchers.IO) { store.setEnabled(enabled) } })
        } }
        OutlinedButton(onClick={showMeasurements=true},modifier=Modifier.fillMaxWidth()) { Text("Contribution measurements and proof") }
        Text("Recent contributions", style = MaterialTheme.typography.titleLarge)
        if (state.recent.isEmpty()) {
            Text("No verified hosted jobs yet. Credits appear here after a completed local HTTP inference job reports output tokens.", style = MaterialTheme.typography.bodyMedium)
        } else state.recent.forEach { receipt ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Job ${receipt.jobId.take(8)}", style = MaterialTheme.typography.titleSmall)
                    Text("Signature verified", style = MaterialTheme.typography.bodySmall)
                }
                Text("+${receipt.generatedTokens}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
            Text("Receipts can be restored only with this wallet identity.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { export.launch("meshlit-signed-receipts.json") }) { Text("Back up receipts") }
            TextButton(onClick = { import.launch(arrayOf("application/json", "text/*")) }) { Text("Restore receipts") }
            transferMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        } }
        Text("One local credit per measured output token. Failed, empty, duplicate and forwarded jobs earn none. These self-signed credits are not transferable or redeemable for money.", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
