package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.ssh.SshConnections
import com.meshlit.core.ssh.SshConnection
import kotlinx.coroutines.*
import java.util.UUID
@Composable fun SshConnectionsScreen(onBack:()->Unit) {
    val repository=koinInject<SshConnections>();val connections by repository.connections.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var editing by remember{mutableStateOf<SshConnection?>(null)};var password by remember{mutableStateOf("")};var key by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<String?>(null)};var command by remember{mutableStateOf("")};var output by remember{mutableStateOf("")};var job by remember{mutableStateOf<Job?>(null)};var error by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{NodeSshCard()}
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("SSH connections",style=MaterialTheme.typography.headlineSmall);Text("Connect to hosts where you have permission. Verify the server fingerprint through an independent channel before saving. The remote device must already run an SSH server; this screen does not enroll a layer worker or install software.")}
        items(connections,key={it.id}){connection->Card{Column(Modifier.padding(16.dp)){Text("${connection.name} · ${connection.username}@${connection.host}:${connection.port}")
            Row{TextButton(onClick={selected=connection.id}){Text(if(selected==connection.id) "Selected" else "Select")};TextButton(onClick={editing=connection;password="";key=""}){Text("Edit")};TextButton(onClick={repository.remove(connection.id)}){Text("Remove")}}}}}
        item{Button(onClick={editing=SshConnection(UUID.randomUUID().toString(),"","",username="",hostKeySha256="");password="";key=""}){Text("Add SSH host")}}
        editing?.let{profile->item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(profile.name,{editing=profile.copy(name=it)},label={Text("Name")});OutlinedTextField(profile.host,{editing=profile.copy(host=it)},label={Text("Host / IP")});OutlinedTextField(profile.port.toString(),{value->value.toIntOrNull()?.let{editing=profile.copy(port=it)}},label={Text("Port")});OutlinedTextField(profile.username,{editing=profile.copy(username=it)},label={Text("Username")})
            OutlinedTextField(profile.hostKeySha256,{editing=profile.copy(hostKeySha256=it)},label={Text("Verified SHA256: host-key fingerprint")})
            OutlinedTextField(profile.credentialEnvironmentId.orEmpty(),{editing=profile.copy(credentialEnvironmentId=it.takeIf{value->value.isNotBlank()})},label={Text("Optional Cloud vault environment ID (bound to this host)")},singleLine=true)
            Text("A vault reference overrides the credentials below and uses SSH_PASSWORD or SSH_PRIVATE_KEY. Agent use also needs environment delegation.")
            OutlinedTextField(password,{password=it},label={Text("Password (blank keeps saved credential)")},visualTransformation=PasswordVisualTransformation())
            OutlinedTextField(key,{key=it},label={Text("Or unencrypted private key PEM/OpenSSH")},visualTransformation=PasswordVisualTransformation(),maxLines=3)
            Row{Text("Allow agent commands to this host",Modifier.weight(1f));Switch(profile.agentAllowed,{editing=profile.copy(agentAllowed=it)})}
            if(profile.agentAllowed) {
                Text("Agent access is limited to the selected Meshlit JSON node actions. Generic SSH shell commands remain human-only. The remote node must enroll this client's key with matching agent scopes.")
                com.meshlit.core.ssh.NodeSshAction.entries.filter{it!=com.meshlit.core.ssh.NodeSshAction.APP_EXEC}.forEach{action -> Row {
                    Checkbox(action in profile.agentNodeActions,{yes -> editing=profile.copy(agentNodeActions=if(yes) profile.agentNodeActions+action else profile.agentNodeActions-action)})
                    Text(action.name)
                }}
            }
            Button(onClick={try{require(password.isBlank() || key.isBlank()){ "Choose password or private key" };repository.save(profile,password.takeIf{it.isNotBlank()},key.takeIf{it.isNotBlank()});editing=null;password="";key=""}catch(e:Exception){error=e.message}}){Text("Save encrypted profile")}
        }}}
        item{OutlinedTextField(command,{command=it},label={Text("Remote command")},maxLines=4)
            Row{Button(enabled=selected!=null && job==null && command.isNotBlank(),onClick={job=scope.launch{try{val result=repository.execute(selected!!,command);output="Exit ${result.exitCode} · truncated ${result.truncated}\n${result.stdout}\n${result.stderr}"}catch(e:CancellationException){output="SSH cancelled; remote actions may already have completed";throw e}catch(e:Exception){error="SSH failed: ${e.javaClass.simpleName}. Check credentials, host key, server and network."}finally{job=null}}}){Text("Run on selected host")};TextButton(enabled=job!=null,onClick={job?.cancel()}){Text("Cancel")}}
            Text("60-second limit; stdout and stderr each capped at 64 KiB. Cancellation disconnects the session; it cannot undo remote actions.")
            SelectionContainer{Text(output)};error?.let{ErrorCard(it){error=null}}}
    }
}
