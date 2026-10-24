package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.control.WebBridgeHost
import com.meshlit.core.mcp.control.*
import com.meshlit.di.koinInject
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.util.UUID

@Composable fun WebBridgeEnrollmentScreen(onBack:()->Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back") }
            Text("Meshlit Web/API bridge",style=MaterialTheme.typography.headlineSmall)
            Text("A browser or other client can join this phone through an invitation. Review each request and approve only its needed access.") }
        item { DeviceCompanionPanel() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DeviceCompanionPanel(){
    val host=koinInject<WebBridgeHost>();val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    val active by host.active.collectAsState();var snapshot by remember{mutableStateOf(DeviceDirectorySnapshot())}
    var invitation by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var groupName by remember{mutableStateOf("")};var groupMembers by remember{mutableStateOf(setOf<String>())}
    var approval by remember{mutableStateOf<EnrolledDevice?>(null)};var access by remember{mutableStateOf(setOf(DeviceAccess.OBSERVE))}
    var revoke by remember{mutableStateOf<EnrolledDevice?>(null)};var fingerprint by remember{mutableStateOf("")}
    fun action(block:suspend()->Unit){scope.launch{try{withContext(Dispatchers.IO){block();snapshot=host.directory.read()}}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message ?: "Device operation failed"}}}
    LaunchedEffect(host){while(isActive){snapshot=withContext(Dispatchers.IO){host.directory.read()};delay(2000)}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
        Text("Web/API devices and groups",style=MaterialTheme.typography.titleMedium)
        Text("No app install needed for control. Native compute and file storage need their own verified host runtime. Groups here are local selections, not running clusters.")
        Row{Text("Authenticated TLS interface",Modifier.weight(1f));Switch(active,{value->action{if(value){host.start();fingerprint=host.fingerprint()}else{host.stop();invitation=null}}})}
        if(active){val address=host.ownAddress();val browserUrl="https://$address:${host.port}/"
            Text(if(address.isBlank()) "Waiting for a reachable network address" else browserUrl)
            TextButton(enabled=address.isNotBlank(),onClick={clipboard.setText(AnnotatedString(browserUrl))}) { Text("Copy browser address") }
            Text("For a VPN client, use this phone's reachable VPN address with the same port after allowing that network in Network rules.",style=MaterialTheme.typography.bodySmall)
            if(fingerprint.isBlank()) LaunchedEffect(active){fingerprint=withContext(Dispatchers.IO){host.fingerprint()}}
            Text("Verify SHA-256: $fingerprint",style=MaterialTheme.typography.bodySmall)
            Text("The browser may require approving the phone's self-signed certificate after checking its fingerprint.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={action{invitation=host.newInvitation()}}){Text("Create 15-minute invitation")}
            invitation?.let{invite->OutlinedTextField(invite,{},readOnly=true,visualTransformation=PasswordVisualTransformation(),label={Text("Private enrollment invitation")})
                TextButton(onClick={clipboard.setText(AnnotatedString(invite))}){Text("Copy invitation")}}
        }
        Text("${snapshot.devices.count{it.state==EnrollmentState.PENDING}} waiting · ${snapshot.devices.count{it.state==EnrollmentState.APPROVED && it.expiresAtMs>System.currentTimeMillis()}} active · ${snapshot.devices.count{it.state==EnrollmentState.APPROVED && it.expiresAtMs<=System.currentTimeMillis()}} expired")
        snapshot.devices.forEach{device->Column{
            Text("${device.name} · ${device.kind} · ${device.state}")
            Text("Claimed roles: ${device.requestedRoles.joinToString()} · control credentials approved separately",style=MaterialTheme.typography.bodySmall)
            Text("Access: ${device.access.joinToString()} · last request ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(device.lastSeenAtMs))}",style=MaterialTheme.typography.bodySmall)
            Text("Credential expires ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(device.expiresAtMs))}${if(device.expiresAtMs<=System.currentTimeMillis()) " · expired" else ""}",style=MaterialTheme.typography.bodySmall)
            if(device.state==EnrollmentState.PENDING) TextButton(onClick={approval=device;access=setOf(DeviceAccess.OBSERVE)}){Text("Review access")}
            if(device.state==EnrollmentState.APPROVED){Row{Checkbox(device.id in groupMembers,{groupMembers=if(it) groupMembers+device.id else groupMembers-device.id});Text("Select for group")}
                TextButton(onClick={revoke=device}){Text("Revoke access")}}
        }}
        if(snapshot.devices.isEmpty()) Text("Start the interface, create an invitation, and open the address on your other device.")
        if(groupMembers.isNotEmpty()){OutlinedTextField(groupName,{groupName=it},label={Text("Group name")});Button(enabled=groupName.isNotBlank(),onClick={action{host.directory.group(UUID.randomUUID().toString(),groupName,groupMembers);groupName="";groupMembers=emptySet()}}){Text("Save selected group")}}
        snapshot.groups.forEach{group ->Row{Text("${group.name}: ${group.memberIds.size} selected",Modifier.weight(1f));TextButton(onClick={action{host.directory.removeGroup(group.id)}}){Text("Remove group")}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }}
    approval?.let{device->AlertDialog(onDismissRequest={approval=null},title={Text("Approve ${device.name}?")},text={Column{
        Text("Identity: ${device.id}. This approves a control credential, not attested compute. Mutations also need saved agent delegation.")
        FlowRow{DeviceAccess.entries.forEach{value->FilterChip(value in access,{access=if(value in access) access-value else access+value},label={Text(value.name.lowercase())})}}
    }},confirmButton={TextButton(enabled=access.isNotEmpty(),onClick={action{host.directory.approve(device.id,access);approval=null}}){Text("Approve selected access")}},dismissButton={TextButton(onClick={approval=null}){Text("Later")}})}
    revoke?.let{device->AlertDialog(onDismissRequest={revoke=null},title={Text("Revoke ${device.name}?")},text={Text("Stops future authenticated requests and cancels its queued/running operations. Completed actions are not rolled back.")},
        confirmButton={TextButton(onClick={action{host.revoke(device.id);groupMembers=groupMembers-device.id;revoke=null}}){Text("Revoke")}},dismissButton={TextButton(onClick={revoke=null}){Text("Keep")}})}
}
