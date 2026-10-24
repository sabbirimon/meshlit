package com.meshlit.ui.modern
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meshlit.di.koinInject
import com.meshlit.ssh.SshConnections
import com.meshlit.core.ssh.SshConnection
import com.meshlit.devices.QrCodec
import kotlinx.coroutines.*
import java.util.UUID
@Composable fun SshConnectionsScreen(onBack:()->Unit) {
    val repository=koinInject<SshConnections>();val connections by repository.connections.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var editing by remember{mutableStateOf<SshConnection?>(null)};var password by remember{mutableStateOf("")};var key by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<String?>(null)};var command by remember{mutableStateOf("")};var output by remember{mutableStateOf("")};var job by remember{mutableStateOf<Job?>(null)};var error by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { DeviceSshIdentityCard(repository) }
        item{NodeSshCard()}
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("SSH connections",style=MaterialTheme.typography.headlineSmall);Text("Connect to hosts where you have permission. Verify the server fingerprint through an independent channel before saving. The remote device must already run an SSH server; this screen does not enroll a layer worker or install software.")}
        items(connections,key={it.id}){connection->Card{Column(Modifier.padding(16.dp)){Text("${connection.name} · ${connection.username}@${connection.host}:${connection.port}")
            Row{TextButton(onClick={selected=connection.id}){Text(if(selected==connection.id) "Selected" else "Select")};TextButton(onClick={editing=connection;password="";key=""}){Text("Edit")};TextButton(onClick={repository.remove(connection.id)}){Text("Remove")}}}}}
        item{Button(onClick={editing=SshConnection(UUID.randomUUID().toString(),"","",username="",hostKeySha256="");password="";key=""}){Text("Add SSH host")}}
        editing?.let{profile->item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(profile.name,{editing=profile.copy(name=it)},label={Text("Name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(profile.host,{editing=profile.copy(host=it)},label={Text("Host / IP")},modifier=Modifier.fillMaxWidth());OutlinedTextField(profile.port.toString(),{value->value.toIntOrNull()?.let{editing=profile.copy(port=it)}},label={Text("Port")},modifier=Modifier.fillMaxWidth());OutlinedTextField(profile.username,{editing=profile.copy(username=it)},label={Text("Username")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(profile.hostKeySha256,{editing=profile.copy(hostKeySha256=it)},label={Text("Host key pin · SHA256")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(profile.credentialEnvironmentId.orEmpty(),{editing=profile.copy(credentialEnvironmentId=it.takeIf{value->value.isNotBlank()})},label={Text("Cloud vault ID (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("If no password or private key is saved, this device uses My SSH key shown above. A vault reference overrides local keys and uses SSH_PASSWORD or SSH_PRIVATE_KEY. Agent use also needs environment delegation.")
            OutlinedTextField(password,{password=it},label={Text("Password (optional)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(key,{key=it},label={Text("Private key PEM (optional)")},visualTransformation=PasswordVisualTransformation(),maxLines=3,modifier=Modifier.fillMaxWidth())
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
        item{OutlinedTextField(command,{command=it},label={Text("Remote command")},maxLines=4,modifier=Modifier.fillMaxWidth())
            Row{Button(enabled=selected!=null && job==null && command.isNotBlank(),onClick={job=scope.launch{try{val result=repository.execute(selected!!,command);output="Exit ${result.exitCode} · truncated ${result.truncated}\n${result.stdout}\n${result.stderr}"}catch(e:CancellationException){output="SSH cancelled; remote actions may already have completed";throw e}catch(e:Exception){error="SSH failed: ${e.javaClass.simpleName}. Check credentials, host key, server and network."}finally{job=null}}}){Text("Run on selected host")};TextButton(enabled=job!=null,onClick={job?.cancel()}){Text("Cancel")}}
            Text("60-second limit; stdout and stderr each capped at 64 KiB. Cancellation disconnects the session; it cannot undo remote actions.")
            SelectionContainer{Text(output)};error?.let{ErrorCard(it){error=null}}}
    }
}

@Composable private fun DeviceSshIdentityCard(repository:SshConnections) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var publicKey by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(true) }
    var showPublicText by remember { mutableStateOf(false) }
    var privateText by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_STOP) privateText=null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer);privateText=null }
    }
    val unlock=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) {
            runCatching { repository.exportDevicePrivateKey() }
                .onSuccess { privateText=it }
                .onFailure { error=it.message }
        }
    }
    fun revealPrivateKey() {
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(!keyguard.isDeviceSecure) {
            error="Set a screen lock in Android Settings before revealing the private key."
            return
        }
        val intent=keyguard.createConfirmDeviceCredentialIntent("Reveal Meshlit SSH private key", "Only share this key with a device you trust.")
        if(intent==null) error="Android could not open device authentication."
        else unlock.launch(intent)
    }
    val saveQr=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if(uri!=null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                check(qr?.compress(Bitmap.CompressFormat.PNG,100,stream)==true)
            } ?: kotlin.error("Cannot save QR image")
        }.onFailure { error=it.message }
    }
    LaunchedEffect(repository) {
        runCatching {
            val key=withContext(Dispatchers.IO) { repository.devicePublicKey() }
            val image=withContext(Dispatchers.Default) { QrCodec.encode(key,768) }
            publicKey=key
            qr=image
        }.onFailure { failure ->
            android.util.Log.e("MeshlitSshIdentity","Could not create this device's SSH identity",failure)
            error="Could not create SSH key: ${failure.javaClass.simpleName}${failure.message?.let { ": $it" }.orEmpty()}"
        }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("My SSH key",style=MaterialTheme.typography.titleLarge)
        Text("Show this public-key QR on this phone. On the other phone, open SSH nodes and connections → Scan SSH public key with camera. Both key text fields are hidden until you reveal them.")
        if(publicKey==null && error==null) CircularProgressIndicator()
        if(publicKey!=null) {
            TextButton(onClick={visible=!visible}) { Text(if(visible) "Hide my QR" else "Show my QR") }
            if(visible) qr?.let { image -> Image(image.asImageBitmap(),"This device's SSH public key QR",Modifier.fillMaxWidth().height(260.dp)) }
            TextButton(onClick={showPublicText=!showPublicText}) { Text(if(showPublicText) "Hide public key text" else "Show public key text") }
            if(showPublicText) SelectionContainer { OutlinedTextField(publicKey!!,{},readOnly=true,label={Text("Public key · safe to share")},maxLines=5,modifier=Modifier.fillMaxWidth()) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={clipboard.setText(AnnotatedString(publicKey!!))}) { Text("Copy public key") }
                TextButton(enabled=qr!=null,onClick={saveQr.launch("Meshlit-SSH-public-key.png")}) { Text("Save QR") }
            }
            TextButton(onClick={if(privateText==null) ::revealPrivateKey else { -> privateText=null }}) { Text(if(privateText==null) "Unlock private key" else "Hide private key") }
            if(privateText!=null) {
                Text("Keep this private key secret. Copying it places it on the Android clipboard until cleared.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                SelectionContainer { OutlinedTextField(privateText!!,{},readOnly=true,label={Text("Private key · secret")},maxLines=5,modifier=Modifier.fillMaxWidth()) }
                TextButton(onClick={clipboard.setText(AnnotatedString(privateText!!))}) { Text("Copy private key") }
            }
            Text("The other device still needs a separate host fingerprint check and an explicit scope approval before SSH works.",style=MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    } }
}
