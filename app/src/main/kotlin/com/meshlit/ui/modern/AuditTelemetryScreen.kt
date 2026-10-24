package com.meshlit.ui.modern

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.observability.*
import com.meshlit.observability.AuditTelemetry
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.TracingMode as SavedTracingMode
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun AuditTelemetryScreen(onBack:()->Unit) {
    val audit=koinInject<AuditTelemetry>();val settings=koinInject<SettingsRepository>();val controller=koinInject<TracingController>()
    val policy by audit.policy.collectAsStateWithLifecycle();val records by audit.journal.records.collectAsStateWithLifecycle()
    val storage by audit.journal.status.collectAsStateWithLifecycle();val status by audit.state.collectAsStateWithLifecycle()
    val collectorStatus by controller.status.collectAsStateWithLifecycle();val dropped by audit.dropped.collectAsStateWithLifecycle()
    val sample by audit.sample.collectAsStateWithLifecycle();val mode by settings.tracingModeFlow.collectAsStateWithLifecycle(SavedTracingMode.Off)
    val savedEndpoint by settings.tracingOtelEndpointFlow.collectAsStateWithLifecycle("")
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var result by remember{mutableStateOf<String?>(null)}
    val auditReader=koinInject<com.meshlit.resources.ResourceSnapshotReader>()
    val auditRadar=koinInject<com.meshlit.disco.PeerRepository>()
    val auditPipeline=koinInject<com.meshlit.pipeline.PipelineHost>()
    val auditDiscovery=koinInject<com.meshlit.disco.DiscoveryModeStore>()
    var deviceReport by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var reportToExport by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    val reportPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val report=reportToExport;reportToExport=null
        if(uri!=null && report!=null) scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(report.toString()) } ?: error("Destination unavailable") }
                .onFailure { withContext(Dispatchers.Main) { result="Device report export failed" } }
        }
    }
    var query by rememberSaveable{mutableStateOf("")};var source by remember{mutableStateOf<AuditSource?>(null)}
    var actor by remember{mutableStateOf<AuditActor?>(null)};var outcome by remember{mutableStateOf<AuditOutcome?>(null)}
    var range by rememberSaveable{mutableIntStateOf(0)};var endpoint by rememberSaveable{mutableStateOf("")}
    var header by remember{mutableStateOf("")}
    var editing by rememberSaveable{mutableStateOf(false)};var clear by remember{mutableStateOf(false)};var filters by rememberSaveable{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var export by remember{mutableStateOf<Pair<Boolean,List<AuditRecord>>?>(null)}
    LaunchedEffect(savedEndpoint){if(!editing)endpoint=savedEndpoint}
    fun work(block:suspend()->Unit){scope.launch{busy=true;try{block()}catch(e:CancellationException){throw e}catch(_:Exception){result="Operation failed. Check audit storage and collector configuration."}finally{busy=false}}}
    val filtered=remember(records,query,source,actor,outcome,range){records.filter(AuditFilter(query,source,actor,outcome,
        if(range==0)0 else System.currentTimeMillis()-if(range==1)3_600_000 else 86_400_000)::matches)}
    fun save(uri:android.net.Uri?){val snapshot=export;export=null;if(uri==null || snapshot==null)return
        work { withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { w->
            if(snapshot.first){w.write(AuditExport.csvHeader());w.newLine()}
            snapshot.second.forEach{w.write(if(snapshot.first)AuditExport.csv(it) else it.jsonLine());w.newLine()}
        } ?: error("Selected destination is unwritable")};result="Exported ${snapshot.second.size} filtered records. Exported files are unencrypted; protect the destination."
            audit.record(AuditRecord(source=AuditSource.SETTINGS,action="audit.export",actor=AuditActor.HUMAN,outcome=AuditOutcome.SUCCEEDED)) }
    }
    val json=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson"),::save)
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"),::save)
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("Delete retained audit records?")},text={Text("Local records will be removed. Existing exports and collector data remain under their own retention policies.")},confirmButton={TextButton(onClick={clear=false;work{audit.ready.await();audit.journal.clear();result="Local audit records deleted"}}){Text("Delete")}},dismissButton={TextButton(onClick={clear=false}){Text("Cancel")}})
    Scaffold(topBar={TopAppBar(title={Text("Audit and telemetry")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)) {
            item { Card { Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)) {
                Text("Device audit report",style=MaterialTheme.typography.titleMedium)
                Text("One read-only snapshot of Android-exposed hardware, Meshlit resources, permissions and mesh state. Other apps, secrets, payments and passkeys are outside this report.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                    Button(onClick={deviceReport=com.meshlit.audit.DeviceAuditReport(context,auditReader,auditRadar,auditPipeline,auditDiscovery).collect()}) { Text("Run device audit") }
                    OutlinedButton(enabled=deviceReport!=null,onClick={reportToExport=deviceReport;reportPicker.launch("meshlit-device-audit-${System.currentTimeMillis()}.json")}) { Text("Export JSON") }
                }
                deviceReport?.let { report -> SelectionContainer { Text(report.toString(),style=MaterialTheme.typography.bodySmall) } }
            } } }
            item {Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Local audit collection",style=MaterialTheme.typography.titleMedium);Text("Encrypted metadata history",style=MaterialTheme.typography.bodySmall)}
                    Switch(policy.enabled,{v->work{audit.configure(policy.copy(enabled=v))}},enabled=!busy)}
                Text("$status · $storage",style=MaterialTheme.typography.bodySmall)
                Text("${records.size} retained · $dropped dropped/failed this process",style=MaterialTheme.typography.bodySmall)
                Text("Prompts, replies, credentials, URLs, paths and command arguments are excluded. Object IDs are hashed. Collection defaults off; pending batches can be lost during a crash.",style=MaterialTheme.typography.bodySmall)
                Text("Retention",style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){listOf(1,7,30,90).forEach{d->FilterChip(policy.days==d,{work{audit.configure(policy.copy(days=d))}},enabled=!busy,label={Text("$d days")})}}
                Text("Record limit",style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){listOf(500,2000,5000).forEach{n->FilterChip(policy.limit==n,{work{audit.configure(policy.copy(limit=n))}},enabled=!busy,label={Text("$n")})}}
                Text("Device sample interval",style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){listOf(15,30,60,300).forEach{n->FilterChip(policy.intervalSeconds==n,{work{audit.configure(policy.copy(intervalSeconds=n))}},enabled=!busy,label={Text("${n}s")})}}
            }}}
            item {Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                Text("OpenTelemetry collector",style=MaterialTheme.typography.titleMedium)
                Text("$collectorStatus",style=MaterialTheme.typography.bodySmall)
                Text("OTLP/HTTP traces and metrics → your collector → Grafana/Tempo, Prometheus or compatible backends. Collector credentials are encrypted. No vendor is enabled automatically.",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){SavedTracingMode.entries.forEach{m->FilterChip(mode==m,{work{settings.setTracingMode(m)}},enabled=!busy,label={Text(when(m){SavedTracingMode.Off->"Off";SavedTracingMode.Local->"Local tracing";SavedTracingMode.Otel->"OTLP collector"})})}}
                OutlinedTextField(endpoint,{endpoint=it;editing=true},Modifier.fillMaxWidth(),label={Text("HTTPS OTLP base URL")},placeholder={Text("https://collector.example/otlp")},singleLine=true)
                Text("Adds /v1/traces and /v1/metrics. Loopback HTTP is allowed for an owner-controlled local collector. Audit collection and tracing modes have separate switches.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled=!busy,onClick={work{settings.setTracingOtelEndpoint(endpoint);editing=false;result="Collector endpoint saved. If it changed, save new authentication headers for this endpoint."}}){Text("Save endpoint")}
                OutlinedTextField(header,{header=it},Modifier.fillMaxWidth(),label={Text("Replace headers (key=value per line)")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    OutlinedButton(enabled=!busy && header.isNotBlank(),onClick={work{settings.setTracingOtelHeaders(header);header="";result="Encrypted collector headers replaced"}}){Text("Save headers")}
                    TextButton(enabled=!busy,onClick={work{settings.setTracingOtelHeaders("");result="Collector headers removed"}}){Text("Remove")}
                }
                OutlinedButton(enabled=!busy && mode!=SavedTracingMode.Off,onClick={work{
                    audit.record(AuditRecord(source=AuditSource.SETTINGS,action="audit.collector.check",actor=AuditActor.HUMAN,outcome=AuditOutcome.OBSERVED));delay(300)
                    result=if(controller.flush())"Export flush completed. A collector acknowledgment does not prove dashboard ingestion." else "Flush failed or timed out. Check collector reachability and authentication."
                }}){Text("Flush pending telemetry")}
            }}}
            item {Text("Latest device sample",style=MaterialTheme.typography.titleMedium)
                if(sample.isEmpty())Text("No device sample available. Enable collection to measure this device.",style=MaterialTheme.typography.bodySmall)
                else Card{Column(Modifier.padding(T.medium)){sample.forEach{(k,v)->Text("${k.replace('_',' ')}: ${"%.1f".format(v)}",style=MaterialTheme.typography.bodySmall)}}}}
            item {Text("Audit history",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search action or hashed ID")},singleLine=true)
                TextButton(onClick={filters=!filters}){Text(if(filters)"Hide filters" else "Source, actor, outcome and time filters")}
                if(filters){
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){FilterChip(source==null,{source=null},label={Text("All sources")});AuditSource.entries.forEach{v->FilterChip(source==v,{source=v},label={Text(v.name.lowercase())})}}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){FilterChip(actor==null,{actor=null},label={Text("All actors")});AuditActor.entries.forEach{v->FilterChip(actor==v,{actor=v},label={Text(v.name.lowercase())})}}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){FilterChip(outcome==null,{outcome=null},label={Text("All outcomes")});AuditOutcome.entries.forEach{v->FilterChip(outcome==v,{outcome=v},label={Text(v.name.lowercase())})}}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){listOf("Retained","Last hour","Last day").forEachIndexed{i,t->FilterChip(range==i,{range=i},label={Text(t)})}}
                }
                Text("${filtered.size} matching · newest 200 shown. Export includes the entire filtered snapshot.",style=MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    OutlinedButton(enabled=!busy && export==null,onClick={export=false to filtered.toList();json.launch("meshlit-audit-${System.currentTimeMillis()}.jsonl")}){Text("Export JSONL")}
                    OutlinedButton(enabled=!busy && export==null,onClick={export=true to filtered.toList();csv.launch("meshlit-audit-${System.currentTimeMillis()}.csv")}){Text("Export CSV")}
                    TextButton(enabled=!busy,onClick={clear=true}){Text("Delete local history")}
                };result?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
            }
            if(filtered.isEmpty())item{Text("No matching audit records.")}
            items(filtered.takeLast(200).asReversed(),key={it.id}){r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(T.medium),verticalArrangement=Arrangement.spacedBy(T.small)){
                Text("${r.source.name.lowercase()} · ${r.actor.name.lowercase()} · ${r.outcome.name.lowercase()}",style=MaterialTheme.typography.labelSmall)
                SelectionContainer{Text(r.action,style=MaterialTheme.typography.bodyMedium)}
                Text(DateFormat.getDateTimeInstance().format(Date(r.timeMs)),style=MaterialTheme.typography.labelSmall)
                if(r.measurements.isNotEmpty())Text(r.measurements.toString(),style=MaterialTheme.typography.bodySmall)
            }}}
        }
    }
}
