package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.meshlit.core.mcp.gateway.*
import com.meshlit.gateway.GatewayHost
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun GatewayClientsPanel(host:GatewayHost) {
    val clients by host.clients.collectAsState()
    var name by remember{mutableStateOf("")}
    var models by remember{mutableStateOf(host.settings.value.modelId)}
    var tools by remember{mutableStateOf("")}
    var hours by remember{mutableStateOf("1")}
    var limit by remember{mutableStateOf("512")}
    var rpm by remember{mutableStateOf("30")}
    var scopes by remember{mutableStateOf(setOf(GatewayScope.MODELS,GatewayScope.CHAT))}
    var issued by remember{mutableStateOf<IssuedGatewayKey?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    val clipboard=LocalClipboardManager.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Client access",style=MaterialTheme.typography.titleLarge)
        Text("Issue a separate API key for each desktop, phone or web backend. Permissions, model IDs, expiry and limits are enforced. Client edits stop the gateway and cancel its pending jobs; restart after changing access.")
        OutlinedTextField(name,{if(it.length<=80) name=it},Modifier.fillMaxWidth(),label={Text("Client name")})
        OutlinedTextField(models,{models=it},Modifier.fillMaxWidth(),label={Text("Allowed model IDs, comma separated")})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){GatewayScope.entries.forEach{scope->
            FilterChip(scope in scopes,{scopes=if(scope in scopes) scopes-scope else scopes+scope},label={Text(scope.name)})
        }}
        if(GatewayScope.MCP in scopes) {
            OutlinedTextField(tools,{tools=it},Modifier.fillMaxWidth(),label={Text("Allowed MCP tool names, comma separated")})
            Text("MCP grants access to these tools under saved agent permissions. Grant command or remote tools only to a trusted client. An empty list grants no tools.")
        }
        OutlinedTextField(hours,{hours=it},label={Text("Expires after hours (1–720)")})
        OutlinedTextField(limit,{limit=it},label={Text("Maximum output tokens (16–1024)")})
        OutlinedTextField(rpm,{rpm=it},label={Text("Requests per minute (1–120)")})
        Button(enabled=!com.meshlit.BuildConfig.PLAY_REVIEW && name.isNotBlank() && scopes.isNotEmpty(),onClick={
            try {
                fun entries(value:String)=value.split(',').map{it.trim()}.filter{it.isNotBlank()}.toSet()
                issued=host.issueClient(name,entries(models),scopes,if(GatewayScope.MCP in scopes) entries(tools) else emptySet(),hours.toInt(),limit.toInt(),rpm.toInt());error=null
            } catch(_:Exception){error="Could not issue key. Check IDs, expiry, limits and the 64-client limit."}
        }){Text("Issue client key and stop")}
        clients.forEach{client->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)) {
            Text(client.name,style=MaterialTheme.typography.titleMedium)
            Text("${if(client.revoked) "Revoked" else if(client.expiresAtMs<=System.currentTimeMillis()) "Expired" else "Enabled"} · ${client.scopes.joinToString()} · ${client.requestsPerMinute}/minute · 1 active request")
            Text("Expires ${DateFormat.getDateTimeInstance().format(Date(client.expiresAtMs))}")
            Text("Models: ${client.modelIds.joinToString().ifBlank{"none"}}")
            FlowRow {TextButton(enabled=!client.revoked,onClick={host.revokeClient(client.id)}){Text("Revoke and stop")}
                TextButton(onClick={host.removeClient(client.id)}){Text("Remove and stop")}}
        }}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    issued?.let{key->AlertDialog(onDismissRequest={issued=null},title={Text("Client key created")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("Copy this key now and store it in the client's secret storage. The app retains only its verifier and cannot show it again. Copying places a secret on your clipboard. Use an encrypted private tunnel or HTTPS endpoint.")
        Text("Client: ${key.client.name}")
    }},confirmButton={TextButton(onClick={clipboard.setText(AnnotatedString(key.key))}){Text("Copy key")}},dismissButton={TextButton(onClick={issued=null}){Text("Done")}})}
}
