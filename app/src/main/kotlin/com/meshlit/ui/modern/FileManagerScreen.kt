package com.meshlit.ui.modern

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.meshlit.files.*
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun FileManagerScreen(back:()->Unit){
    val context=LocalContext.current;val resolver=context.contentResolver;val scope=rememberCoroutineScope()
    var tab by remember{mutableStateOf("Browse")};var selected by remember{mutableStateOf<List<Uri>>(emptyList())}
    var archive by remember{mutableStateOf<Uri?>(null)};var tree by remember{mutableStateOf<Uri?>(null)}
    var limitGiB by remember{mutableStateOf("32")};var progress by remember{mutableStateOf<ArchiveProgress?>(null)}
    var job by remember{mutableStateOf<Job?>(null)};var message by remember{mutableStateOf<String?>(null)};var preview by remember{mutableStateOf<String?>(null)}
    BackHandler{back()}
    fun limits():ArchiveLimits {val gib=limitGiB.toLongOrNull() ?: error("Enter a byte limit in GiB");require(gib in 1..128){"Choose 1–128 GiB"};return ArchiveLimits(maxExpandedBytes=gib*1024*1024*1024).also{it.validate()}}
    fun name(uri:Uri)=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst()) cursor.getString(0) else null} ?: "file"
    fun grant(uri:Uri){runCatching{resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
    fun run(action:suspend()->Unit){val launched=scope.launch(start=CoroutineStart.LAZY){message=null;progress=null;try{action()}catch(e:CancellationException){if(message==null) message="Cancelled; incomplete output cleanup was attempted.";throw e}catch(e:Exception){message=message?.let{it+"\nCause: "+(e.message ?: "operation failed")} ?: e.message ?: "File operation failed"}finally{job=null}};job=launched;launched.start()}
    val inputs=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->selected=uris;uris.forEach(::grant)}
    val zipInput=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->archive=uri;uri?.let(::grant)}
    val outputTree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->tree=uri;uri?.let{runCatching{resolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}}}
    val zipOutput=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)run{
        val configured=limits();var finished=false
        try{withContext(Dispatchers.IO){var last=0L
            val result=StreamingZip.create(selected.map{source->ArchiveInput(name(source)){resolver.openInputStream(source) ?: error("Source permission unavailable")}},resolver.openOutputStream(uri,"wt") ?: error("Output permission unavailable"),configured){value->val now=android.os.SystemClock.elapsedRealtime();if(now-last>=200){progress=value;last=now}}
            progress=result;finished=true;message="Created ZIP: ${result.files} files, ${result.bytes} source bytes"
        }}finally{if(!finished)withContext(NonCancellable+Dispatchers.IO){if(DocumentFile.fromSingleUri(context,uri)?.delete()!=true)message="Incomplete ZIP could not be removed: $uri"}}
    }}
    val inspect=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)run{withContext(Dispatchers.IO){
        val filename=name(uri);val mime=resolver.getType(uri) ?: "unknown";val bytes=resolver.openInputStream(uri)?.use{source->val buffer=ByteArray(16385);var size=0;while(size<buffer.size){currentCoroutineContext().ensureActive();val count=source.read(buffer,size,buffer.size-size);if(count<0)break;if(count>0)size+=count};buffer.copyOf(size)} ?: error("Read permission unavailable")
        val textTypes=setOf("json","jsonl","csv","tsv","txt","md","yaml","yml","toml","py","kt","js","ts","html","xml","log","ini","tf","hcl")
        preview="$filename\nMIME: $mime\n"+if(filename.substringAfterLast('.',"").lowercase() in textTypes) bytes.take(16384).toByteArray().toString(Charsets.UTF_8)+(if(bytes.size>16384) "\n[Preview truncated at 16 KiB]" else "") else "Binary file; use Models for GGUF validation/import or the corresponding media/IDE tool. Raw tensors and weights are never rendered as text."
    }}}
    if(tab=="Browse"){Column(Modifier.fillMaxSize()){
        Row(Modifier.padding(T.small)){TextButton(onClick=back){Text("Back")};TextButton(onClick={tab="AI files"}){Text("AI files")};TextButton(onClick={tab="ZIP tools"}){Text("ZIP / unzip")}}
        Box(Modifier.weight(1f)){com.meshlit.ui.screens.FilesScreen(onOpenDrawer=back)}
    };return}
    Scaffold(topBar={TopAppBar(title={Text("Files and AI assets")},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{FlowRow{listOf("Browse","AI files","ZIP tools").forEach{label->FilterChip(tab==label,{tab=label},label={Text(label)})}}}
            if(tab=="AI files"){
                item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                    Text("AI file toolkit",style=MaterialTheme.typography.headlineSmall)
                    Text("Browse, copy/move/share/delete through the existing file browser. Inspect bounded text previews; manage weights in Models, datasets/adapters in Fine-tuning, code in the workspace and images/audio/video through media tools.")
                    Text("Relevant formats: GGUF; safetensors/bin/PT/PTH/ONNX weights; tokenizer JSON, vocab and SentencePiece; LoRA adapters; JSONL/JSON/CSV/Parquet datasets; Markdown/PDF documents; WAV/MP3/images/video; ZIP bundles. Handling a file does not imply this phone can execute/train every format.")
                    Button(enabled=job==null,onClick={inspect.launch(arrayOf("*/*"))}){Text("Inspect a granted file")}
                }}}
                preview?.let{item{SelectionContainer{Text(it,style=MaterialTheme.typography.bodySmall)}}}
            }else{
                item{Text("Streaming ZIP / unzip",style=MaterialTheme.typography.headlineSmall);Text("Human-selected files and output folder only. At most 10,000 entries, depth 16; no overwrite. Extraction goes to a new folder. Paths cannot escape it. Encrypted ZIP, RAR, 7z, links/permissions and multi-volume archives are unsupported. Large files stream in 64 KiB chunks; output-provider space/quota errors can still occur.")}
                item{OutlinedTextField(limitGiB,{limitGiB=it},label={Text("Maximum input/expanded size, GiB (1–128)")},singleLine=true)}
                item{Card{Column(Modifier.padding(T.large)){
                    Text("Create ZIP",style=MaterialTheme.typography.titleMedium)
                    OutlinedButton(enabled=job==null,onClick={inputs.launch(arrayOf("*/*"))}){Text("Select multiple files (${selected.size})")}
                    Button(enabled=selected.isNotEmpty() && job==null,onClick={try{limits();zipOutput.launch("meshlit-assets.zip")}catch(e:Exception){message=e.message}}){Text("Choose output ZIP and create")}
                }}}
                item{Card{Column(Modifier.padding(T.large)){
                    Text("Extract ZIP",style=MaterialTheme.typography.titleMedium)
                    OutlinedButton(enabled=job==null,onClick={zipInput.launch(arrayOf("application/zip","application/octet-stream"))}){Text(if(archive==null) "Select ZIP" else "ZIP selected")}
                    OutlinedButton(enabled=job==null,onClick={outputTree.launch(null)}){Text(if(tree==null) "Choose output folder" else "Output folder selected")}
                    Button(enabled=archive!=null && tree!=null && job==null,onClick={run{withContext(Dispatchers.IO){
                        val configured=limits();val root=DocumentFile.fromTreeUri(context,tree!!) ?: error("Output folder permission unavailable")
                        val destination=root.createDirectory("meshlit-unpacked-${UUID.randomUUID().toString().take(8)}") ?: error("Cannot create extraction folder")
                        var finished=false
                        fun directory(path:String):DocumentFile {var parent=destination;path.split('/').filter{it.isNotBlank()}.forEach{part->parent=parent.findFile(part)?.also{require(it.isDirectory){"Archive directory conflicts with a file"}} ?: parent.createDirectory(part) ?: error("Cannot create archive directory")};return parent}
                        try{var last=0L
                            val result=StreamingZip.extract(resolver.openInputStream(archive!!) ?: error("ZIP read permission unavailable"),configured,{directory(it)},{path->
                                val folder=path.substringBeforeLast('/',"").let{if(it.isBlank()) destination else directory(it)};val leaf=path.substringAfterLast('/')
                                require(folder.findFile(leaf)==null){"Archive output already exists"};val file=folder.createFile("application/octet-stream",leaf) ?: error("Cannot create archive file")
                                require(file.name==leaf){"Storage provider changed the output name"};resolver.openOutputStream(file.uri,"wt") ?: error("Cannot write archive file")
                            }){value->val now=android.os.SystemClock.elapsedRealtime();if(now-last>=200){progress=value;last=now}}
                            progress=result;finished=true;message="Extracted ${result.files} files, ${result.bytes} bytes into ${destination.uri}"
                        }finally{if(!finished)withContext(NonCancellable){if(!destination.delete())message="Partial extraction cleanup failed: ${destination.uri}"}}
                    }}}){Text("Extract into a new folder")}
                }}}
            }
            if(job!=null) item{LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text("Cancel operation")}}
            progress?.let{item{Text("${it.files} completed files · ${it.bytes} streamed bytes\n${it.current}")}}
            message?.let{item{Text(it)}}
        }
    }
}
