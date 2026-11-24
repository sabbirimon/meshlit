package com.meshlit.ui.modern
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.configuration.*
import com.meshlit.di.koinInject
import kotlinx.coroutines.*

@Composable fun ConfigurationScreen(onBack:()->Unit) {
    val transfer=koinInject<ConfigurationTransfer>();val context=LocalContext.current;val scope=rememberCoroutineScope()
    val history=remember{ConfigurationHistory(context)}
    var checkpoints by remember{mutableStateOf(emptyList<ConfigurationCheckpoint>())}
    var restoreAreas by remember{mutableStateOf(setOf("appearance","power","cpu","models","providers","network"))}
    var resetDialog by remember{mutableStateOf(false)}
    var resetText by remember{mutableStateOf("")}
    LaunchedEffect(Unit){checkpoints=withContext(Dispatchers.IO){history.list()}}
    var staged by remember{mutableStateOf<PortableConfiguration?>(null)};var message by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null) scope.launch{busy=true;try{val snapshot=transfer.snapshot();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(transfer.encode(snapshot))} ?: error("Cannot write export")};message="Exported schema v1 settings. No credentials, trust, grants or agent delegation."}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}finally{busy=false}}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) scope.launch{busy=true;try{staged=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytesBounded()} ?: error("Cannot read configuration");transfer.decode(bytes.toString(Charsets.UTF_8))};message="Validated. Review below before applying."}catch(e:CancellationException){throw e}catch(e:Exception){message="Import failed: ${e.message}"}finally{busy=false}}}
    if(resetDialog)AlertDialog(onDismissRequest={resetDialog=false},title={Text("Full app reset")},text={Column{Text("Deletes this app's data, imported models, configuration, journals, credentials and pairing identity. Export needed backups first. Other devices must enroll again. Type RESET to continue.");OutlinedTextField(resetText,{resetText=it})}},confirmButton={TextButton(enabled=resetText=="RESET",onClick={context.getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData()}){Text("Erase app data")}},dismissButton={TextButton(onClick={resetDialog=false}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Configuration profiles",style=MaterialTheme.typography.headlineSmall);Text("Export a versioned settings file to another phone or manage it as declarative JSON. Review each destination device before applying; hardware limits are checked locally.")
            Button(enabled=!busy,onClick={exporter.launch("meshlit-config-v1.json")}){Text("Export current configuration")}
            OutlinedButton(enabled=!busy,onClick={importer.launch(arrayOf("application/json","text/plain"))}){Text("Import for review")}
            OutlinedButton(enabled=!busy,onClick={staged=PortableConfiguration();message="Default profile staged for review."}){Text("Review safe default profile")}}
        item{Text("Configuration checkpoints",style=MaterialTheme.typography.titleMedium)
            Button(enabled=!busy,onClick={scope.launch{busy=true;try{withContext(Dispatchers.IO){history.save(transfer.snapshot())};checkpoints=withContext(Dispatchers.IO){history.list()};message="Saved full configuration and dynamic changes"}catch(e:Exception){if(e is CancellationException)throw e;message=e.message}finally{busy=false}}}){Text("Save checkpoint")}
            checkpoints.asReversed().forEach{checkpoint->TextButton(onClick={staged=checkpoint.configuration;message="Checkpoint ${checkpoint.at} staged; select restore areas below"}){Text("Restore ${checkpoint.at} · ${checkpoint.delta?.changed?.size ?: 0} changed fields")}}
            TextButton(onClick={resetDialog=true;resetText=""}){Text("Full app reset…")}
        }
        staged?.let{config->item{Card{Column(Modifier.padding(16.dp)){
            Row{TextButton(onClick={restoreAreas=setOf("appearance","power","cpu","models","providers","network")}){Text("Full configuration restore")};TextButton(onClick={restoreAreas=emptySet()}){Text("Choose areas")}}
            listOf("appearance","power","cpu","models","providers","network").forEach{area->Row{Checkbox(area in restoreAreas,{checked->restoreAreas=if(checked)restoreAreas+area else restoreAreas-area});Text(area)}}
            Text("${config.name} · schema ${config.schemaVersion}",style=MaterialTheme.typography.titleMedium)
            Text("Appearance, power policy, CPU defaults, ${config.modelOptions.size} model options, ${config.onlineProfiles.size} disabled provider templates, ${config.firewall.rules.size} listener rules.")
            Text("Import merges matching provider IDs and model IDs. Missing model IDs are skipped. Existing provider templates with matching IDs become disabled. Credentials are excluded; saved keys already on this device are retained. Device permissions, pairing keys, SSH identities, autonomous/root consent and agent scopes remain local.")
            SelectionContainer{Text(transfer.encode(config),style=MaterialTheme.typography.bodySmall)}
            Button(enabled=!busy,onClick={scope.launch{busy=true;try{val current=transfer.snapshot();withContext(Dispatchers.IO){history.save(current)}
                val selected=config.copy(appearance=if("appearance" in restoreAreas)config.appearance else current.appearance,power=if("power" in restoreAreas)config.power else current.power,
                    nativeThreadLimit=if("cpu" in restoreAreas)config.nativeThreadLimit else current.nativeThreadLimit,cpuMode=if("cpu" in restoreAreas)config.cpuMode else current.cpuMode,
                    cpuIgnoreHeat=if("cpu" in restoreAreas)config.cpuIgnoreHeat else current.cpuIgnoreHeat,cpuIgnoreSaver=if("cpu" in restoreAreas)config.cpuIgnoreSaver else current.cpuIgnoreSaver,cpuIgnoreBattery=if("cpu" in restoreAreas)config.cpuIgnoreBattery else current.cpuIgnoreBattery,cpuIgnoreMemory=if("cpu" in restoreAreas)config.cpuIgnoreMemory else current.cpuIgnoreMemory,cpuIgnoreBackground=if("cpu" in restoreAreas)config.cpuIgnoreBackground else current.cpuIgnoreBackground,
                    defaultLocalBackend=if("cpu" in restoreAreas)config.defaultLocalBackend else current.defaultLocalBackend,modelOptions=if("models" in restoreAreas)config.modelOptions else current.modelOptions,
                    onlineProfiles=if("providers" in restoreAreas)config.onlineProfiles else emptyList(),firewall=if("network" in restoreAreas)config.firewall else current.firewall)
                val report=transfer.apply(selected);checkpoints=withContext(Dispatchers.IO){history.list()};message=report.joinToString("\n");staged=null}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}finally{busy=false}}}){Text("Apply reviewed configuration")}
            TextButton(onClick={staged=null}){Text("Discard preview")}
        }}}}
        item{if(busy) LinearProgressIndicator(Modifier.fillMaxWidth());message?.let{Text(it)};Text("Configuration spans multiple settings stores. Validation runs first; an interrupted apply reports progress and can be reapplied. This is not a distributed transaction or a cluster enrollment bundle.")}
    }
}
private fun java.io.InputStream.readBytesBounded():ByteArray {
    val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
    while(true){val read=read(chunk);if(read<0) break;require(buffer.size()+read<=256*1024){"Configuration exceeds 256 KiB"};buffer.write(chunk,0,read)}
    return buffer.toByteArray()
}
