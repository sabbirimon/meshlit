package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.integrations.*
import com.meshlit.workspace.context.ContextEvidence
import kotlinx.coroutines.*

@Composable internal fun ConnectedServicesScreen(onBack:()->Unit,onOpen:(String)->Unit) {
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick=onBack){Text("Back")};Text("Connected capabilities",style=MaterialTheme.typography.headlineSmall)
            Text("One Meshlit workflow for role templates, teams, memory, phone control and model providers. Each backend keeps its own grants, source attribution and availability.")
            TextButton(onClick={onOpen("agent-catalog")}){Text("Agency Agents role library")}
            TextButton(onClick={onOpen("agent-teams")}){Text("Agent teams and supervision")}
            TextButton(onClick={onOpen("rag")}){Text("Built-in knowledge and recall")}
            TextButton(onClick={onOpen("openclaw")}){Text("On-phone agent and Accessibility grants")}
            TextButton(onClick={onOpen("bot-connections")}){Text("Bots and connections")}
            TextButton(onClick={onOpen("providers")}){Text("Model providers")}
        }
        item{ConnectedServiceSettings("openviking")}
        item{ConnectedServiceSettings("artemis")}
    }
}

@Composable private fun ConnectedServiceSettings(kind:String) {
    val context=LocalContext.current;val services=remember{ConnectedServices.get(context)};val scope=rememberCoroutineScope()
    var saved by remember{mutableStateOf(services.connection(kind))}
    var editing by remember{mutableStateOf(false)}
    var address by remember{mutableStateOf(saved?.address.orEmpty())};var secret by remember{mutableStateOf("")}
    var namespace by remember{mutableStateOf(saved?.namespace ?: "viking://resources/meshlit")}
    var enabled by remember{mutableStateOf(saved?.enabled ?: false)}
    var local by remember{mutableStateOf(saved?.localRecall ?: false)};var remote by remember{mutableStateOf(saved?.remoteRecall ?: false)}
    var agents by remember{mutableStateOf(saved?.agents ?: false)};var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")};var query by remember{mutableStateOf("")};var hits by remember{mutableStateOf<List<ContextEvidence>>(emptyList())}
    var content by remember{mutableStateOf("")};var serial by remember{mutableStateOf("")};var appPackage by remember{mutableStateOf(context.packageName)}
    var goal by remember{mutableStateOf("")};var review by remember{mutableStateOf(false)}
    var jobs by remember{mutableStateOf<List<ArtemisJob>>(emptyList())}
    fun action(work:suspend()->Unit){scope.launch{busy=true;try{work()}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message ?: "Service request failed"}finally{if(kind=="artemis")runCatching{jobs=services.artemisJobs()};busy=false}}}
    LaunchedEffect(kind){if(kind=="artemis")jobs=services.artemisJobs()}
    if(review)AlertDialog(onDismissRequest={review=false},title={Text("Review phone automation")},text={Column{
        Text("Artemis host: ${saved?.address}\nDevice: $serial\nApp constraint: $appPackage\nTask: $goal")
        Text("This sends the task to your configured host. Its model may receive screenshots and app content. Its app lock is an upstream constraint, not proof of Meshlit's per-action grants. Inspect the device and Stop from either dashboard when needed.")
    }},confirmButton={TextButton(onClick={review=false;action{
        val result=services.artemisSubmit(java.util.UUID.randomUUID().toString(),goal,serial,appPackage,"flash");status="Task ${result.id} submitted. Server status is separate from verified task success."
    }}){Text("Run reviewed task")}},dismissButton={TextButton(onClick={review=false}){Text("Cancel")}})
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(kind=="openviking")"OpenViking · memory and knowledge" else "Google Artemis · phone automation",style=MaterialTheme.typography.titleLarge)
        Text(if(kind=="openviking")"Search shared knowledge and long-term memory." else "Run reviewed phone tasks through your device host.")
        Text(if(saved==null)"Not configured" else if(saved?.enabled==true)"Connection enabled · ${saved?.address}" else "Connection off",style=MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick={editing=!editing}){Text(if(editing)"Close setup" else if(saved==null)"Set up connection" else "Connection and permissions")}
        if(editing){
        Text(if(kind=="openviking")"Optional AGPL server over its HTTP API. Search within your namespace, inspect L0 summaries, L1 overviews and L2 content. Existing local knowledge remains available offline. Imported skill text is reference data; it does not run."
            else "Optional Apache-2.0 device host. Artemis requires a configured model and an authorized ADB-connected device at that host. Meshlit's on-phone Accessibility adapter remains available separately. No host installation or ADB pairing runs automatically.")
        OutlinedTextField(address,{address=it.take(2048)},label={Text("HTTPS endpoint, or literal loopback HTTP")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(secret,{secret=it.take(8192)},label={Text(if(kind=="openviking")"User API key · blank keeps same-endpoint key" else "Host bearer token · blank keeps same-endpoint token")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Row{Switch(enabled,{enabled=it});Text("Enable connection")}
        if(kind=="openviking"){
            OutlinedTextField(namespace,{namespace=it.take(1024)},label={Text("Canonical viking:// namespace")},modifier=Modifier.fillMaxWidth())
            Row{Switch(local,{local=it});Text("Recall into local model chats")}
            Row{Switch(remote,{remote=it});Text("Recall into remote model chats")}
            Row{Switch(agents,{agents=it});Text("Allow agent recall")}
            Text("Recall sends the query to this memory server and may use its configured embedding provider. Selected excerpts may then go to the chosen model. No chat capture, session commit or cloud synchronization is enabled.")
        }
        Button(enabled=!busy,onClick={action{
            val next=ServiceConnection(kind,address,namespace,enabled,local,remote,agents)
            withContext(Dispatchers.IO){services.save(next,secret.takeIf{it.isNotBlank()})};saved=next;secret="";status="Configuration saved. Reachability has not been verified."
        }}){Text("Save configuration")}
        if(saved!=null)TextButton(enabled=!busy,onClick={action{withContext(Dispatchers.IO){services.remove(kind)};saved=null;status="Connection removed. Existing external tasks may still need Stop."}}){Text("Remove connection")}
        }
        if(kind=="openviking" && saved?.enabled==true){
            OutlinedTextField(query,{query=it.take(4000)},label={Text("Search memory and knowledge")},modifier=Modifier.fillMaxWidth())
            Button(enabled=!busy && saved?.enabled==true && query.isNotBlank(),onClick={action{hits=services.find(query,manual=true);status="${hits.size} source-attributed matches"}}){Text("Find in configured namespace")}
            hits.forEach{hit->Text("${hit.kind} · ${hit.uri}");Text(hit.text)
                Row{(0..2).forEach{layer->TextButton(enabled=!busy,onClick={action{content=services.read(hit.uri,layer);status="Read L$layer from ${hit.uri}"}}){Text("Read L$layer")}}}
            }
            if(content.isNotBlank())Text(content)
        }else if(kind=="artemis") {
            if(saved?.enabled==true){
            Row{
                TextButton(enabled=!busy && saved?.enabled==true,onClick={action{status=services.artemisStatus().toString().take(12000)}}){Text("Check host")}
                TextButton(enabled=!busy && saved?.enabled==true,onClick={action{status=services.artemisDevices().toString().take(12000)}}){Text("List actual devices")}
            }
            OutlinedTextField(serial,{serial=it.take(128)},label={Text("Exact device serial from host")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(appPackage,{appPackage=it.take(160)},label={Text("App package constraint")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(goal,{goal=it.take(4000)},label={Text("Phone task · review before execution")},modifier=Modifier.fillMaxWidth())
            Button(enabled=!busy && saved?.enabled==true && serial.isNotBlank() && goal.isNotBlank(),onClick={review=true}){Text("Review task")}
            Text("Artemis starts in human-reviewed mode. Direct agent delegation is unavailable until its host-side action scope is qualified. Task admissions are encrypted; restart never automatically repeats a task.")
            }
            jobs.asReversed().forEach{job->
                HorizontalDivider();Text("${job.phase} · ${job.id}");Text("${job.serial} · ${job.appPackage}\n${job.goal}")
                Text(job.observedStatus)
                Row{
                    TextButton(enabled=!busy,onClick={action{services.artemisTask(job.id);status="Observed host task status. This does not independently verify the task result."}}){Text("Refresh task")}
                    TextButton(enabled=!busy,onClick={action{status=if(services.artemisStop(job.id))"Host acknowledged Stop" else "Remote stop is unconfirmed"}}){Text("Stop task")}
                }
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank())Text(status)
    }}
}
