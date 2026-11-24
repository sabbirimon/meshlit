package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.recovery.ReplicaHost
import com.meshlit.recovery.ReplicaChatCheckpoint
import com.meshlit.recovery.ReplicaChatDraft
import com.meshlit.recovery.ReplicaChatTranscript
import com.meshlit.chat.ChatController
import com.meshlit.core.federation.recovery.JournalEntry
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@Composable fun ReplicaRecoveryPanel() {
    val host = koinInject<ReplicaHost>(); val running by host.running.collectAsState()
    val paths by host.paths.collectAsState()
    val backend = koinInject<com.meshlit.control.AgentBackend>()
    val chat = koinInject<ChatController>()
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboardManager.current
    var config by remember { mutableStateOf(host.saved()) }; var entry by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Not queried") }; var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<ReplicaChatDraft?>(null) }
    var chats by remember { mutableStateOf<List<ReplicaChatCheckpoint>>(emptyList()) }
    var selected by remember { mutableStateOf<ReplicaChatCheckpoint?>(null) }
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
        Text("Enroll 3 or 5 independently approved replicas. Keep this screen open while the listener is running. The default is loopback for use with a private tunnel.")
        Text("For direct LAN access, set lanAddress to this phone's private IPv4 address and allow the configured port in Network rules. Use HTTPS and the independently verified deviceCertificateSha256 on each peer's endpoint.")
        Text("This records bounded replay state and hash references. Automatic task/model restart, portable KV transfer, membership changes and hardware fault proof are pending. Completed external actions are never replayed automatically.")
        Text("Each replica endpoint may have up to two independently approved alternatives. selection is ADAPTIVE (observed latency and cooldown) or PRIORITY (configured order). Alternatives reach the same member, not additional quorum voters. Only status reads fail over; uncertain writes are never blindly repeated.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { run { clipboard.setText(AnnotatedString(host.publicIdentity())); "Public identity copied" } }) { Text("Copy public key") }
            TextButton(onClick = { clipboard.setText(AnnotatedString(host.token())); result = "Secret copied to clipboard; use only for the approved replica endpoint" }) { Text("Copy token") }
        }
        TextButton(onClick = { run { clipboard.setText(AnnotatedString(host.certificateFingerprint())); "Device certificate fingerprint copied" } }) { Text("Copy device certificate fingerprint") }
        OutlinedTextField(config, { config = it }, label = { Text("Replica configuration JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run { host.save(config); "Configuration saved; listener stopped" } }) { Text("Save") }
            OutlinedButton(enabled = !busy && !com.meshlit.BuildConfig.PLAY_REVIEW, onClick = { run { host.start() } }) { Text("Start") }
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
        SettingsFeatureSection("Sync paths and observed health") {
            if(paths.isEmpty()) Text("No path observations in this session. Read quorum to check the configured paths.")
            paths.forEach { path ->
                Text("${path.member} · path ${path.pathIndex+1}${if(path.lastSelected) " · last selected" else ""}",style=MaterialTheme.typography.bodyMedium)
                Text("${path.successfulRequests} succeeded · ${path.failedRequests} failed · ${path.cancelledRequests} cancelled · ${path.attempts} attempts")
                Text("Latency: ${path.latencyMs?.let{"$it ms"} ?: "unknown"} · ${if(path.identityRejected) "identity rejected; review configuration" else if(path.cooldownRemainingMs>0) "cooldown at last observation: ${path.cooldownRemainingMs} ms" else "no recorded cooldown"}")
            }
            Text("Actual session HTTP observations include status checks. They are not compute, byte totals, earnings or guaranteed availability. Stop clears observations and cancels transport calls.")
        }
        SettingsFeatureSection("Recover a conversation") {
            Text("Review and commit up to four completed local text turns from the current chat to your configured replica group. The transcript may contain private text; every participating replica can read it. Drafts, tool actions, attachment files and prompt caches are excluded.")
            Text("To recover, load the exact matching GGUF on this device and keep the replica paths reachable. A signed quorum transfers journal ownership. The restored conversation opens in Chat mode with tools off and waits for your next message.")
            OutlinedButton(enabled=!busy && !com.meshlit.BuildConfig.PLAY_REVIEW,onClick={run {
                val preview=chat.prepareReplicaChat()
                withContext(Dispatchers.Main.immediate){draft=preview}
                "Review the exact text before publishing."
            }}){Text("Review current chat for checkpoint")}
            OutlinedButton(enabled=!busy && !com.meshlit.BuildConfig.PLAY_REVIEW,onClick={run {
                val found=host.chatCheckpoints()
                withContext(Dispatchers.Main.immediate){chats=found}
                "Read ${found.size} conversation checkpoints from verified quorum."
            }}){Text("Read conversation checkpoints")}
            chats.forEach { checkpoint ->
                Text("Checkpoint ${checkpoint.sequence} · ${checkpoint.transcript.messages.size/2} turns · model ${checkpoint.modelSha256.take(12)} · owner ${checkpoint.owner}",style=MaterialTheme.typography.bodySmall)
                Text(checkpoint.transcript.messages.first().text.take(100),style=MaterialTheme.typography.bodyMedium)
                TextButton(enabled=!busy,onClick={selected=checkpoint}){Text("Review and recover")}
            }
        }
        Text("Publish local agent job metadata explicitly. This copies operation/phase/request ID only; prompts, replies, paths, credentials and automatic replay are excluded. Existing remote ownership must match this installation.")
        OutlinedButton(enabled = !busy, onClick = { run {
            backend.controller.ready.await()
            val jobs = backend.controller.jobs.value.takeLast(10)
            var committed = 0
            for (job in jobs) { host.publishJobMetadata(job); committed++ }
            "Published $committed actual local job references (at most 10); an error can leave a partial batch. Inspect quorum before retry."
        } }) { Text("Publish current job metadata") }
    } }
    draft?.let { preview ->
        AlertDialog(onDismissRequest={draft=null},title={Text("Publish this text to your replicas?")},
            text={ReplicaTranscriptPreview(preview.transcript)},
            confirmButton={TextButton(onClick={draft=null;run {
                val commit=host.publishChat(preview)
                "Conversation checkpoint ${commit.value.entry.offset} committed; ${commit.votes.size} signed acceptances."
            }}){Text("Commit to quorum")}},dismissButton={TextButton(onClick={draft=null}){Text("Cancel")}})
    }
    selected?.let { checkpoint ->
        AlertDialog(onDismissRequest={selected=null},title={Text("Recover this conversation?")},
            text={ReplicaTranscriptPreview(checkpoint.transcript)},
            confirmButton={TextButton(onClick={selected=null;run {
                chat.verifyReplicaChat(checkpoint)
                val binding=host.claimChat(checkpoint)
                chat.restoreReplicaChat(checkpoint,binding)
                "Recovered conversation selected. Open Chat to continue when ready."
            }}){Text("Claim and restore text")}},dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})
    }

}


@Composable private fun ReplicaTranscriptPreview(transcript:ReplicaChatTranscript) {
    Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        transcript.messages.forEach{line->Text("${if(line.role=="user")"You" else "Assistant"}: ${line.text}")}
    }
}
