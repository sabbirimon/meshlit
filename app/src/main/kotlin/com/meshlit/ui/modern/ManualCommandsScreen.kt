package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.control.*
import com.meshlit.core.sandbox.tokenizeCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun ManualCommandsScreen(back:()->Unit) {
    val runtime=koinInject<com.meshlit.sandbox.RuntimeHost>();val ssh=koinInject<com.meshlit.ssh.SshConnections>();val backend=koinInject<AgentBackend>()
    val hosts by ssh.connections.collectAsState();val scope=rememberCoroutineScope()
    var mode by remember {mutableStateOf("Runtime")};var hostId by remember {mutableStateOf("")};var modelId by remember {mutableStateOf("")}
    var text by remember {mutableStateOf("")};var rootConsent by remember {mutableStateOf(false)};var result by remember {mutableStateOf("")}
    var active by remember {mutableStateOf<Job?>(null)};var submitted by remember {mutableStateOf<String?>(null)}
    DisposableEffect(Unit) {onDispose {active?.cancel()}}
    Scaffold(topBar={TopAppBar(title={Text("Commands and instructions")},navigationIcon={TextButton(onClick=back){Text("Back")}})}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {FlowRow {listOf("Runtime","SSH","Instruction").forEach {value->FilterChip(mode==value,{if(active==null){mode=value;rootConsent=false}},label={Text(value)})}}}
            item {Text(when(mode) {"Runtime"->"Current backend: ${runtime.config().mode}. Batch argv with quotes; pipes/expansion require explicitly invoking a shell. APP commands can access app data. Configure Linux/VM under Runtime.";"SSH"->"Runs a manual command on the selected pinned SSH host. This may modify that host.";else->"Submits a model-generation task. Text is an instruction to the selected model; no shell command or tool is executed automatically."})}
            if(mode=="SSH") item {FlowRow {hosts.forEach {host->FilterChip(hostId==host.id,{hostId=host.id},label={Text(host.name)})}}}
            if(mode=="Instruction") item {OutlinedTextField(modelId,{modelId=it},label={Text("Installed model ID or cloud:profile-id")},modifier=Modifier.fillMaxWidth())}
            item {OutlinedTextField(text,{text=it},label={Text(if(mode=="Instruction") "Instruction" else "Command")},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=active==null)}
            if(mode=="Runtime") item {Row {Text("Approve root for this command",Modifier.weight(1f));Switch(rootConsent,{rootConsent=it},enabled=active==null)}}
            item {Row {
                Button(enabled=active==null && text.isNotBlank(),onClick={val input=text;val method=mode;val selected=hostId;val model=modelId;val consent=rootConsent;rootConsent=false
                    active=scope.launch {result="Running…";try {
                        result=when(method) {
                            "Runtime"->{val value=runtime.execute(tokenizeCommand(input),consent);"exit=${value.exitCode ?: "timeout"}; truncated=${value.truncated}\n${value.stdout}\nstderr:\n${value.stderr}"}
                            "SSH"->{val value=ssh.execute(selected,input);"exit=${value.exitCode}; truncated=${value.truncated}\n${value.stdout}\nstderr:\n${value.stderr}"}
                            else->{val id=UUID.randomUUID().toString();val command=AgentCommand(id,AgentOperation.MODEL_GENERATE,modelId=model,prompt=input);backend.humanController.submit(command);submitted=id
                                val job=backend.humanController.jobs.first {jobs->jobs.any {it.command.requestId==id && it.terminal}}.first {it.command.requestId==id};submitted=null;"${job.phase}\n${job.result ?: job.errorCode}"}
                        }
                    } catch(e:CancellationException) {submitted?.let {id->withContext(NonCancellable){backend.humanController.cancel(id)}};submitted=null;result="Stop requested; completed side effects are not rolled back. Remote descendants may require host cleanup.";throw e}
                    catch(_:Exception) {result="Command failed; check configuration, permission, input and live host state"} finally {active=null}}
                }){Text("Run")}
                OutlinedButton(enabled=active!=null,onClick={active?.cancel()}){Text("Stop")}
            }}
            item {Text("Output stays in this screen and clears on exit. Model tasks use the existing encrypted human job journal. Commands may contain secrets; no command history is saved here.")}
            item {androidx.compose.foundation.text.selection.SelectionContainer {Text(result.take(140000))}}
        }
    }
}
