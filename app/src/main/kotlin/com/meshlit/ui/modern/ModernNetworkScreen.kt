package com.meshlit.ui.modern

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.meshlit.ui.theme.ChatTokens as T

/** Pairing and discovery are distinct from readiness for model computation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ModernNetworkScreen(onBack:()->Unit) {
    var page by rememberSaveable{mutableStateOf<String?>(null)}
    var query by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    BackHandler(page!=null){page=null}
    if(page=="relay"){com.meshlit.ui.v2.screens.RelaySettingsScreen();return}
    if(page=="join"){com.meshlit.ui.v2.screens.RelayJoinScreen();return}
    if(page=="identity-recovery"){com.meshlit.ui.v2.screens.RecoveryScreen();return}
    if(page=="rewards"){com.meshlit.ui.v2.screens.RewardsScreen();return}
    if(page=="device_info"){com.meshlit.ui.v2.screens.DeviceInfoScreen{page=null};return}
    if(page=="ssh"){SshConnectionsScreen{page=null};return}
    if(page=="firewall"){FirewallSettingsScreen{page=null};return}
    if(page=="app"){com.meshlit.ui.v2.screens.DeviceWorkspaceScreen{page=it};return}
    if(page=="scan"){com.meshlit.ui.v2.screens.V2ScanScreen(onOpenQrPairing={page="app"},onBack={page=null});return}
    if(page=="forward"){com.meshlit.ui.screens.settings.ForwardingPeersScreen({page=null});return}
    Scaffold(topBar={TopAppBar(title={Text("Network and pairing")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{Text("Connect your hive",style=MaterialTheme.typography.headlineSmall)
                Text("Enroll devices, approve access, then verify the capabilities each node can offer.")}
            item { Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                Button({page="app"}) { Text("Devices") }
                OutlinedButton({page="join"}) { Text("Join cluster") }
                OutlinedButton({page="relay"}) { Text("Host relay") }
            } }
            item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Filter connection methods")},singleLine=true)}
            if("Meshlit QR LAN VPN app".contains(query,true) || query.isBlank()) item{NetworkMethod("Meshlit devices","QR/manual endpoint enrollment and network scope settings. Legacy routing endpoints have a separate trust model from native layer workers."){
                Button(onClick={page="app"}){Text("Pair or manage endpoints")}
            }}
            if("layer workers native TLS model cluster".contains(query,true) || query.isBlank()) item{
                com.meshlit.pipeline.PipelinePanel()
            }
            if("forward routing peer".contains(query,true) || query.isBlank()) item{NetworkMethod("Inference forwarding","Manage the existing independent-job forwarding router. This list does not establish layer-worker TLS pairing."){
                OutlinedButton(onClick={page="forward"}){Text("Manage forwarding peers")}
            }}
            if("SSH NAS server terminal".contains(query,true) || query.isBlank()) item{NetworkMethod("SSH hosts without the app","Execute bounded SSH commands using saved encrypted credentials and independently verified host-key pins. Remote worker deployment is a separate operation."){
                Button(onClick={page="ssh"}){Text("SSH connections")}
            }}
            if("web API browser groups companion no app".contains(query,true) || query.isBlank()) item{DeviceCompanionPanel()}

            if("Bluetooth nearby discovery".contains(query,true) || query.isBlank()) item{NetworkMethod("Bluetooth","Meshlit has BLE discovery and a transport scan surface. Discovery or Android bonding alone does not authorize a compute worker; layer traffic currently uses pinned TLS over IP."){
                Button(onClick={page="scan"}){Text("Scan nearby devices")}
                OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}){Text("Open Bluetooth settings")}
            }}
            item{OutlinedButton(onClick={page="firewall"}){Text("Network status and firewall rules")}}
            item{NetworkMethod("Shared task memory","Planned phone-first design: replicate compact task/session journals across enrolled phones and serve large model files/checkpoints from selected storage holders. Today journals are local; durable replication, leases and recovery remain to be implemented."){
                Text("Compute, storage, tool, routing and monitoring roles must be negotiated independently.",style=MaterialTheme.typography.bodySmall)
            }}
        }
    }
}
@Composable private fun NetworkMethod(title:String,description:String,actions:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
        Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
            Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall);actions()
        }
    }
}
