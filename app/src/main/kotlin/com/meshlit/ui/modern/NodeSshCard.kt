package com.meshlit.ui.modern

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.ssh.*
import com.meshlit.di.koinInject
import com.meshlit.ssh.NodeSshHost
import kotlinx.coroutines.*
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import java.util.UUID

@Composable fun NodeSshCard() {
    if(Build.VERSION.SDK_INT<26) { Text("Inbound SSH requires Android 8/API 26 or newer. Outbound SSH is still available.");return }
    if(com.meshlit.BuildConfig.PLAY_REVIEW) { Text("Inbound SSH is available in the Experimental build; this distribution has no SSH listener.");return }
    NodeSshControls()
}
@RequiresApi(26)
@Composable private fun NodeSshControls() {
    val host=koinInject<NodeSshHost>();val state by host.state.collectAsStateWithLifecycle();val grants by host.grants.collectAsStateWithLifecycle()
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    var bind by remember { mutableStateOf(NodeSshBind()) }
    var draft by remember { mutableStateOf<NodeSshGrant?>(null) }
    var scanPublicKey by remember { mutableStateOf(false) }
    var rejectedScan by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val stopped=state.phase in setOf("Stopped","Failed")
    Card(Modifier.fillMaxWidth().testTag("node-ssh-controls")) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SSH into this app node",style=MaterialTheme.typography.titleLarge)
        Text("Experimental · ${state.phase}. Public-key JSON commands only. No login shell, file-transfer service, forwarding or root. The node runs with Meshlit's Android permissions.")
        OutlinedTextField(bind.address,{bind=bind.copy(address=it.take(32))},enabled=stopped,label={Text("Private IPv4 address")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(bind.port.toString(),{value -> value.toIntOrNull()?.let { bind=bind.copy(port=it) } },enabled=stopped,label={Text("Port · 1024–65535")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("Available addresses: ${host.addresses().joinToString()}. LAN works without Internet. Internet access requires your own authenticated private VPN/network; the app does not open router ports.")
        Text("Enrolled keys · ${grants.size}/32",style=MaterialTheme.typography.titleMedium)
        grants.forEach { grant ->
            Text("${grant.name} · ${grant.username} · ${if(grant.agent) "Agent" else "Human"} · ${grant.scopes.joinToString()}")
            Row { TextButton(enabled=stopped,onClick={draft=grant}) { Text("Edit scopes") }
                TextButton(enabled=stopped,onClick={runCatching { host.remove(grant.id) }.onFailure { message=it.message }}) { Text("Revoke key") } }
        }
        TextButton(enabled=stopped && grants.size<32,onClick={draft=NodeSshGrant(UUID.randomUUID().toString(),"","meshlit","",agent=false)}) { Text("Enroll a client public key") }
        OutlinedButton(enabled=stopped && grants.size<32,onClick={scanPublicKey=true},modifier=Modifier.fillMaxWidth()) { Text("Scan client key with camera") }
        if(scanPublicKey) {
            Dialog(onDismissRequest={scanPublicKey=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
                Surface(Modifier.fillMaxWidth().padding(16.dp),shape=MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Scan SSH public key",style=MaterialTheme.typography.titleLarge)
                        Text("Point the camera at the other device's My SSH key QR.")
                        ScannerWithPermissions(
                            modifier=Modifier.fillMaxWidth().height(340.dp),
                            onScanned={ raw ->
                                val value=raw.trim()
                                if(runCatching { NodeSshServer.publicKey(value) }.isSuccess) {
                                    draft=(draft ?: NodeSshGrant(UUID.randomUUID().toString(),"","meshlit","",agent=false)).copy(publicKey=value)
                                    rejectedScan=null
                                    message="Public key scanned. Add a client name, review scopes, then save."
                                } else {
                                    rejectedScan=value.take(4096)
                                    message="QR decoded, but it is not a supported SSH public key. Check the scanned contents below."
                                }
                                scanPublicKey=false
                                true
                            },
                            types=listOf(CodeType.QR),
                            cameraPosition=CameraPosition.BACK,
                        )
                        TextButton(onClick={scanPublicKey=false}) { Text("Close camera") }
                    }
                }
            }
        }
        draft?.let { grant -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(grant.name,{draft=grant.copy(name=it.take(100))},label={Text("Client name")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(grant.username,{draft=grant.copy(username=it.take(32))},label={Text("SSH username")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(grant.publicKey,{draft=grant.copy(publicKey=it.take(4096))},label={Text("Client public key")},maxLines=3,modifier=Modifier.fillMaxWidth())
            Text("Generate the key on your client. Paste its public .pub line; keep its private key on that client. Check the saved node fingerprint independently before connecting.")
            Row { Text("Agent identity",Modifier.weight(1f));Switch(grant.agent,{draft=grant.copy(agent=it,scopes=if(it) grant.scopes-NodeSshScope.APP_EXEC else grant.scopes)}) }
            NodeSshScope.entries.filter { !grant.agent || it!=NodeSshScope.APP_EXEC }.forEach { access -> Row {
                Checkbox(access in grant.scopes,{yes -> draft=grant.copy(scopes=if(yes) grant.scopes+access else grant.scopes-access)})
                Text(when(access) { NodeSshScope.STATUS->"Read node and VM status";NodeSshScope.VM_CONTROL->"Start/wait/stop the saved VM";NodeSshScope.VM_EXEC->"Execute in the configured VM guest";NodeSshScope.APP_EXEC->"Human app diagnostics · id, uname, uptime and df" })
            } }
            Text("Agent VM commands also need the saved VM opt-in and global agent VM/SSH gates. Human keys can carry powerful grants; do not give them to agents. App diagnostics use the Android UID and provide no additional isolation; shell programs require the guest.")
            Row { Button(enabled=stopped,onClick={runCatching { host.save(grant);draft=null;message="Client key saved; listener remains stopped" }.onFailure { message=it.message }}) { Text("Save key and scopes") }
                TextButton(onClick={draft=null}) { Text("Cancel") } }
        } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(enabled=stopped && grants.isNotEmpty(),onClick={scope.launch { try { host.start(bind);message=null } catch(e:CancellationException) { throw e } catch(e:Exception) { message=e.message ?: "SSH start failed" } }},modifier=Modifier.testTag("node-ssh-start")) { Text("Start SSH node") }
            OutlinedButton(enabled=!stopped,onClick={host.stop()},modifier=Modifier.testTag("node-ssh-stop")) { Text("Stop SSH") }
        }
        state.fingerprint?.let { pin -> SelectionContainer { Text("Verify host key: $pin\nssh -T -p ${state.port} USER@${state.address} 'status'") } }
        Text("Maximum 4 connections and 4 concurrent commands, 60 seconds per command, 30 minutes per listener session. Stop and key edits close local sessions; they do not undo completed actions. No listener restarts at boot.")
        (message ?: state.error)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        rejectedScan?.let { value ->
            Text("Scanned ${value.length} characters · ${value.take(32)}",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value,{},readOnly=true,label={Text("Scanned QR contents")},maxLines=3,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={clipboard.setText(AnnotatedString(value))}) { Text("Copy scanned contents") }
        }
    } }
}
