package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.meshlit.core.ssh.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@Composable internal fun SshPanel() {
    var host by remember { mutableStateOf("") }; var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }; var fingerprint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }; var command by remember { mutableStateOf("status") }
    var allowed by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }; var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }; val scope = rememberCoroutineScope()
    Text("SSH · pinned outbound connections", style = MaterialTheme.typography.h6)
    Text("Enter the host-key SHA256 fingerprint verified on the target, username and unencrypted PEM key. Keys are session-only; no accept-new, forwarding or automatic login. Commands run on the target under that SSH account.")
    OutlinedTextField(host, { host = it.take(253); allowed = false; job?.cancel() }, label = { Text("Host or Internet address") }, modifier = Modifier.fillMaxWidth())
    Row { OutlinedTextField(port, { port = it.take(5); allowed = false; job?.cancel() }, label = { Text("Port") }, modifier = Modifier.weight(1f)); OutlinedTextField(user, { user = it.take(64); allowed = false; job?.cancel() }, label = { Text("Username") }, modifier = Modifier.weight(2f)) }
    OutlinedTextField(fingerprint, { fingerprint = it.take(100); allowed = false; job?.cancel() }, label = { Text("Independently verified SHA256:… host-key pin") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(key, { key = it.take(65536); allowed = false; job?.cancel() }, label = { Text("PEM private key · memory only") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    Row { Checkbox(allowed, { allowed = it; if (!it) job?.cancel() }); Text("Allow my command on this exact SSH target") }
    OutlinedTextField(command, { command = it.take(8000) }, label = { Text("Remote command") }, modifier = Modifier.fillMaxWidth(), maxLines = 4, enabled = !busy)
    Row {
        Button({
            busy = true; failed = false; result = ""
            val config = runCatching { SshConnection("desktop", "Desktop target", host, port.toInt(), user, fingerprint) }
            val secret = key; val instruction = command
            job = scope.launch {
                try {
                    val reply = SshClient().execute(config.getOrThrow(), null, secret, instruction)
                    result = "Exit ${reply.exitCode}${if (reply.truncated) " · truncated" else ""}\n${reply.stdout}\n${reply.stderr}"
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed = true }
                finally { busy = false }
            }
        }, enabled = allowed && !busy && key.isNotBlank()) { Text("Run SSH command") }
        TextButton({ job?.cancel() }, enabled = busy) { Text("Stop") }
    }
    if (failed) Text("SSH failed: check key, target, pin, authentication or timeout. Host-key changes require reapproval.", color = MaterialTheme.colors.error)
    if (result.isNotBlank()) Text(result)
    Divider()
    GhosttyPanel(host, port, user, fingerprint)
    Divider()
    InboundSshPanel()
}

@Composable private fun InboundSshPanel() {
    var bind by remember { mutableStateOf("127.0.0.1") }; var port by remember { mutableStateOf("2223") }
    var publicKey by remember { mutableStateOf("") }; var agent by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }; var pin by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf(false) }
    val server = remember { NodeSshServer({ grant -> check(grant.scopes == setOf(NodeSshScope.STATUS)) }, { _, request ->
        check(request.action == NodeSshAction.STATUS)
        NodeSshReply(true, buildJsonObject { hostReadings().forEach { (name, value) -> put(name, value) } }.toString())
    }) }
    DisposableEffect(Unit) { onDispose { server.stop() } }
    Text("Inbound app node · authenticated status only", style = MaterialTheme.typography.h6)
    Text("While this panel is open, one approved public key can read this desktop's actual status. No shell, SFTP, forwarding, VM or arbitrary execution. Start creates a session host key; verify its displayed pin on the client. Stop or closing this panel revokes access.")
    Row { OutlinedTextField(bind, { bind = it.take(15) }, label = { Text("Loopback or private IPv4") }, modifier = Modifier.weight(2f), enabled = !running); OutlinedTextField(port, { port = it.take(5) }, label = { Text("Port") }, modifier = Modifier.weight(1f), enabled = !running) }
    OutlinedTextField(publicKey, { publicKey = it.take(4096) }, label = { Text("Authorised ECDSA P-256 / RSA 3072+ public key") }, modifier = Modifier.fillMaxWidth(), enabled = !running)
    Row { Checkbox(agent, { agent = it }, enabled = !running); Text("This key belongs to an agent · status scope only") }
    Row {
        Button({ runCatching {
            val identity = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            server.start(NodeSshBind(bind, port.toInt()), identity, listOf(NodeSshGrant("desktop-client", "Approved client", "meshlit", publicKey, agent = agent)))
            pin = checkNotNull(server.fingerprint); running = server.running
        }.onSuccess { failure = false }.onFailure { server.stop(); running = false; failure = true } }, enabled = !running && publicKey.isNotBlank()) { Text("Start status node") }
        TextButton({ server.stop(); running = false; pin = "" }, enabled = running) { Text("Stop / revoke") }
    }
    if (failure) Text("Cannot start node: invalid bind/key, occupied port or transport failure.", color = MaterialTheme.colors.error)
    if (running) Text("Listening on $bind:$port · user meshlit · $pin · command status")
}
