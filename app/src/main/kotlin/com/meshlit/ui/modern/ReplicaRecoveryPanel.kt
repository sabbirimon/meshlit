package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.recovery.ReplicaHost
import com.meshlit.core.federation.recovery.JournalEntry
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@Composable fun ReplicaRecoveryPanel() {
    val host = koinInject<ReplicaHost>(); val running by host.running.collectAsState()
    val backend = koinInject<com.meshlit.control.AgentBackend>()
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    var config by remember { mutableStateOf(host.saved()) }; var entry by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Not queried") }; var busy by remember { mutableStateOf(false) }
    DisposableEffect(host) { onDispose { host.stop() } }
    fun run(action: suspend () -> String) { scope.launch {
        busy = true
        try { result = withContext(Dispatchers.IO) { action() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { result = "Operation refused or uncertain. Inspect membership, quorum and journal; retry with the same request ID." }
        finally { busy = false }
    } }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Replicated recovery journal", style = MaterialTheme.typography.titleMedium)
        Text("Advanced manual setup: 3 or 5 independently approved replicas. Encrypted local storage, signed majority commits and fenced task ownership. Keep this screen open for the loopback listener; use pinned TLS or explicit private SSH tunnels between nodes.")
        Text("This records bounded replay state and hash references. Automatic task/model restart, portable KV transfer, membership changes and hardware fault proof are pending. Completed external actions are never replayed automatically.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { run { clipboard.setText(AnnotatedString(host.publicIdentity())); "Public identity copied" } }) { Text("Copy public key") }
            TextButton(onClick = { clipboard.setText(AnnotatedString(host.token())); result = "Secret copied to clipboard; use only for the approved replica endpoint" }) { Text("Copy token") }
        }
        OutlinedTextField(config, { config = it }, label = { Text("Replica configuration JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run { host.save(config); "Configuration saved; listener stopped" } }) { Text("Save") }
            OutlinedButton(enabled = !busy && !com.meshlit.BuildConfig.PLAY_REVIEW, onClick = { run { host.start(); "Replica listener running on loopback" } }) { Text("Start") }
            TextButton(onClick = { host.stop() }) { Text("Stop") }
        }
        Text(if (running) "Listener running" else "Listener stopped")
        OutlinedTextField(entry, { entry = it }, label = { Text("Explicit journal entry JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { run {
                val commits = host.read()
                "Verified ${commits.size} committed entries. " + (commits.lastOrNull()?.value?.entry?.let { "Last task ${it.taskId}: ${it.action}, owner ${it.owner}, epoch ${it.epoch}, offset ${it.offset}" } ?: "Journal empty")
            } }) { Text("Read quorum") }
            Button(enabled = !busy && entry.isNotBlank(), onClick = { run {
                val value = Json.decodeFromString<JournalEntry>(entry); val commit = host.append(value)
                "Durable quorum commit at slot ${commit.value.slot}; ${commit.votes.size} signed acknowledgements"
            } }) { Text("Commit entry") }
        }
        TextButton(enabled = !busy, onClick = { host.rotateToken(); result = "Token rotated; update peer endpoint credentials" }) { Text("Revoke / rotate token") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(result)
        Text("Publish local agent job metadata explicitly. This copies operation/phase/request ID only; prompts, replies, paths, credentials and automatic replay are excluded. Existing remote ownership must match this installation.")
        OutlinedButton(enabled = !busy, onClick = { run {
            backend.controller.ready.await()
            val jobs = backend.controller.jobs.value.takeLast(10)
            var committed = 0
            for (job in jobs) { host.publishJobMetadata(job); committed++ }
            "Published $committed actual local job references (at most 10); an error can leave a partial batch. Inspect quorum before retry."
        } }) { Text("Publish current job metadata") }
    } }
}
