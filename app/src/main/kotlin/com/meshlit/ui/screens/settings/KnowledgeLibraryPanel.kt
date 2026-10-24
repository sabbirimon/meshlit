package com.meshlit.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.knowledge.KnowledgeLibrary
import com.meshlit.workspace.*
import kotlinx.coroutines.*

@Composable fun KnowledgeLibraryPanel() {
    val library = koinInject<KnowledgeLibrary>()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<KnowledgeSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<KnowledgeHit>>(emptyList()) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; state = withContext(Dispatchers.IO) { library.corpus.snapshot() }; error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message ?: "Knowledge operation failed" }
            finally { busy = false }
        }
    }
    LaunchedEffect(library) { action {} }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) action { library.import(uri) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Knowledge library", style = MaterialTheme.typography.titleLarge)
        Text("Import text, Markdown or CSV and retrieve relevant excerpts with source labels. Stored encrypted on this device. Word matching works offline; this does not train model weights.")
        OutlinedButton(enabled = !busy && state != null, onClick = { picker.launch(arrayOf("text/*", "application/json")) }) { Text("Import document") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state?.let { snapshot ->
            Text("${snapshot.documents.size} / ${KnowledgeCorpus.MAX_DOCUMENTS} documents · ${snapshot.documents.sumOf { it.text.length }} characters")
            fun policy(value: KnowledgePolicy) { action { library.corpus.setPolicy(value) } }
            KnowledgeSwitch("Use excerpts in local chat", snapshot.policy.localChat, !busy) { policy(snapshot.policy.copy(localChat = it)) }
            KnowledgeSwitch("Allow excerpts in remote / routed chat", snapshot.policy.remoteChat, !busy) { policy(snapshot.policy.copy(remoteChat = it)) }
            KnowledgeSwitch("Allow delegated agents to read excerpts", snapshot.policy.agents, !busy) { policy(snapshot.policy.copy(agents = it)) }
            Text("All controls start off. Remote routes, cluster inference and tool-enabled chats also require remote sharing. The read-only knowledge_search MCP tool requires both agent access and remote sharing. Turning a grant off stops future retrieval; already sent messages cannot be recalled.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { if (it.length <= 1000) query = it }, Modifier.fillMaxWidth(), label = { Text("Find in your documents") }, maxLines = 3)
            TextButton(enabled = !busy && query.isNotBlank(), onClick = { action { val found = library.corpus.search(query); withContext(Dispatchers.Main) { hits = found } } }) { Text("Search excerpts") }
            hits.forEach { hit -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(hit.title, style = MaterialTheme.typography.titleSmall); Text("${hit.source} · part ${hit.chunk + 1}", style = MaterialTheme.typography.labelSmall); Text(hit.text) } } }
            snapshot.documents.forEach { document ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(document.title, style = MaterialTheme.typography.titleMedium)
                    Text("${document.text.length} characters · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(document.addedAt))}", style = MaterialTheme.typography.bodySmall)
                    Text("ID ${document.id.take(12)} · ${document.source}", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy, onClick = { action { library.corpus.delete(document.id); withContext(Dispatchers.Main) { hits = hits.filterNot { it.documentId == document.id } } } }) { Text("Remove document") }
                } }
            }
        }
    }
}

@Composable private fun KnowledgeSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp)); Switch(checked, change, enabled = enabled)
    }
}
