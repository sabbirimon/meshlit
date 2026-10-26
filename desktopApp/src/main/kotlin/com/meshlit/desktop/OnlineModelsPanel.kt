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
    OnlinePreset("OmniRoute", "http://127.0.0.1:20128/v1", true), OnlinePreset("FreeLLMAPI", "http://127.0.0.1:3001/v1", true),
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
    openDashboard: (String, String) -> Unit = { _, _ -> },
) {
    var advanced by remember { mutableStateOf(false) }
    var filter by remember(service) { mutableStateOf("") }
    var tab by remember(service) { mutableStateOf("Overview") }
    var connectionMessage by remember(service) { mutableStateOf("") }
    val vendor = vendorPages[service]
    val addresses = remember { VendorDashboardAddresses() }
    var dashboard by remember(service) { mutableStateOf(addresses.load(service, vendor?.dashboard ?: "")) }
    fun browse(url: String) { connectionMessage = runCatching { openDashboard(service, dashboardUri(url).toString()); "Opened inside Meshlit." }.getOrElse { "Could not open this address. Use HTTPS, or HTTP on literal loopback for local dashboards." } }
    Text("Model services", style = MaterialTheme.typography.h5)
    Text("Choose a vendor to explore its products, manage your account or connect models to Meshlit.")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        onlinePresets.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item -> OutlinedButton({ if(service != item.name) preset(item) else tab = "Overview" }, Modifier.weight(1f).heightIn(min = 64.dp), enabled = !busy, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = if(service == item.name) MaterialTheme.colors.primary.copy(alpha = .12f) else MaterialTheme.colors.surface)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((if (service == item.name) "✓  " else "") + item.name)
                    Text(if(vendorPages[item.name]?.local == true) "Your gateway dashboard" else "Models and account", style = MaterialTheme.typography.caption)
                }
            } }
        } }
    }
    Text(service, style = MaterialTheme.typography.h6)
    Text(if(connected) "Connected · ${models.size} models returned" else "Not connected to Meshlit", style = MaterialTheme.typography.caption)
    Row { listOf("Overview", "Models", "Connection", "Usage").forEach { page -> TextButton({ tab = page }) { Text((if(tab == page) "• " else "") + page) } } }
    if(connectionMessage.isNotBlank()) Text(connectionMessage, style = MaterialTheme.typography.caption)
    when(tab) {
        "Overview" -> {
            Text(vendor?.description ?: "Connect your compatible API and supply its dashboard address.")
            if(vendor?.local == true) Text("The gateway must already be installed and running. These are standard server defaults, not a detected installation. FreeLLMAPI desktop and development builds can use different ports.", style = MaterialTheme.typography.caption)
            if(vendor?.local == true || vendor == null) {
                OutlinedTextField(dashboard, { dashboard = it.take(2048) }, label = { Text("Your dashboard URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton({ connectionMessage = runCatching { addresses.save(service, dashboard); "Dashboard address saved." }.getOrElse { "Invalid address. Use HTTPS or literal loopback HTTP, without credentials or query parameters." } }) { Text("Save dashboard address") }
            }
            Button({ browse(dashboard) }, enabled = dashboard.isNotBlank()) { Text("Open dashboard in Meshlit") }
            vendor?.let { page ->
                TextButton({ browse(page.catalog) }) { Text("Browse products / catalog") }
                TextButton({ browse(page.guide) }) { Text(if(page.local) "Installation and setup" else "Service documentation") }
            }
            Text("Vendor pages open inside Meshlit. Sign in directly to the vendor; API keys are not injected. Use Read page to review text before adding it to chat.", style = MaterialTheme.typography.caption)
            OutlinedButton({ tab = "Connection" }) { Text("Connect this service to chat") }
        }
        "Models" -> {
            vendor?.let { TextButton({ browse(it.catalog) }) { Text("Explore vendor catalog") } }
            if(!connected) {
                Text("Connect to retrieve the models available from your endpoint. A public catalog does not prove account access or free usage.")
                Button({ tab = "Connection" }) { Text("Set up connection") }
            } else {
                OutlinedTextField(filter, { filter = it.take(128) }, label = { Text("Search available models") }, modifier = Modifier.fillMaxWidth())
                val matches = models.filter { it.contains(filter, ignoreCase = true) }
                Text("${matches.size} matches · showing up to 100", style = MaterialTheme.typography.caption)
                if(models.isEmpty()) Text("This endpoint returned no models.")
                matches.take(100).forEach { id -> OutlinedButton({ choose(id) }, Modifier.fillMaxWidth(), enabled = !busy) { Text((if(model == id) "✓ " else "") + id) } }
            }
        }
        "Usage" -> {
            Text("Meshlit's history tracks requests made here. Provider invoices, balances and quotas remain in the vendor dashboard.")
            Button(onUsage) { Text("Meshlit usage & budgets") }
            vendor?.let { TextButton({ browse(it.billing.ifBlank { dashboard }) }) { Text("Vendor usage / account dashboard") } }
            Text("Device-local estimates are not provider billing caps.", style = MaterialTheme.typography.caption)
        }
        "Connection" -> {
            Text("Use an API key from this service or your gateway's unified endpoint key.")
            OutlinedTextField(endpoint, setEndpoint, Modifier.fillMaxWidth(), label = { Text("API URL") }, singleLine = true, enabled = !busy)
            TextButton({ connectionMessage = runCatching { saveConnection(); "Connection address saved." }.getOrElse { "Could not save this connection." } }) { Text("Save connection address") }
            OutlinedTextField(token, setToken, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !busy)
            credentialControls()
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowed, setAllowed, enabled = !busy); Text("Allow access to this service for this session") }
            Button(connect, enabled = allowed && !busy && endpoint.isNotBlank()) { Text(if(busy) "Connecting…" else "Connect / refresh models") }
            if(connected) Button({ tab = "Models" }) { Text("Choose from ${models.size} models") }
            Text("Prompts and keys go to the selected API host only after explicit connection or sending.", style = MaterialTheme.typography.caption)
            if(gateway) Text("Strict free-only and currency budgets block opaque gateway routes until costs can be verified.", style = MaterialTheme.typography.caption)
            TextButton({ advanced = !advanced }) { Text(if(advanced) "Hide advanced" else "Advanced connection settings") }
            if(advanced) {
                TextButton({ connectionMessage = runCatching { forgetConnection(); "Saved address removed." }.getOrElse { "Could not remove address." } }) { Text("Forget saved connection") }
                Row { HostProtocol.entries.forEach { p -> TextButton({ setProtocol(p) }, enabled = !busy) { Text((if(protocol == p) "✓ " else "") + p.label) } } }
                Row { Checkbox(gateway, setGateway, enabled = !busy); Text("Routes through other providers") }
            }
        }
    }
}
