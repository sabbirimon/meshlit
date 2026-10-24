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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.observability.LogSource
import com.meshlit.di.koinInject
import com.meshlit.observability.*
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun ModernLogsScreen(onBack:()->Unit,initialSource:LogSource?=null,title:String="Logs and export") {
    val buffer=koinInject<LogBuffer>();val entries by buffer.entries.collectAsStateWithLifecycle()
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var query by remember{mutableStateOf("")};var level by remember{mutableStateOf(LogBuffer.Level.INFO)}
    var source by remember(initialSource){mutableStateOf(initialSource)};var export by remember{mutableStateOf<Pair<LogExporter.Format,List<LogBuffer.Entry>>?>(null)}
    var result by remember{mutableStateOf<String?>(null)}
    var clearConfirm by remember{mutableStateOf(false)}
    if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text("Clear local log buffer?")},text={Text("This clears the in-memory buffer. Existing exports and Android logcat remain. Export useful diagnostics first.")},confirmButton={TextButton(onClick={buffer.clear();clearConfirm=false}){Text("Clear")}},dismissButton={TextButton(onClick={clearConfirm=false}){Text("Cancel")}})
    val filtered=remember(entries,query,level,source){entries.filter{it.level.ordinal>=level.ordinal && (source==null || it.source==source)}
        .map(LogRedaction::entry).filter{"${it.tag} ${it.message} ${it.context}".contains(query,true)}}
    fun save(uri:android.net.Uri?){val snapshot=export;export=null;if(uri==null || snapshot==null)return
        scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{writer ->
            snapshot.second.forEach{writer.write(if(snapshot.first==LogExporter.Format.TXT)it.format() else it.toJsonLine());writer.newLine()}
        } ?: error("Cannot write selected file")};result="Exported ${snapshot.second.size} filtered entries"}
        catch(e:CancellationException){throw e}catch(e:Exception){result="Export failed: ${e.message}"}}
    }
    val txt=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"),::save)
    val json=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson"),::save)
    Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
            item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search tag or message")},singleLine=true)}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){LogBuffer.Level.entries.forEach{value ->
                FilterChip(level==value,{level=value},label={Text("${value.name}+")})}}}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
                FilterChip(source==null,{source=null},label={Text("All sources")})
                LogSource.entries.forEach{value -> FilterChip(source==value,{source=value},label={Text(value.name)})}
            }}
            item{Text("${filtered.size} matching entries. Common credentials and sensitive context fields are redacted.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    OutlinedButton(enabled=export==null,onClick={export=LogExporter.Format.TXT to filtered.toList();txt.launch("meshlit-${System.currentTimeMillis()}.txt")}){Text("Export TXT")}
                    OutlinedButton(enabled=export==null,onClick={export=LogExporter.Format.JSONL to filtered.toList();json.launch("meshlit-${System.currentTimeMillis()}.jsonl")}){Text("Export JSONL")}
                };OutlinedButton(onClick={clearConfirm=true}){Text("Clear local buffer")};Text("Bounded to ${buffer.maxEntries} entries; older entries are evicted. App restart clears this buffer.",style=MaterialTheme.typography.bodySmall);result?.let{Text(it)}
            }
            if(filtered.isEmpty()) item{Text("No matching logs yet.")}
            items(filtered.takeLast(1000).asReversed()){entry -> Card(Modifier.fillMaxWidth()){
                Column(Modifier.padding(T.medium)){
                    Text("${entry.level} · ${entry.source} · ${entry.tag}",style=MaterialTheme.typography.labelSmall)
                    SelectionContainer{Text(entry.message,style=MaterialTheme.typography.bodySmall)}
                    if(entry.context.isNotEmpty()) Text(entry.context.toString(),style=MaterialTheme.typography.bodySmall)
                }
            }}
        }
    }
}
