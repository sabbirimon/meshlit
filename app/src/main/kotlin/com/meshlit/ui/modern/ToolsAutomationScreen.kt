package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.di.koinInject
import com.meshlit.gateway.GatewayHost
import com.meshlit.settings.SettingsRepository

/** One inventory over the existing MCP, Gateway and user-authored Hook backends. */
@Composable internal fun ToolsAutomationScreen(onBack:()->Unit,onOpen:(String)->Unit) {
    val registry=koinInject<McpToolRegistry>()
    val gateway=koinInject<GatewayHost>()
    val running by gateway.running.collectAsStateWithLifecycle()
    val settings=koinInject<SettingsRepository>()
    val hooks by settings.hooksRegistryFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val hooksEnabled by settings.hooksEnabledFlow.collectAsStateWithLifecycle(initialValue=false)
    var query by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val tools=remember(revision) { registry.list() }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("tools-automation-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back to settings") };Text("Tools & automation",style=MaterialTheme.typography.headlineSmall)
            Text("A tool being registered does not grant an agent permission to run it. Saved scopes, operation switches, provider access and per-chat tools remain separate.") }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("MCP · ${tools.size} registered tools",style=MaterialTheme.typography.titleMedium)
            Text(if(running) "Agent Gateway running on this device" else "Agent Gateway stopped")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onOpen("gateway")}) { Text("Gateway and remote routes") }
                TextButton(onClick={onOpen("agents")}) { Text("Agent grants and jobs") }
            }
            OutlinedButton(onClick={onOpen("mcp-catalog")}) { Text("Browse and connect MCP servers") }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={onOpen("agent-registry")}) { Text("Agent registry") }
                TextButton(onClick={onOpen("agent-logs")}) { Text("Agent logs") }
            }
            OutlinedTextField(query,{query=it.take(120)},Modifier.fillMaxWidth(),label={Text("Find a registered tool")})
            TextButton(onClick={revision++}) { Text("Refresh tool list") }
        } } }
        items(tools.filter { "${it.name} ${it.description}".contains(query,true) }.take(100),key={it.name}) { tool ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(tool.name,style=MaterialTheme.typography.titleSmall)
                Text(tool.description,style=MaterialTheme.typography.bodySmall)
                Text("Availability and permission are checked again at execution.",style=MaterialTheme.typography.labelSmall)
            } }
        }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Agent hooks · ${hooks.size} saved",style=MaterialTheme.typography.titleMedium)
            Text(if(hooksEnabled) "Master switch on · ${hooks.count{it.enabled}} hooks enabled" else "Master switch off · no hooks will run")
            Text("Hooks are owner-authored scripts. Review their triggers and bodies before enabling; downloaded text is never installed as a hook automatically.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={onOpen("hooks")}) { Text("Review and edit hooks") }
        } } }
        item { OutlinedButton(onClick={onOpen("operations")}) { Text("Operation switches and emergency stop") } }
    }
}
