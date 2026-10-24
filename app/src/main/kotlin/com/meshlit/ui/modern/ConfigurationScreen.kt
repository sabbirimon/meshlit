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
    var staged by remember{mutableStateOf<PortableConfiguration?>(null)};var message by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null) scope.launch{busy=true;try{val snapshot=transfer.snapshot();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(transfer.encode(snapshot))} ?: error("Cannot write export")};message="Exported schema v1 settings. No credentials, trust, grants or agent delegation."}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}finally{busy=false}}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) scope.launch{busy=true;try{staged=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytesBounded()} ?: error("Cannot read configuration");transfer.decode(bytes.toString(Charsets.UTF_8))};message="Validated. Review below before applying."}catch(e:CancellationException){throw e}catch(e:Exception){message="Import failed: ${e.message}"}finally{busy=false}}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Configuration profiles",style=MaterialTheme.typography.headlineSmall);Text("Export a versioned settings file to another phone or manage it as declarative JSON. Review each destination device before applying; hardware limits are checked locally.")
            Button(enabled=!busy,onClick={exporter.launch("meshlit-config-v1.json")}){Text("Export current configuration")}
            OutlinedButton(enabled=!busy,onClick={importer.launch(arrayOf("application/json","text/plain"))}){Text("Import for review")}
            OutlinedButton(enabled=!busy,onClick={staged=PortableConfiguration();message="Default profile staged for review."}){Text("Review safe default profile")}}
        staged?.let{config->item{Card{Column(Modifier.padding(16.dp)){
            Text("${config.name} · schema ${config.schemaVersion}",style=MaterialTheme.typography.titleMedium)
            Text("Appearance, power policy, CPU defaults, ${config.modelOptions.size} model options, ${config.onlineProfiles.size} disabled provider templates, ${config.firewall.rules.size} listener rules.")
            Text("Import merges matching provider IDs and model IDs. Missing model IDs are skipped. Existing provider templates with matching IDs become disabled. Credentials are excluded; saved keys already on this device are retained. Device permissions, pairing keys, SSH identities, autonomous/root consent and agent scopes remain local.")
            SelectionContainer{Text(transfer.encode(config),style=MaterialTheme.typography.bodySmall)}
            Button(enabled=!busy,onClick={scope.launch{busy=true;try{val report=transfer.apply(config);message=report.joinToString("\n");staged=null}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}finally{busy=false}}}){Text("Apply reviewed configuration")}
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
