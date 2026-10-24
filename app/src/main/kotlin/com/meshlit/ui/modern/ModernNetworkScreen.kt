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
@Composable fun ModernNetworkScreen(onBack:()->Unit,onDashboard:()->Unit,initialPage:String?=null,
    onDevices:(()->Unit)?=null,focusPeerNodeId:String?=null) {
    var page by rememberSaveable{mutableStateOf(initialPage)}
    var focusedPeer by rememberSaveable { mutableStateOf(focusPeerNodeId) }
    var fromExternalService by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    BackHandler(page!=initialPage){page=initialPage}
    if(page=="relay"){com.meshlit.ui.v2.screens.RelaySettingsScreen();return}
    if(page=="join"){com.meshlit.ui.v2.screens.RelayJoinScreen();return}
    if(page=="identity-recovery"){com.meshlit.ui.v2.screens.RecoveryScreen();return}
    if(page=="rewards"){com.meshlit.ui.v2.screens.RewardsScreen(onBack={page=null});return}
    if(page=="device_info"){com.meshlit.ui.v2.screens.DeviceInfoScreen(onBack={page=initialPage});return}
    if(page=="ssh"){SshConnectionsScreen{page=if(fromExternalService) "external-services" else null};return}
    if(page=="firewall"){FirewallSettingsScreen{page=null};return}
    if(page=="app"){com.meshlit.ui.v2.screens.DeviceWorkspaceScreen({page=it},focusedPeer);return}
    if(page=="saved-qrs"){DeviceManagementScreen({page="app"},{page=it});return}
    if(page=="sharding"){ShardingSettingsScreen(onBack={page="app"});return}
    if(page=="scan"){com.meshlit.ui.v2.screens.V2ScanScreen(onOpenQrPairing={page="app"},onBack={page=initialPage});return}
    if(page=="external-services"){ExternalServiceScreen({page=initialPage}) { fromExternalService=true;page=it };return}
    if(page=="web-bridge"){WebBridgeEnrollmentScreen{page="external-services"};return}
    if(page=="mcp-catalog"){McpCatalogScreen({page="external-services"},{page="gateway"});return}
    if(page=="providers"){OnlineProvidersScreen{page="external-services"};return}
    if(page=="gateway"){GatewayScreen{page="mcp-catalog"};return}
    if(page=="radar"){RadarScreen({page=initialPage},{page="scan"},{page="meshchat"},{page="logs"},{page="host-map"},
        {focusedPeer=it;page="app"});return}
    if(page=="host-map"){HostMapScreen({page="radar"},{page="privacy"},{page="app"});return}
    if(page=="privacy"){SecurityPrivacyScreen({page="host-map"},{page=it});return}
    if(page=="meshchat"){PeerChatScreen{page="radar"};return}
    if(page=="logs"){ModernLogsScreen(onBack={page="radar"});return}
    if(page=="forward"){com.meshlit.ui.screens.settings.ForwardingPeersScreen({page=null});return}
    Scaffold(topBar={TopAppBar(title={Text("Network and pairing")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{Text("Connect devices",style=MaterialTheme.typography.headlineSmall)
                Text("Choose a path for another phone, a remote cluster, or a browser device.")}
            item { NetworkMethod("Direct device pairing · local or Internet",
                "Discover devices on the same network, or add a verified HTTPS endpoint or pairing link for a remote device. A VPN can make remote devices reachable privately.") {
                Button({if(onDevices!=null) onDevices() else page="app"},Modifier.fillMaxWidth()) { Text("Open Devices and pair") }
                OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))},modifier=Modifier.fillMaxWidth()) {
                    Text("Open Android VPN settings")
                }
            } }
            item { NetworkMethod("Join or host a relay cluster",
                "Join with its address and current six-digit code, or host one here. The network path can be local, VPN, or a reachable Internet endpoint.") {
                OutlinedButton({page="join"},Modifier.fillMaxWidth()) { Text("Join cluster") }
                OutlinedButton({page="relay"},Modifier.fillMaxWidth()) { Text("Host relay") }
            } }
            item { Text("For a browser or API client, use Web/API devices and groups below. That path enrolls a control client, not a compute worker.",
                style=MaterialTheme.typography.bodySmall) }
            item { NetworkMethod("Other LAN or VPN services","Check and save a manually entered local, VPN or HTTPS URL for a device without Meshlit. Reachability does not grant control or compute access.") {
                OutlinedButton(onClick={page="external-services"},modifier=Modifier.fillMaxWidth()) { Text("Add external service") }
            } }
            item { Column(verticalArrangement=Arrangement.spacedBy(T.small)) {
                OutlinedButton({page="radar"},Modifier.fillMaxWidth()) { Text("Device radar") }
                OutlinedButton({page="host-map"},Modifier.fillMaxWidth()) { Text("Global host map") }
                OutlinedButton({page="meshchat"},Modifier.fillMaxWidth()) { Text("Mesh chat") }
            } }
            item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Filter connection methods")},singleLine=true)}
            if("Meshlit QR LAN VPN app".contains(query,true) || query.isBlank()) item{NetworkMethod("Meshlit devices","Discovery, pairing and approvals live in Devices. Network scope and transport options are configured here."){
                Button(onClick={if(onDevices!=null) onDevices() else page="app"}){Text("Open Devices")}
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
            if("web API browser groups companion no app".contains(query,true) || query.isBlank()) item {
                NetworkMethod("Web/API clients without Meshlit","Start the authenticated bridge, create an invitation and review browser or API client requests.") {
                    OutlinedButton(onClick={page="web-bridge"},modifier=Modifier.fillMaxWidth()) { Text("Manage Web/API bridge") }
                }
            }

            if("Bluetooth nearby discovery".contains(query,true) || query.isBlank()) item{NetworkMethod("Bluetooth","Meshlit has BLE discovery and a transport scan surface. Discovery or Android bonding alone does not authorize a compute worker; layer traffic currently uses pinned TLS over IP."){
                Button(onClick={if(onDevices!=null) onDevices() else page="scan"}){Text("Browse device list")}
                OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}){Text("Open Bluetooth settings")}
            }}
            item{OutlinedButton(onClick={page="firewall"}){Text("Network status and firewall rules")}}
            if("rewards wallet contribution credits".contains(query,true) || query.isBlank()) item {
                NetworkMethod("Mesh rewards","Review local signed contribution receipts and back up this device's reward history. Credits are not spendable or transferable.") {
                    OutlinedButton(onClick={page="rewards"}) { Text("Open rewards wallet") }
                }
            }
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
