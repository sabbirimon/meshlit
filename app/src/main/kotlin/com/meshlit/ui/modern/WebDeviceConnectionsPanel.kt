package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.meshlit.resources.WebDevices
import kotlinx.coroutines.*
@Composable internal fun WebDeviceConnectionsPanel() {
    val context=LocalContext.current;val clients=remember{WebDevices.get(context)}
    val peers by clients.devices.collectAsState();val readings by clients.observations.collectAsState();val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf("Remote device")};var url by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")};var invite by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun action(block:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{block();error=null}catch(e:CancellationException){throw e}catch(_:Exception){error="Could not complete device request. Check HTTPS reachability, certificate pin, invitation, receiver approval and Network rules."}finally{busy=false}}}
    Text("Connect to another Meshlit device",style=MaterialTheme.typography.titleMedium)
    Text("Works over reachable HTTPS on LAN, VPN or an authenticated public endpoint. LTE/4G alone does not provide an inbound route. Verify the receiving device’s certificate identity before enrollment.")
    OutlinedTextField(name,{name=it},label={Text("Device label")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(url,{url=it},label={Text("Receiving device HTTPS address")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(pin,{pin=it},label={Text("Verified certificate SHA-256 (optional with trusted CA)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(invite,{invite=it},label={Text("Receiving device invitation")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
    Button(enabled=!busy && url.isNotBlank() && invite.isNotBlank(),onClick={action{clients.enroll(name,url.trim(),pin.replace(":","").trim().takeIf{it.isNotBlank()},invite.trim());invite=""}}){Text("Request pairing")}
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    peers.forEach{peer->
        Text("${peer.name} · ${peer.state}",style=MaterialTheme.typography.titleSmall)
        Text(peer.url)
        Row{OutlinedButton(enabled=!busy,onClick={action{clients.status(peer.id)}}){Text("Check approval")};OutlinedButton(enabled=!busy,onClick={action{clients.readResources(peer.id)}}){Text("Read resources")}}
        Row{Switch(peer.agentRead,{clients.permission(peer.id,agentRead=it)});Text("Allow agent observation reads")}
        var model by remember(peer.id){mutableStateOf(peer.modelId)}
        OutlinedTextField(model,{model=it},label={Text("Remote loaded model ID for teams")})
        Row{Switch(peer.teamAllowed,{allowed->try{clients.permission(peer.id,teamAllowed=allowed,modelId=model)}catch(_:Exception){error="Choose a valid local model ID"}},enabled=model.isNotBlank());Text("Allow team text jobs for this model")}
        readings[peer.id]?.let{sample->Text("${if(sample.fresh(System.currentTimeMillis()))"Fresh" else "Stale"} · receipt ${sample.receiptId.take(8)} · CPU ${sample.cpuCores ?: "unknown"} · RAM ${sample.availableRamBytes ?: "unknown"} bytes · ${sample.sensors.size} shared sensor readings")}
        TextButton(enabled=!busy,onClick={action{clients.disconnect(peer.id)}}){Text("Disconnect and revoke at receiver")}
    }
}
