package com.meshlit.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LegalDocumentsScreen(onBack: () -> Unit) {
    var document by rememberSaveable { mutableStateOf("privacy") }
    LegalDocument(document, onBack, { document = if (document == "privacy") "terms" else "privacy" })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LegalDocument(document: String, onBack: () -> Unit, switch: (() -> Unit)? = null) {
    val context = LocalContext.current
    var content by remember(document) { mutableStateOf("Loading offline document…") }
    LaunchedEffect(document) {
        content = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("legal/$document.txt").bufferedReader().use { it.readText() } }
                .getOrElse { "Document unavailable. Please retry; acceptance is not required to exit." }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (document == "privacy") "Privacy policy" else "Terms of use") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp)) {
            if (switch != null) item { TextButton(onClick = switch) { Text(if (document == "privacy") "Read terms of use" else "Read privacy policy") } }
            item { SelectionContainer { Text(content, style = MaterialTheme.typography.bodyLarge) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LegalAgreementGate(onDecline: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { LegalAgreementStore(context) }
    var accepted by remember { mutableStateOf(store.accepted()) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var document by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (accepted) { content(); return }
    if (document != null) { LegalDocument(document!!, { document = null }); return }
    Scaffold(topBar = { TopAppBar(title = { Text("Welcome to Meshlit") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Your AI, your devices", style = MaterialTheme.typography.headlineMedium)
                Text("By IMON · ${LegalAgreementStore.VERSION}", style = MaterialTheme.typography.bodySmall) }
            item { Text("Read the Terms and Privacy policy before using Meshlit. Startup model loading, SDK initialization and background bootstrap wait for acceptance.") }
            item { Text("Local chats, files and settings stay on this installation unless you choose sharing or an online service. Optional cloud providers receive the data you send. Audit collection is off by default. The model SDK may attempt its own development telemetry; its opt-out is still under review.") }
            item { OutlinedButton(onClick = { document = "terms" }, modifier = Modifier.testTag("read-terms")) { Text("Read Terms of use") }
                OutlinedButton(onClick = { document = "privacy" }, modifier = Modifier.testTag("read-privacy")) { Text("Read Privacy policy") } }
            item { Row { Checkbox(terms, { terms = it }, Modifier.testTag("accept-terms")); Text("I agree to the Terms of use", Modifier.padding(top = 12.dp)) }
                Row { Checkbox(privacy, { privacy = it }, Modifier.testTag("accept-privacy")); Text("I acknowledge and accept the Privacy policy", Modifier.padding(top = 12.dp)) } }
            item { Button(onClick = { runCatching { store.accept(terms, privacy) }.onSuccess { accepted = true }.onFailure { error = it.message } },
                    enabled = terms && privacy, modifier = Modifier.fillMaxWidth().testTag("legal-continue")) { Text("Agree and continue") }
                TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Decline and exit") } }
            item { Text("This agreement does not grant camera, microphone, location, agent autonomy, online access or optional telemetry permissions. You choose those separately.", style = MaterialTheme.typography.bodySmall) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}
