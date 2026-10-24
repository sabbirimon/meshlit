package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.gateway.GatewayHost
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
@Composable fun GatewayRoutingPanel(host:GatewayHost){
    val routes by host.routing.routes.collectAsState();val health by host.routing.health.collectAsState()
    var configuration by remember{mutableStateOf(host.routing.saved())};var routeId by remember{mutableStateOf("")};var args by remember{mutableStateOf("{}")}
    var result by remember{mutableStateOf("")};var pending by remember{mutableStateOf<Job?>(null)};val scope=rememberCoroutineScope()
    Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Unified routing dashboard",style=MaterialTheme.typography.titleMedium)
        Text("LLM model IDs, approved MCP tools and A2A methods share owner-defined routes. Priority or recent observed latency chooses one target. There is no automatic replay after a failure. EXTENSION is reserved and unavailable until an adapter is installed.")
        OutlinedTextField(configuration,{configuration=it},label={Text("Unified routes JSON · no credentials")},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
        Button(onClick={try{host.routing.saveHuman(configuration);result="Saved; in-flight results from the old route generation will be rejected"}catch(_:Exception){result="Invalid route configuration or save failure"}}){Text("Save routes")}
        routes.forEach{route->Text("${route.id} · ${route.protocol} · human=${route.enabled} · agent=${route.agentAllowed} · ${route.selection}")}
        health.forEach{(name,observation)->Text("$name · ${if(observation.succeeded) "last call succeeded" else "failed / outcome uncertain"} · ${observation.latencyMs} ms · ${observation.observedAtMs}")}
        Text("Manual human invocation. LLM arguments: prompt and optional maxTokens. MCP/A2A arguments follow the discovered schema. Remote content and instructions remain untrusted data.")
        OutlinedTextField(routeId,{routeId=it},label={Text("Route ID")});OutlinedTextField(args,{args=it},label={Text("Arguments JSON / instructions")},maxLines=6)
        Row{Button(enabled=pending?.isActive!=true,onClick={pending=scope.launch{result="Running";try{result=host.routing.execute(routeId,Json.parseToJsonElement(args).jsonObject).toString().take(8192)}catch(e:CancellationException){result="Cancelled locally; inspect remote task state";throw e}catch(_:Exception){result="Route failed or unavailable; no automatic retry"}}}){Text("Run as human")};OutlinedButton(onClick={pending?.cancel()}){Text("Cancel")}}
        Text(result)
    }}
}
