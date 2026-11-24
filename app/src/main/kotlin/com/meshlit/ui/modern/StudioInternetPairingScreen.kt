package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.devices.studio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioInternetPairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var store by remember { mutableStateOf<StudioPairings?>(null) }
    var peers by remember { mutableStateOf(emptyList<SavedStudioPeer>()) }
    var hasToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var phoneId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf<SavedStudioPeer?>(null) }
    LaunchedEffect(context) {
        try {
            val saved = withContext(Dispatchers.IO) { StudioPairings(context) }
            peers = withContext(Dispatchers.IO) { saved.peers() }
            hasToken = withContext(Dispatchers.IO) { saved.token() != null }
            phoneId = withContext(Dispatchers.IO) { StudioRelayClient(context).localDeviceId() }
            store = saved
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { failed = true; status = "Could not open saved pairing data." }
    }
    fun act(message: String, operation: suspend (StudioPairings) -> String) {
        val saved = store ?: return
        if (busy) return
        busy = true
        status = message
        failed = false
        scope.launch {
            try {
                status = withContext(Dispatchers.IO) { operation(saved) }
                peers = withContext(Dispatchers.IO) { saved.peers() }
                hasToken = withContext(Dispatchers.IO) { saved.token() != null }
                token = ""
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { failed = true; status = e.message ?: "Could not reach the desktop." }
            finally { busy = false }
        }
    }
    fun connect(target: String, prior: SavedStudioPeer?) {
        val entered = token
        act(if (prior == null) "Waiting for approval on the desktop…" else "Verifying the saved desktop…") { saved ->
            val access = entered.ifEmpty { saved.token().orEmpty() }
            require(access.isNotEmpty()) { "Enter the relay access token." }
            val peer = StudioRelayClient(context).request(STUDIO_RELAY_ORIGIN, access, target, prior?.key, prior == null)
            val name = peer.name.ifBlank { prior?.name ?: target }
            saved.save(SavedStudioPeer(target, peer.key, peer.node, name), access)
            if (prior == null) "Paired with $name." else "Reconnected to $name. Identity verified."
        }
    }
    val available = store != null && !busy
    val canConnect = available && (hasToken || token.isNotBlank())
    Scaffold(topBar = { TopAppBar(title = { Text("Pair Studio desktop") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("On the desktop, open Devices, enable pairing and copy its device ID.", style = MaterialTheme.typography.bodyMedium) }
            item { Text(STUDIO_RELAY_ORIGIN.removePrefix("https://"), style = MaterialTheme.typography.labelLarge) }
            if (phoneId.isNotEmpty()) item {
                Text("This phone · $phoneId", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { clipboard.setText(AnnotatedString(phoneId)) }) { Text("Copy phone ID") }
            }
            item {
                OutlinedTextField(token, { token = it }, enabled = available, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasToken) "Replace saved relay token" else "Relay access token") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = PasswordVisualTransformation())
                if (hasToken) TextButton(enabled = available, onClick = {
                    act("Removing access token…") { it.forgetToken(); "Relay access token removed." }
                }) { Text("Forget relay token") }
            }
            item { OutlinedTextField(id, { id = it.trim().lowercase() }, enabled = available, singleLine = true,
                label = { Text("20-character desktop ID") }, modifier = Modifier.fillMaxWidth()) }
            item {
                val previous = peers.firstOrNull { it.id == id }
                Button(enabled = canConnect && id.matches(Regex("[a-f0-9]{20}")), modifier = Modifier.fillMaxWidth(),
                    onClick = { connect(id, previous) }) { Text(if (previous == null) "Pair desktop" else "Reconnect") }
            }
            if (status.isNotEmpty()) item { Text(status, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (peers.isNotEmpty()) item { Text("Saved desktops", style = MaterialTheme.typography.titleMedium) }
            items(peers, key = { it.id }) { peer ->
                ListItem(headlineContent = { Text(peer.name) }, supportingContent = { Text(peer.id) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { connect(peer.id, peer) }, enabled = canConnect) { Text("Verify connection") }
                    TextButton(onClick = { forget = peer }, enabled = available) { Text("Forget") }
                }
                HorizontalDivider()
            }
        }
    }
    forget?.let { peer -> AlertDialog(onDismissRequest = { forget = null },
        title = { Text("Forget ${peer.name}?") }, text = { Text("Pair again to verify this desktop in the future.") },
        dismissButton = { TextButton(onClick = { forget = null }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            forget = null
            act("Removing desktop…") { it.forget(peer.id); "Desktop removed from this phone." }
        }) { Text("Forget desktop") } }) }
}
