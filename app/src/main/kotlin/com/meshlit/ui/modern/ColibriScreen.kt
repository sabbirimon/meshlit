package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.colibri.*
import com.meshlit.di.koinInject
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ColibriScreen(onBack: () -> Unit) {
    val backend = koinInject<ColibriBackend>()
    val saved by backend.config.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    var token by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save() { try { backend.saveHuman(draft, token); token = ""; error = null } catch (_: Exception) { error = "Check the exact endpoint, model and private key. Host changes need a new key." } }
    Scaffold(topBar = { TopAppBar(expandedHeight = 48.dp, title = { Text("Colibri", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("colibri-settings"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Your optional inference host", style = MaterialTheme.typography.titleLarge)
                    Text("Off by default. Colibri runs on a separately configured host. No weights, drivers or engine are installed by a switch.", style = MaterialTheme.typography.bodyMedium)
                }
            } }
            item { OutlinedTextField(draft.endpoint, { draft = draft.copy(endpoint = it.take(2048)) }, Modifier.fillMaxWidth(), label = { Text("https://host:port/v1") }, singleLine = true) }
            item { OutlinedTextField(token, { token = it.take(4096) }, Modifier.fillMaxWidth(), label = { Text(if (backend.hasToken()) "New private key · saved key retained for same host" else "Private host bearer key · 32+ characters") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
            item { OutlinedTextField(draft.model, { draft = draft.copy(model = it.take(200)) }, Modifier.fillMaxWidth(), label = { Text("Host model identifier") }, singleLine = true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow this host to receive chat context", Modifier.weight(1f))
                Switch(draft.hostAccess, { on -> draft = draft.copy(hostAccess = on); if (!on) { backend.saveHuman(saved.copy(hostAccess = false, agentSwitching = false)); token = ""; models = emptyList() } }, Modifier.testTag("colibri-host-access"))
            } }
            item { Text("The selected host receives prompts, instructions and chosen history. Remote/LAN hosts require trusted HTTPS and an enforced bearer key; HTTP is literal loopback only. No web-search grant is implied.", style = MaterialTheme.typography.bodySmall) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow agents to switch mode", Modifier.weight(1f)); Switch(draft.agentSwitching, { on -> draft = draft.copy(agentSwitching = on); if (!on) backend.saveHuman(saved.copy(agentSwitching = false)) }, Modifier.testTag("colibri-agent-switching"))
            } }
            item { Text("Also requires Models/cloud/automation delegation and this chat's user grant. Agent mode requests expire after 30 minutes and cannot edit hosts, keys or saved policy.", style = MaterialTheme.typography.bodySmall) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Prefer host in Auto", Modifier.weight(1f)); Switch(draft.preferHost, { draft = draft.copy(preferHost = it) }) } }
            item { Text("Auto uses an authorized, recently observed model if no local model is ready, or if preferred here. It does not estimate cluster power. Host errors remain failures.", style = MaterialTheme.typography.bodySmall) }
            item { Button(onClick = ::save, modifier = Modifier.testTag("colibri-save")) { Text("Save host policy") } }
            item { OutlinedButton(enabled = saved.hostAccess && !busy, onClick = { scope.launch {
                busy = true; error = null
                try { models = backend.refreshModels() } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Model refresh failed. Verify trusted TLS, host bearer authorization and API availability." } finally { busy = false }
            } }, modifier = Modifier.testTag("colibri-model-refresh")) { Text(if (busy) "Refreshing…" else "Refresh host models") } }
            items(models.size) { i -> TextButton(onClick = { backend.saveHuman(saved.copy(model = models[i])) }) { Text((if (saved.model == models[i]) "✓ " else "") + models[i]) } }
            item { Text(if (backend.freshModel()) "Selected model observed; routing evidence expires after 5 minutes." else "Selected model has no fresh observation. Refresh before selecting On.", style = MaterialTheme.typography.bodySmall) }
            item { Text("Choose Off, On or Auto in Conversation and token settings. Reported output tokens and end-to-end tokens/s appear below replies; unavailable counts remain unknown.", style = MaterialTheme.typography.bodyMedium) }
            item { Text("Reviewed source: bf2442915d6e · Apache-2.0. Model licences are separate. CUDA/Metal/Vulkan and frontier-model claims require actual host qualification. This adapter supports text only.", style = MaterialTheme.typography.bodySmall) }
            error?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.error) } }
        }
    }
}
