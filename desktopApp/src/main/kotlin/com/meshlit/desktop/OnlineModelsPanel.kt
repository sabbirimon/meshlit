package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

internal data class OnlinePreset(val name: String, val endpoint: String, val gateway: Boolean = false)
internal val onlinePresets = listOf(
    OnlinePreset("OpenRouter", "https://openrouter.ai/api/v1", true),
    OnlinePreset("Hugging Face", "https://router.huggingface.co/v1", true),
    OnlinePreset("OmniRoute", "", true), OnlinePreset("FreeLLMAPI", "", true),
    OnlinePreset("OpenAI", "https://api.openai.com/v1"), OnlinePreset("Custom compatible", ""),
)

@Composable internal fun OnlineModelsPanel(
    service: String, endpoint: String, token: String, protocol: HostProtocol, gateway: Boolean,
    allowed: Boolean, connected: Boolean, busy: Boolean, models: List<String>, model: String,
    preset: (OnlinePreset) -> Unit, setEndpoint: (String) -> Unit, setToken: (String) -> Unit,
    setProtocol: (HostProtocol) -> Unit, setGateway: (Boolean) -> Unit, setAllowed: (Boolean) -> Unit,
    connect: () -> Unit, choose: (String) -> Unit, onUsage: () -> Unit,
    credentialControls: @Composable () -> Unit,
    saveConnection: () -> Unit = {}, forgetConnection: () -> Unit = {},
) {
    var advanced by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var connectionMessage by remember { mutableStateOf("") }
    Text("One place for online models", style = MaterialTheme.typography.h6)
    Text("Choose a service, connect your account, then pick its model in chat.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        onlinePresets.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item -> OutlinedButton({ preset(item) }, Modifier.weight(1f).heightIn(min = 64.dp), enabled = !busy, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = if(service == item.name) MaterialTheme.colors.primary.copy(alpha = .12f) else MaterialTheme.colors.surface)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((if (service == item.name) "✓  " else "") + item.name)
                    Text(if(item.endpoint.isBlank()) "Your deployment" else "Connect your account", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = .55f))
                }
            } }
        } }
    }
    Text(service, style = MaterialTheme.typography.subtitle1)
    if (service in setOf("OmniRoute", "FreeLLMAPI")) Text("Optional gateway: enter your own deployment URL and scoped key. FreeLLMAPI is experimental; a gateway does not include model access or paid credits.", style = MaterialTheme.typography.caption)
    Text("Free and paid models depend on your account. Requests send this conversation to the selected service. Nothing is sent until you explicitly connect or send.", style = MaterialTheme.typography.caption)
    OutlinedTextField(endpoint, setEndpoint, Modifier.fillMaxWidth(), label = { Text("API URL") }, singleLine = true, enabled = !busy)
    TextButton({ connectionMessage = runCatching { saveConnection(); "Address saved. Keys and network consent are separate." }.getOrElse { "Could not save this connection. Check its URL and local storage." } }) { Text("Save connection address") }
    if(connectionMessage.isNotBlank()) Text(connectionMessage, style = MaterialTheme.typography.caption)
    OutlinedTextField(token, setToken, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), enabled = !busy)
    credentialControls()
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowed, setAllowed, enabled = !busy); Text("Allow access to this service for this session") }
    Button(connect, enabled = allowed && !busy && endpoint.isNotBlank()) { Text(if (busy) "Connecting…" else "Connect / fetch models") }
    if (connected) {
        Text("Select a model to return to chat", style = MaterialTheme.typography.subtitle2)
        if (models.isEmpty()) Text("The service returned no models.")
        OutlinedTextField(filter, { filter = it.take(128) }, label = { Text("Find a model") }, modifier = Modifier.fillMaxWidth())
        Text("${models.size} models returned · showing up to 100 matches", style = MaterialTheme.typography.caption)
        models.filter { it.contains(filter, ignoreCase = true) }.take(100).forEach { id -> TextButton({ choose(id) }, enabled = !busy) { Text((if (model == id) "✓ " else "") + id) } }
    }
    TextButton(onUsage) { Text("Usage & budgets") }
    if (gateway) Text("Gateway costs can include downstream routing. Strict free-only and currency-budget policies block opaque gateways until costs can be verified.", style = MaterialTheme.typography.caption)
    TextButton({ advanced = !advanced }) { Text(if (advanced) "Hide advanced settings" else "Advanced settings") }
    if (advanced) {
        TextButton({ connectionMessage = runCatching { forgetConnection(); "Saved address removed; encrypted keys are managed separately." }.getOrElse { "Could not remove saved address." } }) { Text("Forget saved address") }
        Row { HostProtocol.entries.forEach { p -> TextButton({ setProtocol(p) }, enabled = !busy) { Text((if (protocol == p) "✓ " else "") + p.label) } } }
        Row { Checkbox(gateway, setGateway, enabled = !busy); Text("Routes through other providers") }
        Text("HTTPS uses system certificate trust. Redirects are blocked; HTTP is restricted to literal loopback. Credentials are bound to the exact service and protocol.", style = MaterialTheme.typography.caption)
    }
}
