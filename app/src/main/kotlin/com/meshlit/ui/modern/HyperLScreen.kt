package com.meshlit.ui.modern

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.provider.DocumentsContract
import com.meshlit.core.hyperl.*
import com.meshlit.di.koinInject
import com.meshlit.hyperl.*
import com.meshlit.operations.OperationsControl
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun HyperLScreen(back:()->Unit) {
    val context=LocalContext.current
    val control=koinInject<OperationsControl>();val policy by control.gate.policy.collectAsState()
    val controller=remember(control){HyperLWorkbenchController(control.gate)};val scope=rememberCoroutineScope()
    val prefs=remember(context){context.getSharedPreferences("hyperl-alpha6",android.content.Context.MODE_PRIVATE)}
    var enabled by remember{mutableStateOf(prefs.getBoolean("community-1-enabled",false))}
    var showLicence by remember{mutableStateOf(false)}
    var licence by remember{mutableStateOf("Loading offline notices…")}
    LaunchedEffect(Unit){licence=withContext(Dispatchers.IO){listOf("LICENSE","NOTICE","Apache-2.0.txt","LICENSE_HISTORY.md").joinToString("\n\n"){name->
        context.assets.open("hyperl/$name").bufferedReader().use{it.readText()}
    }}}
    val clipboard=LocalClipboardManager.current
    var selected by remember{mutableStateOf("weighted_relu")};var menu by remember{mutableStateOf(false)}
    val first=remember{HyperLLibrary.recipe("weighted_relu")}
    var program by remember{mutableStateOf(HyperLCodec.json.encodeToString(first.program))}
    var inputs by remember{mutableStateOf(HyperLCodec.json.encodeToString(first.example))}
    var budget by remember{mutableStateOf("16")};var output by remember{mutableStateOf("Choose a recipe, review the inputs and run it locally.")}
    var running by remember{mutableStateOf<Job?>(null)};var sourceTarget by remember{mutableStateOf(HyperLTarget.METAL)}
    var choice by remember{mutableStateOf(HyperLCpuChoice.REFERENCE)}
    val idle=running?.isActive!=true
    val allowed=enabled && idle && !policy.emergencyStopped && (com.meshlit.core.common.control.ManagedFeature.HYPERL !in policy.disabled)
    fun start(work:suspend()->String){
        if(!enabled || !idle)return
        val job=scope.launch(start=CoroutineStart.LAZY) {
            output="Running…"
            try{output=work()}catch(e:TimeoutCancellationException){output="Time limit reached. No completed measurement is reported; check dataset state before retrying."}
            catch(e:CancellationException){output="Stopped. Check the dataset list for a completed operation; an interrupted document export may need removal.";throw e}
            catch(e:Exception){output="Failed: ${e.message.orEmpty().take(1024)}"}
            finally{running=null}
        };running=job;job.start()
    }
    if(showLicence) AlertDialog(onDismissRequest={showLicence=false},title={Text("HyperL licences and notices")},
        text={SelectionContainer{Text(licence,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))}},
        confirmButton={TextButton(onClick={showLicence=false}){Text("Close")}})
    Scaffold(topBar={TopAppBar(title={Text("HyperL libraries")},navigationIcon={TextButton(onClick=back){Text("Back")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("hyperl-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text("HyperL alpha.6",style=MaterialTheme.typography.headlineSmall)
                Text("Offline preprocessing with Kotlin or bundled C99 CPU execution. This is separate from your chat model. Metal/Vulkan exports are source for qualified hosts; phone GPU execution and full neural networks are not available here.")}
            item{OutlinedCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Free for personal, developer and research use, and organizations below both enterprise thresholds. Large entities (US$10M revenue/budget or 100 employees) need a paid licence after their six-month production trial. Earlier Apache grants remain.")
                TextButton(onClick={showLicence=true}){Text("Read full licence and notices")}
                Row{Switch(enabled,{value->
                    if(prefs.edit().putBoolean("community-1-enabled",value).commit()){enabled=value;if(!value)running?.cancel()}
                    else output="Could not save HyperL setting"
                },Modifier.testTag("hyperl-enable"));Text("Enable alpha.6 under these terms",Modifier.padding(start=8.dp,top=12.dp))}
            }}}
            if(policy.emergencyStopped)item{Text("Emergency stop is latched. Resume from Operations to allow execution.",color=MaterialTheme.colorScheme.error)}
            if(com.meshlit.core.common.control.ManagedFeature.HYPERL in policy.disabled)item{Text("HyperL is disabled in Operations.",color=MaterialTheme.colorScheme.error)}
            item{Text("1 · CPU workspace",style=MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(choice==HyperLCpuChoice.REFERENCE,{choice=HyperLCpuChoice.REFERENCE},label={Text("Kotlin reference")},enabled=idle)
                    FilterChip(choice==HyperLCpuChoice.NATIVE,{choice=HyperLCpuChoice.NATIVE},label={Text("Native C99")},enabled=idle,modifier=Modifier.testTag("hyperl-native"))
                }}
            item{Box{OutlinedButton(onClick={menu=true},enabled=idle,modifier=Modifier.testTag("hyperl-recipe")){Text(HyperLLibrary.recipe(selected).title)}
                DropdownMenu(menu,{menu=false}){HyperLLibrary.ids.forEach{id->DropdownMenuItem(text={Text(HyperLLibrary.recipe(id).title)},onClick={
                    selected=id;val r=HyperLLibrary.recipe(id);program=HyperLCodec.json.encodeToString(r.program);inputs=HyperLCodec.json.encodeToString(r.example);menu=false
                })}}};Text(HyperLLibrary.recipe(selected).purpose)}
            item{OutlinedTextField(program,{if(it.length<=65536)program=it},Modifier.fillMaxWidth().testTag("hyperl-program"),label={Text("Program JSON")},minLines=4,maxLines=12,enabled=idle)}
            item{OutlinedTextField(inputs,{if(it.length<=65536)inputs=it},Modifier.fillMaxWidth().testTag("hyperl-inputs"),label={Text("Input vectors JSON")},minLines=3,maxLines=8,enabled=idle)}
            item{OutlinedTextField(budget,{if(it.length<=4)budget=it},modifier=Modifier.testTag("hyperl-budget"),label={Text("Array budget · MiB (1–1024)")},singleLine=true,enabled=idle)}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(enabled=allowed,onClick={start{withContext(Dispatchers.Default){val p=controller.plan(program,inputs,budget.toLong()*1024*1024,choice);"${choice.name} · valid graph · estimated ${p.estimatedBytes} bytes · available ${p.headroomBytes} · admitted ${p.admitted}. Native mode includes JNI snapshots; admission is checked again before execution."}}}){Text("Validate")}
                Button(enabled=allowed,onClick={start{val r=controller.execute(program,inputs,budget.toLong()*1024*1024,choice)
                    "${r.backend} · ${r.values.size} outputs · ${"%.3f".format(r.wallMs)} ms total\n"+HyperLCodec.json.encodeToString(r.values.take(256))+if(r.values.size>256)"\nPreview limited to 256 values" else ""
                }}){Text("Run CPU")}
                OutlinedButton(enabled=!idle,onClick={running?.cancel()}){Text("Stop")}
                OutlinedButton(enabled=allowed,onClick={start{
                    val r=controller.benchmark(program,inputs,budget.toLong()*1024*1024,choice)
                    "${r.backend} · 1 warm-up + ${r.samplesMs.size} measured runs\nMedian ${"%.3f".format(r.medianMs)} ms · p95 ${"%.3f".format(r.p95Ms)} ms\nSamples: ${r.samplesMs.joinToString{ "%.3f".format(it) }} ms\n${r.outputCount} outputs · repeated output hashes match\nOutput SHA-256 ${r.outputSha256}\nGraph SHA-256 ${r.graphSha256}\n${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.firstOrNull()}\nExecution includes admission/JNI copies; editor parsing and output hashing are outside samples. Small-run p95 is the slowest sample, not a sustained-throughput benchmark."
                }}){Text("Measure CPU")}
                OutlinedButton(enabled=allowed,onClick={start{"precise-sum/1 · ${choice.name}\n${controller.precise(inputs,budget.toLong()*1024*1024,choice)}"}},modifier=Modifier.testTag("hyperl-precise")){Text("Precise sum")}
            };Text("Precise sum uses one input vector and compensated double accumulation. Normal graph sums keep their original ordered float semantics.",style=MaterialTheme.typography.bodySmall)}
            item{Text("2 · Source export",style=MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    FilterChip(sourceTarget==HyperLTarget.METAL,{sourceTarget=HyperLTarget.METAL},label={Text("Metal source")},enabled=idle)
                    FilterChip(sourceTarget==HyperLTarget.VULKAN_SPIRV,{sourceTarget=HyperLTarget.VULKAN_SPIRV},label={Text("Vulkan source")},enabled=idle)
                };OutlinedButton(enabled=allowed,onClick={start{controller.emit(program,sourceTarget)}}){Text("Generate source")}}
            item{Text("3 · Encrypted datasets",style=MaterialTheme.typography.titleLarge)
                if(Build.VERSION.SDK_INT>=26) HyperLDatasetPanel(control,allowed,::start)
                else Text("Dataset storage requires Android 8/API 26. CPU and source tools remain available on Android 7.")}
            item{Text("Result",style=MaterialTheme.typography.titleLarge)
                SelectionContainer{Text(output,modifier=Modifier.testTag("hyperl-output"),style=MaterialTheme.typography.bodyMedium)}}
            item{OutlinedButton(enabled=idle,onClick={clipboard.setText(AnnotatedString(output))}){Text("Copy output")}}
            item{Text("Admission estimates do not reserve memory. HyperL makes no network calls or agent delegations. Selected document providers may synchronize imports/exports to their own servers. Native elapsed time includes JNI copies; it is not model token speed.",style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@RequiresApi(26)
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun HyperLDatasetPanel(control:OperationsControl,allowed:Boolean,start:(suspend()->String)->Unit) {
    val context=LocalContext.current
    val workspace=remember(context,control){runCatching{HyperLDatasets(File(context.noBackupFilesDir,"hyperl-alpha6"),control.gate)}}
    val datasets=workspace.getOrNull()
    if(datasets==null){Text("Dataset workspace unavailable. Check local storage and reopen this screen.");return}
    var ids by remember{mutableStateOf(datasets.ids())}
    var selected by remember{mutableStateOf(ids.firstOrNull())}
    var menu by remember{mutableStateOf(false)}
    fun refresh(id:String?=null){ids=datasets.ids();selected=id ?: selected?.takeIf{it in ids} ?: ids.firstOrNull()}
    fun details(s:DatasetSummary)="${s.bytes} bytes · ${s.chunks} authenticated chunks\nSHA-256 ${s.sha256}"
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null && allowed)start{val (id,summary)=datasets.import{checkNotNull(context.contentResolver.openInputStream(uri)){"Could not open selected document"}}
            refresh(id);"Encrypted dataset ${id.take(8)}\n${details(summary)}"}
    }
    var exportId by remember{mutableStateOf<String?>(null)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val id=exportId;exportId=null
        if(uri!=null){
            if(allowed && id!=null)start{
                try {
                    val summary=datasets.export(id){file->
                        checkNotNull(context.contentResolver.openOutputStream(uri,"wt")){"Could not open output document"}.use{output->
                            file.inputStream().use{input->val buffer=ByteArray(64*1024)
                                try{while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n)};output.flush()}
                                finally{buffer.fill(0)}
                            }
                        }
                    };"Verified plaintext exported to your chosen document. It is no longer encrypted.\n${details(summary)}"
                } catch(error:Exception){
                    val removed=runCatching{DocumentsContract.deleteDocument(context.contentResolver,uri)}.getOrDefault(false)
                    if(error is CancellationException)throw error
                    error("Export failed. ${if(removed)"Incomplete document removed." else "Remove the empty/partial document at the selected destination."}")
                }
            } else runCatching{DocumentsContract.deleteDocument(context.contentResolver,uri)}
        }
    }
    Text("AES-256-GCM HLM2 · up to four datasets of 64 MiB each. Data and private keys stay in this app's no-backup storage. Uninstalling or clearing app data loses them. Key rotation makes a new copy and preserves the original. Export is explicitly plaintext.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(enabled=allowed && ids.size<HyperLDatasets.MAX_DATASETS,onClick={importer.launch(arrayOf("*/*"))},modifier=Modifier.testTag("hyperl-import")){Text("Import and encrypt")}
        Box{OutlinedButton(enabled=allowed && ids.isNotEmpty(),onClick={menu=true}){Text(selected?.take(8) ?: "No dataset")}
            DropdownMenu(menu,{menu=false}){ids.forEach{id->DropdownMenuItem(text={Text(id)},onClick={selected=id;menu=false})}}}
        OutlinedButton(enabled=allowed && selected!=null,onClick={selected?.let{id->start{"Verified ${id.take(8)}\n${details(datasets.verify(id))}"}}}){Text("Verify dataset")}
        OutlinedButton(enabled=allowed && selected!=null && ids.size<HyperLDatasets.MAX_DATASETS,onClick={selected?.let{id->start{
            val (newId,s)=datasets.rotate(id);refresh(newId);"Rotated to ${newId.take(8)} with a new private key. Original retained.\n${details(s)}"
        }}}){Text("Rotate key")}
        OutlinedButton(enabled=allowed && selected!=null,onClick={exportId=selected;exporter.launch("hyperl-dataset.bin")}){Text("Export plaintext")}
    }
    var confirmDelete by remember{mutableStateOf<String?>(null)}
    TextButton(enabled=allowed && selected!=null,onClick={confirmDelete=selected}){Text("Remove selected dataset")}
    TextButton(enabled=allowed,onClick={refresh()}){Text("Refresh datasets")}
    confirmDelete?.let{id->AlertDialog(onDismissRequest={confirmDelete=null},title={Text("Remove this dataset and its private key?")},
        text={Text("${id.take(8)} cannot be recovered from this workspace after removal.")},
        confirmButton={TextButton(onClick={confirmDelete=null;start{datasets.delete(id);refresh();"Dataset removed"}}){Text("Remove")}},
        dismissButton={TextButton(onClick={confirmDelete=null}){Text("Keep")}})}
}
