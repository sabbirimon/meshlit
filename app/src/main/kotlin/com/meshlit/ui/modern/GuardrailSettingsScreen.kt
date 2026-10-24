package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.LocalModelBehavior
import com.meshlit.core.mcp.gateway.ContentMode
import com.meshlit.di.koinInject
import com.meshlit.gateway.GatewayHost
import com.meshlit.models.LocalBehaviorSettings

@Composable internal fun GuardrailSettingsScreen(onBack: () -> Unit, onLocal: () -> Unit, onGateway: () -> Unit) {
    val behavior = koinInject<LocalBehaviorSettings>()
    val local by behavior.state.collectAsState()
    val gateway = koinInject<GatewayHost>()
    val gatewaySettings by gateway.settings.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    fun saveLocal(enabled: Boolean) {
        try { behavior.save(LocalModelBehavior(enabled, local.instructions)); error = null }
        catch (e: Exception) { error = e.message }
    }
    fun saveGateway(enabled: Boolean) {
        try { gateway.save(gatewaySettings.copy(policy = gatewaySettings.policy.copy(
            mode = if (enabled) ContentMode.FILTERED else ContentMode.MINIMAL))); error = null }
        catch (e: Exception) { error = e.message }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("guardrails-settings-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to settings") }; Text("Local behavior and guardrails", style = MaterialTheme.typography.headlineSmall) }
        item { Text("There is no universal censored or uncensored model switch. Select compatible local GGUF weights in Models. The model's learned behavior, provider restrictions, tool permissions, and Android access do not change here.") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom local instructions", style = MaterialTheme.typography.titleMedium)
            Row { Text("Apply saved instructions", Modifier.weight(1f)); Switch(local.enabled, ::saveLocal,
                modifier = Modifier.testTag("local-behavior-switch"), enabled = local.instructions.isNotBlank() || local.enabled) }
            Text(if (local.instructions.isBlank()) "No instructions saved. Add text before enabling." else "Saved instructions apply to the next local generation.")
            TextButton(onClick = onLocal) { Text("Edit instructions and choose a model") }
        } } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Agent Gateway term rules", style = MaterialTheme.typography.titleMedium)
            Row { Text("Apply optional literal term rules", Modifier.weight(1f)); Switch(gatewaySettings.policy.mode != ContentMode.MINIMAL, ::saveGateway,
                modifier = Modifier.testTag("gateway-terms-switch")) }
            Text("${gatewaySettings.policy.inputTerms.size} input terms · ${gatewaySettings.policy.outputTerms.size} output terms. Empty lists filter nothing. Changing this policy stops a running gateway until you restart it.")
            TextButton(onClick = onGateway) { Text("Edit gateway policy and terms") }
        } } }
        item { Text("Bounds on content size, authenticated access, human approval, and device permissions remain enforced in every mode.") }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}
