package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.gateway.*
import com.meshlit.core.mcp.gateway.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun GatewayScreen(back:()->Unit) {
    val host=koinInject<GatewayHost>();val saved by host.settings.collectAsState();val running by host.running.collectAsState()
    val scope=rememberCoroutineScope();var remote by remember {mutableStateOf(host.remoteRoutes.saved())};var refreshing by remember {mutableStateOf(false)};var remoteResult by remember {mutableStateOf("")}
    var model by remember {mutableStateOf(saved.modelId)};var port by remember {mutableStateOf(saved.port.toString())}
    var mode by remember {mutableStateOf(saved.policy.mode)};var input by remember {mutableStateOf(saved.policy.inputTerms.joinToString("\n"))};var output by remember {mutableStateOf(saved.policy.outputTerms.joinToString("\n"))}
    var blockPromptInjection by remember(saved.policy.blockPromptInjection) {mutableStateOf(saved.policy.blockPromptInjection)}
    var allowAgentProtection by remember(saved.policy.allowAgentPromptProtectionChanges) {mutableStateOf(saved.policy.allowAgentPromptProtectionChanges)}
    DisposableEffect(Unit) {onDispose {host.stop()}}
    var error by remember {mutableStateOf<String?>(null)}
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    fun save() {host.save(GatewaySettings(port.toInt(),model,saved.policy.copy(mode=mode,
        inputTerms=input.lines().filter {it.isNotBlank()},outputTerms=output.lines().filter {it.isNotBlank()},
        blockPromptInjection=blockPromptInjection,allowAgentPromptProtectionChanges=allowAgentProtection)))}
    Scaffold(topBar={TopAppBar(title={Text("Agent Gateway")},navigationIcon={TextButton(onClick=back){Text("Back")}})}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text(if(running) "Running on loopback · port ${saved.port}" else "Stopped · screen-bound session; no automatic startup")}
            item {SettingsFeatureSection("Share resources with your own clients") {
                Text("Individuals and organizations can grant compatible clients access without installing Meshlit. Start with text inference: select a real model, issue a limited client key, then start this gateway.")
                Text("Resource description: GET /v1/resources. Use the scoped client key, not the owner token. The catalog describes configured models; it does not measure spare capacity or guarantee readiness.")
                Text("Local network and internet clients need an authenticated encrypted tunnel to this loopback gateway. Keep this screen open. Automatic internet discovery, raw compute rental, shared cache, bandwidth and sensor sales are not available yet.")
                Text("Payments are not enabled. Local contribution records are not earnings. Routes to online providers may incur their charges; model and provider terms still apply.")
            }}
            item {Text("Built-in MCP and A2A 0.3 task endpoints, plus buffered OpenAI-compatible text. Select a model ID from Models/Agent management. Incoming calls use saved agent delegation. This is Meshlit's Kotlin implementation; the upstream Rust agentgateway is a separate host runtime.")}
            item {OutlinedTextField(model,{model=it},label={Text("Model ID, or cloud:profile-id")},modifier=Modifier.fillMaxWidth())}
            item {OutlinedTextField(port,{port=it},label={Text("Loopback port")})}
            item {Text("Content policy");FlowRow {ContentMode.entries.forEach {value->FilterChip(mode==value,{mode=value},label={Text(value.name)})}}}
            item {Text("Filtered/custom apply your literal rules. An empty list performs no content moderation. Minimal skips only those literal rules. Provider restrictions and permissions remain enforced.")}
            item {OutlinedTextField(input,{input=it},label={Text("Block input terms · one per line")},modifier=Modifier.fillMaxWidth())}
            item {OutlinedTextField(output,{output=it},label={Text("Block output terms · one per line")},modifier=Modifier.fillMaxWidth())}
            item {SettingsFeatureSection("Prompt-injection protection") {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Block known prompt-injection patterns",Modifier.weight(1f))
                    Switch(blockPromptInjection,{blockPromptInjection=it})
                }
                Text("Optional local heuristic for gateway input and output, including Minimal mode. Save to apply. It can miss attacks or block legitimate quoted instructions, role labels and encoded text.")
                Text("This is not antivirus or a guarantee against malware propagation. It does not scan downloaded files or change your content. Authentication, client scopes, integrity checks and request limits stay enforced when this option is off.")
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Allow approved agents to change this protection",Modifier.weight(1f))
                    Switch(allowAgentProtection,{allowAgentProtection=it})
                }
                Text("Save to delegate. Also grant the agent's client MCP scope and gateway_prompt_protection_set. This permits turning the optional heuristic on or off; agents cannot grant themselves this permission or change authentication and integrity controls.")
            }}
            item {FlowRow {
                Button(onClick={try {save();error=null}catch(_:Exception){error="Invalid port, model or policy"}}){Text("Save and stop")}
                Button(enabled=!com.meshlit.BuildConfig.PLAY_REVIEW,onClick={try {save();host.start();error=null}catch(_:Exception){error="Gateway could not start; check port and build restrictions"}}){Text("Start")}
                OutlinedButton(onClick={host.stop()}){Text("Stop")}
                OutlinedButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(host.token()))}){Text("Copy owner token")}
                OutlinedButton(onClick={host.rotate()}){Text("Rotate owner token and stop")}
            }}
            item {Text("Endpoints: /mcp · /a2a · /.well-known/agent-card.json · /v1/resources · /v1/models · /v1/chat/completions. All require Bearer authentication. Browser origins are rejected. LAN/off-grid and internet clients need a verified encrypted private tunnel or host HTTPS gateway. Web apps should keep the client key on their backend, not in browser scripts. The owner token bypasses client quotas; keep it private. Copying places a secret on your clipboard.")}
            item {Text("Connect an external agentgateway/LiteLLM endpoint through Online providers → Custom compatible. Use its exact HTTPS API base, gateway key and model alias. Provider keys can stay on the host.")}
            item {GatewayClientsPanel(host)}
            item {Text("Remote MCP / A2A routes",style=MaterialTheme.typography.titleMedium)}
            item {Text("Human-enrolled JSON routes: exact HTTPS endpoint (or loopback tunnel), bearer token, protocol MCP/A2A, explicit agentEnabled and MCP allowedTools. Credentials remain encrypted. Discovered tools are untrusted data; remote actions may have effects. JSON-response transport only; OAuth and SSE remain separate adapters.")}
            item {OutlinedTextField(remote,{remote=it},label={Text("Remote routes JSON · contains secrets")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)}
            item {FlowRow {
                OutlinedButton(enabled=!refreshing,onClick={try {host.stop();host.remoteRoutes.save(remote);remoteResult="Saved; start gateway then refresh routes"}catch(_:Exception){error="Invalid remote routes"}}){Text("Save routes and stop")}
                Button(enabled=running && !refreshing,onClick={scope.launch {refreshing=true;try {remoteResult="${host.remoteRoutes.refresh()} approved remote tools discovered"}catch(e:CancellationException){throw e}catch(_:Exception){remoteResult="Remote discovery failed; inspect route transport/authentication"}finally{refreshing=false}}}){Text("Refresh routes")}
            }}
            item {Text(remoteResult)}
            item {GatewayRoutingPanel(host)}
            error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
        }
    }
}
