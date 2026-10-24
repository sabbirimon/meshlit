package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.LinkOption

@Composable internal fun KnowledgePanel(library: DesktopKnowledge) {
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(library.isUnlocked()) }
    var passphrase by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<KnowledgeSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<KnowledgeHit>>(emptyList()) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                unlocked = library.isUnlocked()
                state = if (unlocked) withContext(Dispatchers.IO) { library.lockedCorpus().snapshot() } else null
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message ?: "Knowledge operation failed" }
            finally { busy = false }
        }
    }
    LaunchedEffect(library) { action {} }
    Text("Knowledge library", style = MaterialTheme.typography.h6)
    Text("Encrypted local documents with offline word matching and source labels. The vault uses a separate passphrase; it must be unlocked after each app restart. There is no password recovery.")
    if (!unlocked) {
        OutlinedTextField(passphrase, { if (it.length <= 256) passphrase = it }, label = { Text("Library passphrase · at least 12 characters") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        Button(enabled = !busy && passphrase.length >= 12, onClick = {
            val password = passphrase.toCharArray(); passphrase = ""
            action { try { library.unlock(password) } finally { password.fill('\u0000') } }
        }) { Text("Create or unlock library") }
    } else {
        OutlinedButton(enabled = !busy, onClick = { library.lock(); state = null; matches = emptyList(); unlocked = false }) { Text("Lock library") }
        Text("Turning off retrieval or locking stops future lookups. Excerpts already sent to a model or host cannot be recalled.", style = MaterialTheme.typography.caption)
        state?.let { current ->
            Row { Checkbox(current.policy.localChat, { value -> action { library.lockedCorpus().setPolicy(current.policy.copy(localChat = value)) } }, enabled = !busy); Text("Use excerpts in local chat") }
            Row { Checkbox(current.policy.remoteChat, { value -> action { library.lockedCorpus().setPolicy(current.policy.copy(remoteChat = value)) } }, enabled = !busy); Text("Allow excerpts to the selected remote host") }
            Text("${current.documents.size} / ${KnowledgeCorpus.MAX_DOCUMENTS} documents · ${current.documents.sumOf { it.text.length }} characters")
            Button(enabled = !busy, onClick = {
                val chooser = javax.swing.JFileChooser().apply { dialogTitle = "Import UTF-8 text, Markdown, CSV, TSV or JSON"; fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Text documents", "txt", "md", "csv", "tsv", "json"); isAcceptAllFileFilterUsed = false }
                if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                    val path = chooser.selectedFile.toPath()
                    action {
                        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) { "Choose a regular text file" }
                        val bytes = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(131_073) }
                        require(bytes.size <= 131_072) { "Document exceeds 128 KiB" }
                        val name = path.fileName.toString().take(160)
                        library.lockedCorpus().importText(name, "local:$name", bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
                    }
                }
            }) { Text("Import document") }
            OutlinedTextField(query, { query = it.take(1000) }, label = { Text("Find in your documents") }, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !busy && query.isNotBlank(), onClick = { action { val found = library.lockedCorpus().search(query); withContext(Dispatchers.Main) { matches = found } } }) { Text("Search excerpts") }
            matches.forEach { hit -> Text("${hit.title} · ${hit.source} · part ${hit.chunk + 1}\n${hit.text}") }
            current.documents.forEach { document ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(document.title, style = MaterialTheme.typography.subtitle1)
                    Text("${document.source} · ${document.id.take(12)} · ${document.text.length} characters", style = MaterialTheme.typography.caption)
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(document.addedAt)), style = MaterialTheme.typography.caption)
                    TextButton(enabled = !busy, onClick = { action { library.lockedCorpus().delete(document.id); withContext(Dispatchers.Main) { matches = matches.filterNot { it.documentId == document.id } } } }) { Text("Remove document") }
                } }
            }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colors.error) }
}
