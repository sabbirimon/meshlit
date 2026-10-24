package com.meshlit.pipeline
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.models.ModelLibrary
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@Composable fun PipelinePanel() {
    val host=koinInject<PipelineHost>();val library=koinInject<ModelLibrary>()
    val status by host.status.collectAsStateWithLifecycle();val models by library.models.collectAsStateWithLifecycle()
    val context=androidx.compose.ui.platform.LocalContext.current
    var pairingAddress by remember{mutableStateOf((context.applicationContext as com.meshlit.MeshlitApplication).localIpAddress)}
    var scanned by remember{mutableStateOf<PipelinePeer?>(null)}
    var qr by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    var peers by remember{mutableStateOf<List<PipelinePeer>>(emptyList())}
    var assessments by remember{mutableStateOf<List<WorkerAssessment>>(emptyList())}
    var group by remember{mutableStateOf("default")};var groups by remember{mutableStateOf(listOf("default"))}
    var creatingGroup by remember{mutableStateOf(false)};var newGroup by remember{mutableStateOf("")}
    var add by remember{mutableStateOf(false)};var address by remember{mutableStateOf("")}
    var fingerprint by remember{mutableStateOf("")};var token by remember{mutableStateOf("")}
    var localPairing by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var delegated by remember{mutableStateOf(host.agentControlAllowed())}
    var selected by remember{mutableStateOf("")}
    LaunchedEffect(host){withContext(Dispatchers.IO){group=host.activeGroup();groups=host.groups();peers=host.groupPeers()};assessments=host.assessGroup()}
    fun action(block:suspend()->Unit){scope.launch{try{error=null;block()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}}
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)) {
        Text("Layer pipeline",style=MaterialTheme.typography.titleMedium)
        Text("Experimental native layer execution on approved workers. Off by default. Use trusted devices on your private network.")
        Text("Worker connections need an allowed TCP 50551 rule in Settings → Network rules. After the TLS identity upgrade, verify the new fingerprint and pair existing workers again.",style=MaterialTheme.typography.bodySmall)
        Text("Offline: connect devices to the same LAN, hotspot or configured virtual network and use its reachable address. Internet: use a VPN or reachable TCP endpoint. This layer transport does not use the membership relay. Every connection checks the full pinned TLS certificate and token.",style=MaterialTheme.typography.bodySmall)
        Text("The coordinator keeps the complete GGUF on disk and uploads tensors to workers. This is layer offload; large-model and physical-phone verification are still required.",style=MaterialTheme.typography.bodySmall)
        if(!host.available()) Text("Native pipeline runtime is not packaged for this device ABI.")
        Text("Worker: ${if(status.worker) "Listening with TLS on ${host.publicPort}" else "Off"} · Coordinator: ${if(status.coordinator) "Active" else "Off"}")
        Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
            OutlinedButton(enabled=host.available() && !status.starting,onClick={action{if(status.worker) host.stopWorker() else host.startWorker()}}) {
                Text(if(status.worker) "Stop worker" else "Start worker") }
            TextButton(enabled=status.worker,onClick={action{localPairing=withContext(Dispatchers.IO){
                LayerPairingQr.encode(PipelinePeer(pairingAddress.trim(),host.publicPort,host.fingerprint(),host.pairingToken()))
            }}}){Text("Show pairing QR")}
        }
        OutlinedTextField(pairingAddress,{pairingAddress=it.take(253)},label={Text("Reachable host: LAN IP, VPN IP or DNS name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("For Internet pairing, configure a private VPN or reachable TCP 50551 endpoint first. Offline networks need no server. The QR includes a private token; share it only with the coordinator.",style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=!status.worker && !status.coordinator,onClick={action{withContext(Dispatchers.IO){host.rotatePairingToken()}}}){Text("Rotate pairing token")}
        Row {Text("Allow agents to control the paired cluster",Modifier.weight(1f));Switch(delegated,{delegated=it;host.setAgentControlAllowed(it)})}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Cluster group: $group",style=MaterialTheme.typography.titleSmall)
            TextButton(enabled=!status.coordinator && !status.starting,onClick={creatingGroup=true}){Text("New group")}}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(T.small)) {
            groups.forEach{name->FilterChip(selected=group==name,onClick={action{val updated=withContext(Dispatchers.IO){host.setActiveGroup(name)};group=name;peers=updated;assessments=host.assessGroup()}},
                enabled=!status.coordinator && !status.starting,label={Text(name)})}
        }
        Text("${peers.size} approved workers in this group")
        TextButton(enabled=peers.isNotEmpty() && !status.starting,onClick={action{assessments=host.assessGroup()}}){Text("Assess workers")}
        Text("Risk is a 0–100 admission heuristic from live memory, thermal state and control latency. It is not a benchmark, bandwidth test or security certification.",style=MaterialTheme.typography.bodySmall)
        if(status.memoryBudgetBytes>0) Text("Negotiated memory estimate: ${com.meshlit.ui.modern.formatBytes(status.memoryBudgetBytes)}")
        if(status.estimatedKvBytes>0) Text("Estimated KV: ${com.meshlit.ui.modern.formatBytes(status.estimatedKvBytes)} · allocation remains subject to native placement")
        peers.forEach { peer -> Column {
            Row {Text("${peer.host}:${peer.port}",Modifier.weight(1f))
            TextButton(enabled=!status.coordinator && !status.starting,onClick={action{peers=withContext(Dispatchers.IO){host.removePeer(peer)};assessments=host.assessGroup()}}){Text("Remove")}
            }
            assessments.firstOrNull{it.peer.host==peer.host && it.peer.port==peer.port}?.let{assessment->
                val offer=assessment.offer
                Text("${offer?.deviceClass ?: "Unknown device"} · ${offer?.abi ?: "Unknown ABI"} · ${offer?.backend ?: "Unknown backend"} · risk ${assessment.riskScore}/100",
                    style=MaterialTheme.typography.bodySmall)
                if(offer!=null) Text("Available memory ${com.meshlit.ui.modern.formatBytes(offer.freeMemoryBytes)} · storage ${com.meshlit.ui.modern.formatBytes(offer.freeDiskBytes)} · worker threads ${offer.cpuThreads} · thermal ${offer.thermalStatus} · control RTT ${assessment.latencyMs} ms",
                    style=MaterialTheme.typography.bodySmall)
                Text(assessment.reason,style=MaterialTheme.typography.bodySmall)
            }
        } }
        OutlinedButton(enabled=!status.coordinator && !status.starting,onClick={action{
            when(val scan=com.meshlit.devices.QrScanner.scan(context)){
                is com.meshlit.devices.QrScanner.ScanResult.Success -> scanned=LayerPairingQr.decode(scan.rawValue)
                com.meshlit.devices.QrScanner.ScanResult.Cancelled ->Unit
                else ->error="QR scanner unavailable; use manual pairing"
            }
        }}){Text("Scan worker QR")}
        OutlinedButton(enabled=!status.coordinator && !status.starting,onClick={add=true}){Text("Add approved worker")}
        if(status.local) Text("Native local inference is active; no remote workers are being used.")
        if(status.starting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.coordinator) Button(onClick={action{host.stopPipeline()}}){Text("Stop pipeline")}
        else {
            models.filter{it.installed}.forEach { model ->
                Row {RadioButton(selected==model.path,{selected=model.path});Text(model.name,Modifier.padding(top=T.medium))}
            }
            Button(enabled=host.available() && peers.size>=2 && selected.isNotBlank() && !status.starting,
                onClick={action{host.startPipeline(selected,models.first{it.path==selected}.runtimeOptions.contextSize,models.first{it.path==selected}.runtimeOptions.keyCacheType)}}){Text("Negotiate and load cluster")}
        }
        (error ?: status.error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(status.log.isNotEmpty()) Text(status.log.takeLast(4).joinToString("\n"),style=MaterialTheme.typography.bodySmall)
    } }
    if(creatingGroup) AlertDialog(onDismissRequest={creatingGroup=false;newGroup=""},title={Text("New cluster group")},text={Column {
        Text("Pair workers into this group, then select it before loading a model.")
        OutlinedTextField(newGroup,{newGroup=it.take(32)},label={Text("Group name")},singleLine=true)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={action{val selected=newGroup.trim();peers=withContext(Dispatchers.IO){host.setActiveGroup(selected)};group=selected;groups=host.groups();assessments=emptyList();creatingGroup=false;newGroup=""}}){Text("Create")}},
        dismissButton={TextButton(onClick={creatingGroup=false;newGroup=""}){Text("Cancel")}})
    if(add) AlertDialog(onDismissRequest={add=false;token=""},title={Text("Approve worker")},text={Column {
        Text("Keep the worker running. Enter its address, full certificate hash and private token. Compare the short check code on both devices before approval.")
        OutlinedTextField(address,{address=it},label={Text("Host address (TLS port 50551)")})
        OutlinedTextField(fingerprint,{fingerprint=it},label={Text("Certificate SHA-256")})
        OutlinedTextField(token,{token=it},label={Text("Pairing token")},visualTransformation=PasswordVisualTransformation())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={action{val candidate=PipelinePeer(address.trim(),50551,fingerprint.trim(),token.trim());peers=host.verifyAndSavePeer(candidate);groups=host.groups();assessments=host.assessGroup();add=false;token=""}}){Text("Verify and approve")}},dismissButton={TextButton(onClick={add=false;token=""}){Text("Cancel")}})
    scanned?.let{peer ->AlertDialog(onDismissRequest={scanned=null},title={Text("Approve scanned worker")},
        text={Column {Text("${peer.host}:${peer.port}\nIdentity check code: ${LayerPairingQr.identityCode(peer.fingerprint)}\nCertificate SHA-256: ${peer.fingerprint}\nCompare the code on the worker before approving. The full certificate and token are verified on connect.")
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)} }},
        confirmButton={TextButton(onClick={action{peers=host.verifyAndSavePeer(peer);groups=host.groups();assessments=host.assessGroup();scanned=null}}){Text("Verify and approve")}},
        dismissButton={TextButton(onClick={scanned=null}){Text("Cancel")}})}
    LaunchedEffect(localPairing){qr=localPairing?.let{value ->withContext(Dispatchers.Default){
        val matrix=com.google.zxing.MultiFormatWriter().encode(value,com.google.zxing.BarcodeFormat.QR_CODE,512,512)
        android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888).apply{
            val pixels=IntArray(512*512){i ->if(matrix[i%512,i/512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE};setPixels(pixels,0,512,0,0,512,512)
        }
    }}}
    localPairing?.let { pairing -> AlertDialog(onDismissRequest={localPairing=null},title={Text("Pairing details")},text={Column{qr?.let{androidx.compose.foundation.Image(it.asImageBitmap(),"Private worker pairing QR",Modifier.fillMaxWidth())};Text("Identity check code: ${LayerPairingQr.identityCode(host.fingerprint())}");Text("Compare this code on the coordinator before approval. The QR contains a private token; share only with devices you approve.")}},
        confirmButton={TextButton(onClick={clipboard.setText(AnnotatedString(pairing));localPairing=null}){Text("Copy")}},
        dismissButton={TextButton(onClick={localPairing=null}){Text("Close")}}) }
}
