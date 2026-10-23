package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.core.mcp.McpToolRegistry
import com.meshlit.core.mcp.gateway.RemoteAgentRoute
import com.meshlit.di.koinInject
import com.meshlit.gateway.GatewayHost
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private data class McpSuggestion(val title:String,val purpose:String,val host:String)

private val mcpSuggestions=listOf(
    McpSuggestion("Filesystem","Read or edit explicitly allowed folders","Desktop/server host"),
    McpSuggestion("Fetch","Retrieve web pages for an agent","Desktop/server host"),
    McpSuggestion("Memory","Persist a separate knowledge graph","Desktop/server host"),
    McpSuggestion("GitHub","Repository, issue and pull request tools","GitHub hosted or self-hosted"),
    McpSuggestion("Playwright","Operate a browser with page snapshots","Desktop/server host"),
)

/** Enrolls an already running JSON-response MCP endpoint; suggestions never install software. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun McpCatalogScreen(onBack:()->Unit,onGateway:()->Unit) {
    val host=koinInject<GatewayHost>()
    val codec=remember { Json { ignoreUnknownKeys=false } }
    var routes by remember { mutableStateOf(runCatching { codec.decodeFromString<List<RemoteAgentRoute>>(host.remoteRoutes.saved()) }.getOrDefault(emptyList())) }
    var chosen by remember { mutableStateOf("Custom MCP") }
    var id by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var allowlist by remember { mutableStateOf("") }
    var agentEnabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun save(next:List<RemoteAgentRoute>,status:String) {
        try { host.stop();host.remoteRoutes.save(codec.encodeToString(next));routes=next;message=status }
        catch(_:Exception) { message="Could not save route. Check the ID, HTTPS URL, token length and tool names." }
    }
    Scaffold(topBar={TopAppBar(title={Text("MCP connections")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("Common MCP servers",style=MaterialTheme.typography.titleLarge)
                Text("These are setup references. Install and run a server you trust on a host, expose a JSON-response MCP endpoint through HTTPS or a loopback tunnel, then enroll it below. Meshlit does not download or run these servers on your phone.") }
            items(mcpSuggestions) { suggestion -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(suggestion.title,style=MaterialTheme.typography.titleMedium)
                Text("${suggestion.purpose} · ${suggestion.host}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={chosen=suggestion.title;id=suggestion.title.lowercase();message="Selected ${suggestion.title}. Enter your own endpoint, token and exact approved tool names."}) {Text("Configure")}
            } } }
            item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Add $chosen route",style=MaterialTheme.typography.titleMedium)
                Text("A route starts with no approved tools and no agent access. Discovery returns only names you explicitly allow. The gateway must be started again after saving.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(id,{id=it.take(16)},Modifier.fillMaxWidth(),label={Text("Route ID · lowercase letters, digits, underscore")},singleLine=true)
                OutlinedTextField(endpoint,{endpoint=it.take(2048)},Modifier.fillMaxWidth(),label={Text("Exact HTTPS MCP endpoint")},singleLine=true)
                OutlinedTextField(token,{token=it.take(4096)},Modifier.fillMaxWidth(),label={Text("Bearer token · stored encrypted")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                OutlinedTextField(allowlist,{allowlist=it.take(5000)},Modifier.fillMaxWidth(),label={Text("Approved tool names · one per line")},minLines=2,maxLines=6)
                Row(Modifier.fillMaxWidth()) { Text("Allow agent calls",Modifier.weight(1f));Switch(agentEnabled,{agentEnabled=it}) }
                Button(enabled=routes.size<8,onClick={
                    val route=RemoteAgentRoute(id.trim(),endpoint.trim(),token.trim(),allowedTools=allowlist.lines().map{it.trim()}.filter{it.isNotBlank()}.toSet(),agentEnabled=agentEnabled)
                    save(routes.filterNot{it.id==route.id}+route,"Route saved. Start the gateway and refresh remote tools.")
                }) { Text("Save approved route") }
                TextButton(onClick=onGateway) { Text("Open gateway and refresh tools") }
                if(message.isNotBlank()) Text(message)
            } } }
            item { Text("Saved routes · ${routes.size}/8",style=MaterialTheme.typography.titleMedium) }
            items(routes,key={it.id}) { route -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(route.id,style=MaterialTheme.typography.titleMedium)
                Text("${route.endpoint} · ${route.allowedTools.size} allowed tools · agent ${if(route.agentEnabled) "on" else "off"}",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={save(routes.filterNot{it.id==route.id},"Route removed and gateway stopped.")}) { Text("Remove route") }
            } } }
        }
    }
}

@Composable fun AgentRegistryScreen(onBack:()->Unit,onManage:()->Unit,onMcp:()->Unit) {
    val context=LocalContext.current
    val profiles=remember { runCatching { com.meshlit.agent.AgentProfileStore(context).load() }.getOrDefault(emptyList()) }
    val registry=koinInject<McpToolRegistry>()
    val tools=remember { registry.list() }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack){Text("Back to settings")};Text("Agent registry",style=MaterialTheme.typography.headlineSmall)
            Text("Saved agents are task templates. A registered tool is not automatically approved for an agent.")
            Column {
                TextButton(onClick=onManage){Text("Manage agents")}
                TextButton(onClick=onMcp){Text("MCP connections")}
            } }
        item { Text("${profiles.size} saved agents · ${tools.size} registered tools",style=MaterialTheme.typography.titleMedium) }
        if(profiles.isEmpty()) item {Text("No saved agents yet. Create one in Agent management.")}
        items(profiles,key={it.id}) { profile -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Text(profile.name,style=MaterialTheme.typography.titleMedium)
            Text("${if(profile.enabled) "Enabled" else "Disabled"} · model ${profile.modelId}",style=MaterialTheme.typography.bodySmall)
        } } }
        items(tools.take(100),key={it.name}) { tool -> ListItem(headlineContent={Text(tool.name)},supportingContent={Text(tool.description)},overlineContent={Text("Registered MCP tool")}) }
    }
}
