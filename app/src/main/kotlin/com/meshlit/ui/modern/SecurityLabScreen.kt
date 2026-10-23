package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.core.mcp.security.*
import com.meshlit.di.koinInject
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun SecurityLabScreen(back:()->Unit,onRuntime:()->Unit,onPackages:()->Unit) {
    val lab=koinInject<com.meshlit.security.SecurityLab>();val runtime=koinInject<com.meshlit.sandbox.RuntimeHost>()
    val assessments by lab.assessments.collectAsState();val scope=rememberCoroutineScope()
    var owner by remember {mutableStateOf("")};var target by remember {mutableStateOf("")};var hash by remember {mutableStateOf("")};var hostId by remember {mutableStateOf("vm-ssh")}
    var agentAllowed by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf("apk_inventory")};var output by remember {mutableStateOf("")};var active by remember {mutableStateOf<Job?>(null)}
    DisposableEffect(Unit) {onDispose {active?.cancel()}}
    Scaffold(topBar={TopAppBar(title={Text("Security Lab")},navigationIcon={TextButton(onClick=back){Text("Back")}})}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Red team, blue team and forensic tool inventory. Only three read-only companion adapters are implemented here. Install companions/cyber inside your VM. Lab execution requires VM_SSH and SSH_READY; other runtimes are blocked. Root, exploitation and dynamic instrumentation adapters remain unavailable.")}
            item {Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onRuntime,modifier=Modifier.fillMaxWidth()) {Text("Configure isolated Linux VM")}
                OutlinedButton(onClick=onPackages,modifier=Modifier.fillMaxWidth()) {Text("VM artifacts and lab packages")}
            }}
            CyberTools.all.forEach {tool->item {Text("${tool.name}: ${tool.role} · ${if(tool.implemented) "adapter implemented; host required" else "planned adapter"}")}}
            item {Text("Create a 24-hour assessment grant for an owned artifact")}
            item {OutlinedTextField(owner,{owner=it},label={Text("Owner / authority")},modifier=Modifier.fillMaxWidth())}
            item {OutlinedTextField(target,{target=it},label={Text("Exact absolute input path inside VM")},modifier=Modifier.fillMaxWidth())}
            item {OutlinedTextField(hash,{hash=it},label={Text("Independently verified SHA-256")},modifier=Modifier.fillMaxWidth())}
            item {Text("Required environment: VM_SSH / SSH_READY. Current: ${runtime.config().mode} / ${runtime.vm.state}")}
            item {FlowRow {CyberTools.all.filter {it.implemented}.forEach {tool->FilterChip(selected==tool.id,{selected=tool.id},label={Text(tool.name)})}}}
            item {Row {Text("Allow agents for this assessment",Modifier.weight(1f));Switch(agentAllowed,{agentAllowed=it})}}
            item {Button(onClick={try {lab.save(SecurityAssessment(UUID.randomUUID().toString(),owner,target,hash,hostId,System.currentTimeMillis()+86400000,agentAllowed=agentAllowed,tools=setOf(selected),environmentId=runtime.labIdentity()));output="Grant saved; no tool executed"}catch(_:Exception){output="Invalid owner, host, path or digest"}}){Text("Save assessment")}}
            assessments.forEach {assessment->item {Card {Column(Modifier.padding(12.dp)) {
                Text("${assessment.owner} · ${assessment.target}\nExpires ${java.util.Date(assessment.expiresAtMs)}")
                FlowRow {assessment.tools.forEach {tool->Button(enabled=active==null,onClick={active=scope.launch {try {output=lab.run(assessment.id,tool).toString()}catch(e:CancellationException){output="Stopped; inspect host cleanup";throw e}catch(_:Exception){output="Analysis failed: check grant expiry, host key, companion installation and digest"}finally {active=null}}}){Text("Run $tool")}}}
                OutlinedButton(onClick={active?.cancel();lab.remove(assessment.id)}){Text("Revoke assessment")}
                TextButton(onClick={output=lab.report(assessment.id).orEmpty()}){Text("Read retained report")}
            }}}}
            item {OutlinedButton(enabled=active!=null,onClick={active?.cancel()}){Text("Stop")}}
            item {androidx.compose.foundation.text.selection.SelectionContainer {Text(output.take(65536))}}
        }
    }
}
