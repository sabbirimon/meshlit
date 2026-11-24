package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.mcp.control.*
import com.meshlit.devices.DeviceConnectionHost
import com.meshlit.di.koinInject
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DeviceConnectionAccessPanel(onOpen:(String)->Unit) {
    val host=koinInject<DeviceConnectionHost>()
    val state by host.coordinator.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var sources by remember{mutableStateOf<List<DeviceConnectionSource>>(emptyList())}
    var reviewing by remember{mutableStateOf<DeviceConnectionSource?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    LaunchedEffect(host) { while(isActive){sources=withContext(Dispatchers.IO){host.sources()};delay(2000)} }
    fun save(action:()->Unit) { scope.launch {
        busy=true;try{withContext(Dispatchers.IO){action()};error=null}catch(e:Exception){error=e.message ?: "Connection approval could not be saved"}finally{busy=false}
    } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row { Text("Connection assistance",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Switch(state.policy.enabled,{enabled->save{host.coordinator.setEnabled(enabled)}},enabled=!busy,modifier=Modifier.semantics{contentDescription="Allow agent device connection assistance"}) }
        Text("Let Chat and agents check exact saved sources you approve below. Enable Device connection assistance in that chat's tool choices. No new identities, credentials, commands or worker roles are approved automatically.")
        Text("Two bounded read-only contact attempts can recover a short network change when the same approved address and identity remain valid. Broken tasks and uncertain actions are not replayed.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        sources.forEach { source ->
            val approved=state.policy.grants[source.id]==source.binding
            Row { Column(Modifier.weight(1f)) {
                Text(source.name,style=MaterialTheme.typography.titleSmall)
                Text(source.transport.name.lowercase().replace('_',' '),style=MaterialTheme.typography.bodySmall)
                Text(source.identityLabel,style=MaterialTheme.typography.bodySmall)
                if(!source.supported)Text(source.setupReason,style=MaterialTheme.typography.bodySmall)
            }
                Switch(approved,{on->if(on)reviewing=source else save{host.coordinator.setSourceAllowed(source.id,false)}},enabled=!busy && source.supported,
                    modifier=Modifier.semantics{contentDescription="Allow contact checks for ${source.name}"})
            }
        }
        if(sources.isEmpty())Text("No saved sources or opt-in nearby advertisements are available.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TextButton(onClick={onOpen("web-bridge")}){Text("Web devices")}
            TextButton(onClick={onOpen("ssh")}){Text("SSH setup")}
            TextButton(onClick={onOpen("providers")}){Text("Model APIs")}
            TextButton(onClick={onOpen("mcp-catalog")}){Text("MCP setup")}
            TextButton(onClick={onOpen("radar")}){Text("Nearby pairing")}
        }
        state.receipts.takeLast(4).asReversed().forEach { Text("${it.name}: ${it.phase.name.lowercase().replace('_',' ')} · ${it.detail}",style=MaterialTheme.typography.bodySmall) }
    } }
    reviewing?.let { source -> AlertDialog(onDismissRequest={reviewing=null},title={Text("Approve this saved connection?")},
        text={Text("${source.name}\n${source.identityLabel}\n\nAllow automatic contact/identity checks only. Existing source credentials stay in their saved store. Model use, commands, first enrollment and compute still need their own scopes. A changed identity/configuration needs new approval.")},
        confirmButton={TextButton(onClick={reviewing=null;save{host.coordinator.setSourceAllowed(source.id,true,source.binding)}}){Text("Approve contact checks")}},
        dismissButton={TextButton(onClick={reviewing=null}){Text("Cancel")}}) }
}

@Composable internal fun ChatDeviceConnections(offer:DeviceConnectionOffer?,receipt:DeviceConnectionReceipt?,enabled:Boolean,onConnect:(String,String)->Unit,onRefresh:()->Unit,onAccess:()->Unit) {
    var expired by remember(offer?.id){mutableStateOf(offer!=null && offer.expiresAtMs<=System.currentTimeMillis())}
    LaunchedEffect(offer?.id) { offer?.let{delay((it.expiresAtMs-System.currentTimeMillis()).coerceAtLeast(0));expired=true} }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Device connections",style=MaterialTheme.typography.titleSmall)
        offer?.choices?.forEach { choice ->
            Text(choice.name,style=MaterialTheme.typography.titleSmall)
            Text("${choice.transport.name.lowercase().replace('_',' ')} · ${choice.reason}",style=MaterialTheme.typography.bodySmall)
            Text(choice.identityLabel,style=MaterialTheme.typography.bodySmall)
            Button(onClick={onConnect(offer.id,choice.id)},enabled=enabled && choice.canConnect && !expired){Text("Check approved connection")}
        }
        if(expired)Text("These choices expired. Refresh before connecting.",style=MaterialTheme.typography.bodySmall)
        receipt?.let{Text("${it.phase.name.lowercase().replace('_',' ')} · ${it.detail}",color=if(it.phase==ConnectionPhase.VERIFIED)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
        Row { TextButton(onClick=onRefresh,enabled=enabled){Text("Refresh choices")};TextButton(onClick=onAccess){Text("Connection access")} }
    } }
}
