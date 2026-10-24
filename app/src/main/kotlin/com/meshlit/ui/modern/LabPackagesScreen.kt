package com.meshlit.ui.modern

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.core.sandbox.*
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun LabPackagesScreen(back:()->Unit) {
    val runtime=koinInject<com.meshlit.sandbox.RuntimeHost>();val packages=koinInject<com.meshlit.security.LabPackages>();val scope=rememberCoroutineScope();val context=LocalContext.current
    var manager by remember {mutableStateOf("pip")};var kind by remember {mutableStateOf("repo")};var source by remember {mutableStateOf("")};var hash by remember {mutableStateOf("")}
    var artifactName by remember {mutableStateOf("linux.qcow2")};var artifactHash by remember {mutableStateOf("")}
    var artifactBytes by remember {mutableStateOf("")};var artifactUrl by remember {mutableStateOf("")}
    var agentNames by remember {mutableStateOf(packages.delegation().names.joinToString("\n"))}
    var consent by remember {mutableStateOf(false)};var output by remember {mutableStateOf("")};var active by remember {mutableStateOf<Job?>(null)}
    fun requireVm() {com.meshlit.core.mcp.security.LabGate.requireReady(runtime.config().mode.name,runtime.vm.state.name)}
    fun run(action:String) {
        val root=consent;consent=false;val m=manager;val k=kind;val s=source;val h=hash
        active=scope.launch {try {requireVm();output=packages.run(action,m,k,s,h,guestRoot=root).toString()
        }catch(e:CancellationException){output="Stopped; inspect package-manager state inside guest before retry";throw e}catch(_:Exception){output="Package operation blocked or failed; require VM_SSH / SSH_READY and installed companion"}finally {active=null}}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri?->if(uri!=null && active==null) {
        active=scope.launch {try {requireVm();val suffix=when(manager){"pip"->".whl";"apt"->".deb";"apk"->".apk";"dnf"->".rpm";else->".pkg.tar.zst"};val path="/tmp/meshlit-import-${UUID.randomUUID()}$suffix"
            val digest=MessageDigest.getInstance("SHA-256")
            val init=runtime.executeGuest(listOf("sh","-c","umask 077; : > "+shellQuote(path)));require(init.exitCode==0)
            try {withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)!!.use {stream->
                val buffer=ByteArray(18000);var bytes=0L
                while(true){ensureActive();val count=stream.read(buffer);if(count<0) break;bytes+=count;require(bytes<=128L*1024*1024);digest.update(buffer,0,count)
                    val data=android.util.Base64.encodeToString(buffer.copyOf(count),android.util.Base64.NO_WRAP)
                    val result=runtime.executeGuest(listOf("sh","-c","printf %s "+shellQuote(data)+" | base64 -d >> "+shellQuote(path)));require(result.exitCode==0 && !result.timedOut)
                }
            }}}catch(e:Exception){withContext(NonCancellable){runCatching {runtime.executeGuest(listOf("rm","-f",path))}};throw e}
            source=path;kind="file";hash=digest.digest().joinToString(""){"%02x".format(it)};output="Transferred to VM and computed SHA-256. Review provenance before Install. Remove the staged file after use: $path"
        }catch(e:CancellationException){output="Transfer stopped";throw e}catch(_:Exception){output="Transfer failed; require ready VM and base64 utility"}finally {active=null}}
    }}
    fun requireStopped() {require(runtime.vm.state in listOf(VmState.STOPPED,VmState.FAILED)) {"Stop the VM first"}}
    val artifactPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri:Uri?->if(uri!=null && active==null) {
        val name=artifactName;val expected=artifactHash;val bytes=artifactBytes.toLongOrNull()
        active=scope.launch {try {
            requireStopped();require(bytes!=null)
            var displayed=0L
            val file=runtime.artifacts.installStream({context.contentResolver.openInputStream(uri) ?: error("Cannot read artifact")},name,expected,bytes,
                progress={count->if(count-displayed>=1024*1024 || count==bytes) {displayed=count;withContext(Dispatchers.Main){output="Copied $count / $bytes bytes; verification pending"}}})
            output="Verified artifact saved: ${file.absolutePath}. Configure the VM with this path; nothing was executed."
        }catch(e:CancellationException){output="Artifact import stopped; incomplete staging removed";throw e}
        catch(_:Exception){output="Artifact import failed: check size, SHA-256, unique name, free storage and stopped VM"}finally {active=null}}
    }}
    fun downloadArtifact() {
        val url=artifactUrl;val name=artifactName;val expected=artifactHash;val bytes=artifactBytes.toLongOrNull()
        active=scope.launch {try {
            requireStopped();require(bytes!=null)
            var displayed=0L
            val file=runtime.artifacts.download(url,name,expected,bytes,progress={count->if(count-displayed>=1024*1024 || count==bytes) {displayed=count;withContext(Dispatchers.Main){output="Downloaded $count / $bytes bytes; verification pending"}}})
            output="Verified artifact saved: ${file.absolutePath}. Configure the VM with this path; nothing was executed."
        }catch(e:CancellationException){output="Download stopped; incomplete staging removed";throw e}
        catch(_:Exception){output="Artifact download failed: require exact HTTPS without redirects, matching SHA-256/size, free storage and stopped VM"}finally {active=null}}
    }
    DisposableEffect(Unit){onDispose {active?.cancel()}}
    Scaffold(topBar={TopAppBar(title={Text("Lab packages")},navigationIcon={TextButton(onClick=back){Text("Back")}})}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Rooted and non-rooted phone support uses an isolated Linux VM. APP/PRoot/ordinary SSH do not unlock this lab. Current ${runtime.config().mode} / ${runtime.vm.state}. Install companions/cyber inside the guest first.")}
            item {Text("VM setup artifacts · import a trusted qcow2 disk, kernel or initrd while stopped. Exact size and publisher SHA-256 are required. Import does not install a distro or make a Linux binary Android-compatible.")}
            item {OutlinedTextField(artifactName,{artifactName=it},label={Text("Unique artifact name")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {OutlinedTextField(artifactHash,{artifactHash=it},label={Text("Publisher SHA-256")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {OutlinedTextField(artifactBytes,{artifactBytes=it},label={Text("Exact artifact size in bytes · up to 8 GiB")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {OutlinedTextField(artifactUrl,{artifactUrl=it},label={Text("Exact HTTPS artifact URL · no redirects")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {FlowRow {
                OutlinedButton(enabled=active==null,onClick={artifactPicker.launch(arrayOf("*/*"))}){Text("Import VM artifact from storage")}
                OutlinedButton(enabled=active==null,onClick={downloadArtifact()}){Text("Download verified VM artifact")}
            }}
            item {Text("Guest disk mode: ${if(runtime.vmConfig().persistDisk) "persistent · writes retained in supplied disk" else "ephemeral · changes discarded at stop"}. Change only while stopped; choose an owned disposable image.")}
            item {FlowRow {
                OutlinedButton(enabled=active==null,onClick={try {runtime.configureVm(runtime.vmConfig().copy(persistDisk=true));output="Persistent disk mode saved for next boot; guest writes modify the supplied qcow2"}catch(_:Exception){output="Stop VM and configure valid artifacts before changing disk mode"}}){Text("Keep guest changes")}
                OutlinedButton(enabled=active==null,onClick={try {runtime.configureVm(runtime.vmConfig().copy(persistDisk=false));output="Ephemeral disk mode saved for next boot"}catch(_:Exception){output="Stop VM before changing disk mode"}}){Text("Discard changes on stop")}
            }}
            item {Text("Guest outbound network: ${if(runtime.vmConfig().allowOutboundNetwork) "enabled at VM startup" else "restricted by default"}. Web/repository downloads need your explicit opt-in before starting the VM.")}
            item {FlowRow {
                OutlinedButton(enabled=active==null,onClick={try {runtime.configureVm(runtime.vmConfig().copy(allowOutboundNetwork=true));output="Guest internet enabled for next VM start; renew grants after restart"}catch(_:Exception){output="Stop the VM and configure trusted guest artifacts before changing networking"}}){Text("Enable guest internet")}
                OutlinedButton(enabled=active==null,onClick={try {runtime.configureVm(runtime.vmConfig().copy(allowOutboundNetwork=false));output="Guest internet restricted for next VM start"}catch(_:Exception){output="Stop the VM before changing networking"}}){Text("Restrict guest internet")}
            }}
            item {FlowRow {listOf("pip","apt","apk","dnf","pacman").forEach {value->FilterChip(manager==value,{if(active==null) manager=value},label={Text(value)})}}}
            item {Text("pip uses a non-root dedicated virtual environment. Distro managers require root inside the VM and per-operation approval. Package install can run package scripts. Only use trusted packages; install/uninstall are explicit actions.")}
            item {FlowRow {listOf("repo","file","web").forEach {value->FilterChip(kind==value,{if(active==null) kind=value},label={Text(value)})}}}
            item {OutlinedTextField(source,{source=it},label={Text("Package name / guest file path / exact HTTPS artifact URL")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {OutlinedTextField(hash,{hash=it},label={Text("Expected SHA-256 for file/web artifact")},modifier=Modifier.fillMaxWidth(),enabled=active==null)}
            item {OutlinedButton(enabled=active==null,onClick={picker.launch(arrayOf("*/*"))}){Text("Import package from phone storage")}}
            item {Row {Text("Approve guest-root operation",Modifier.weight(1f));Switch(consent,{consent=it},enabled=active==null)}}
            item {OutlinedButton(enabled=active==null,onClick={val approved=consent;consent=false;active=scope.launch {try {output=packages.provisionCompanions(approved)}catch(e:CancellationException){output="Setup stopped; inspect guest for partial updates";throw e}catch(_:Exception){output="Setup blocked or failed: require ready VM, explicit approval, guest root SSH, sha256sum/base64 and /usr/local/bin. Partial updates may remain."}finally {active=null}}}){Text("Install / update bundled lab companions")}}
            item {FlowRow {listOf("list","install","uninstall").forEach {action->Button(enabled=active==null,onClick={run(action)}){Text(action.replaceFirstChar {it.uppercase()})}};OutlinedButton(enabled=active!=null,onClick={active?.cancel()}){Text("Stop")}}}
            item {Text("For uninstall, enter the installed package name. Files can come from storage or another device's verified HTTPS endpoint. Repo installs use the guest's configured repositories. Ephemeral VM changes disappear on stop; persistent mode retains disk writes. Automatic agent package operations are limited to the saved one-hour pip name allowlist.")}
            item {OutlinedTextField(agentNames,{agentNames=it},label={Text("Agent pip package allowlist · one name per line")},modifier=Modifier.fillMaxWidth())}
            item {FlowRow {
                OutlinedButton(onClick={try {packages.delegate(agentNames.lines().filter {it.isNotBlank()});output="One-hour pip install/uninstall delegation saved; agent VM activation is also required"}catch(_:Exception){output="Invalid package allowlist"}}){Text("Delegate for 1 hour")}
                OutlinedButton(onClick={packages.delegate(emptyList());agentNames="";output="Package delegation revoked"}){Text("Revoke delegation")}
            }}
            item {androidx.compose.foundation.text.selection.SelectionContainer {Text(output.take(65536))}}
        }
    }
}
