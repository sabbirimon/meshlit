package com.meshlit.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=28.dp,vertical=36.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item {
                Icon(androidx.compose.ui.res.painterResource(com.meshlit.R.drawable.meshlit_mark),null,
                    Modifier.size(44.dp),tint=androidx.compose.ui.graphics.Color.Unspecified)
                Spacer(Modifier.height(24.dp))
                Text("Welcome to Meshlit",style=MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text("Review both documents to start chatting on this device.",style=MaterialTheme.typography.bodyLarge)
            }
            item {
                TextButton(onClick={document="terms"},modifier=Modifier.testTag("read-terms")){Text("Read Terms of use")}
                TextButton(onClick={document="privacy"},modifier=Modifier.testTag("read-privacy")){Text("Read Privacy policy")}
                HorizontalDivider()
            }
            item {
                AgreementChoice("I agree to the Terms of use",terms,"accept-terms"){terms=it}
                AgreementChoice("I accept the Privacy policy",privacy,"accept-privacy"){privacy=it}
            }
            item {
                Text("Cloud providers receive what you send. The model SDK may attempt development telemetry.",style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={showDetails=!showDetails}){Text(if(showDetails) "Hide details" else "Privacy details")}
                if(showDetails) Text("Local chats, files and settings stay on this installation unless you choose sharing or an online service. Audit collection is off by default. SDK telemetry opt-out is under review. Startup model loading and background setup wait for acceptance. By IMON · ${LegalAgreementStore.VERSION}",style=MaterialTheme.typography.bodyMedium)
            }
            item { Button(onClick = { runCatching { store.accept(terms, privacy) }.onSuccess { accepted = true }.onFailure { error = it.message } },
                    enabled = terms && privacy, shape=RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("legal-continue")) { Text("Agree and continue") }
                TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Decline and exit") } }
            item { Text("This agreement does not grant camera, microphone, location, agent autonomy, online access or optional telemetry permissions. You choose those separately.", style = MaterialTheme.typography.bodyMedium) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun AgreementChoice(label:String, selected:Boolean, tag:String, onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)
        .toggleable(value=selected,role=Role.Checkbox,onValueChange=onChange),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Checkbox(checked=selected,onCheckedChange=null)
        Text(label,style=MaterialTheme.typography.bodyLarge)
    }
}
