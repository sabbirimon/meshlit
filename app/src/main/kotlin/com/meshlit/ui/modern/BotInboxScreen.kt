package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.bots.BotInbox
import com.meshlit.chat.ChatController
import com.meshlit.di.koinInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun BotInboxScreen(back: () -> Unit) {
    val inbox = koinInject<BotInbox>()
    val chat = koinInject<ChatController>()
    val pending by inbox.pending.collectAsStateWithLifecycle()
    val status by inbox.status.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun run(action: suspend () -> String) {
        scope.launch {
            busy = true
            try { notice = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { notice = error.message ?: "Could not complete this action" }
            finally { busy = false }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = back) { Text("Back") }
            Text("Bot inbox", style = MaterialTheme.typography.headlineSmall)
            Text("Accept signed messages from a bot you control. Messages wait here for review and can be saved as Chat drafts.")
            Text("POST to http://127.0.0.1:${inbox.port}/bot/inbox from this device or through a private tunnel. The receiver stays active with a notification until stopped.")
            Text(status, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && !status.startsWith("Listening") && status !in setOf("Starting", "Stopping"), onClick = {
                    run { inbox.requestStart(); "Starting receiver" }
                }) { Text("Start receiver") }
                OutlinedButton(enabled = !busy, onClick = {
                    run { withContext(Dispatchers.IO) { inbox.stop() }; "Receiver stopped" }
                }) { Text("Stop") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = {
                    run {
                        val secret = withContext(Dispatchers.IO) { inbox.secret() }
                        clipboard.setText(AnnotatedString(secret))
                        "Signing key copied. Share it only with your sender."
                    }
                }) { Text("Copy signing key") }
                TextButton(enabled = !busy, onClick = {
                    run { withContext(Dispatchers.IO) { inbox.rotate() }; "Receiver stopped and key rotated" }
                }) { Text("Rotate key") }
            }
            notice?.let { Text(it) }
            if (pending.isEmpty()) Text("No messages awaiting review.")
        }
        items(pending, key = { "${it.source}/${it.id}" }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${event.source} · ${event.id}", style = MaterialTheme.typography.labelMedium)
                    Text(event.text.orEmpty())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            run { chat.createBotDraft(event.source, event.id, checkNotNull(event.text)); "Chat is ready for review." }
                        }) { Text("Save as Chat draft") }
                        TextButton(enabled = !busy, onClick = {
                            run { withContext(Dispatchers.IO) { inbox.dismiss(event) }; "Message dismissed" }
                        }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
