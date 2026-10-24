package com.meshlit.ui.modern
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.firewall.*
import com.meshlit.settings.SettingsRepository
import com.meshlit.di.koinInject
import kotlinx.coroutines.*
import java.util.UUID
@OptIn(ExperimentalLayoutApi::class)
@Composable fun FirewallSettingsScreen(onBack:()->Unit) {
    val context=LocalContext.current;val repository=koinInject<SettingsRepository>();val policy by repository.firewallFlow.collectAsStateWithLifecycle(PortLayerPolicy());val scope=rememberCoroutineScope()
    var port by remember{mutableStateOf("18792")};var allowed by remember{mutableStateOf(true)};var priority by remember{mutableStateOf("10")};var query by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var network by remember{mutableStateOf("Probing…")}
    LaunchedEffect(Unit){while(isActive){val manager=context.getSystemService(ConnectivityManager::class.java);val caps=manager.activeNetwork?.let{manager.getNetworkCapabilities(it)};network="Validated internet: ${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true} · metered: ${manager.isActiveNetworkMetered} · VPN: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}";delay(5000)}}
    fun save(next:PortLayerPolicy){scope.launch{try{repository.setFirewallPolicy(next)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Network rules",style=MaterialTheme.typography.headlineSmall);Text(network)
            Text("Rules gate new Meshlit inbound connections: legacy inference, HTTPS control (18792), and RPC worker TLS (50551). Existing sessions are not retroactively disconnected. This is an app listener firewall, not Android-wide VPN/iptables enforcement. Cloud, SSH and other outbound traffic are not governed by this editor.")
            Text("IPv4 private LAN ranges are required by the separate address layer; a port allow does not authorize WAN, IPv6 or a device identity. Keep endpoint authentication and pairing enabled.")}
        item{Row{Text("Default allow for eligible LAN ports",Modifier.weight(1f));Switch(policy.defaultAction==PortDefaultAction.ALLOW,{save(policy.copy(defaultAction=if(it) PortDefaultAction.ALLOW else PortDefaultAction.DENY))})}
            OutlinedTextField(query,{query=it},label={Text("Filter rules")})}
        items(policy.rules.filter{"${it.id} ${it.portSpec.describe()} ${it.reason}".contains(query,true)},key={it.id}){rule->Card{Column(Modifier.padding(16.dp)){Text("${if(rule.allowed) "ALLOW" else "DENY"} ${rule.protocol} ${rule.direction} ${rule.portSpec.describe()} · priority ${rule.priority}");Text(rule.reason);TextButton(onClick={save(policy.copy(rules=policy.rules.filterNot{it.id==rule.id}))}){Text("Remove rule")}}}}
        item{Text("Add inbound TCP rule",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(port,{port=it},label={Text("Port (1–65535)")});OutlinedTextField(priority,{priority=it},label={Text("Priority (higher first)")})
            FlowRow{FilterChip(allowed,{allowed=true},label={Text("Allow")});FilterChip(!allowed,{allowed=false},label={Text("Deny")})}
            Button(onClick={try{val number=port.toIntOrNull() ?: error("Invalid port");val order=priority.toIntOrNull() ?: error("Invalid priority");val rule=PortRule(UUID.randomUUID().toString(),portSpec=PortSpec.Single(number),allowed=allowed,priority=order,reason="User-configured listener rule");val updated=policy.copy(rules=policy.rules+rule);updated.validate();save(updated)}catch(e:Exception){error=e.message}}){Text("Add rule")}
            OutlinedButton(onClick={save(PortLayerPolicy())}){Text("Reset to deny all ports")};error?.let{ErrorCard(it){error=null}}}
    }
}
