package com.meshlit.ui.modern

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.pipeline.PipelineHost
import com.meshlit.recovery.NativeCheckpoint
import com.meshlit.ui.theme.ChatTokens as T
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecoveryScreen(back:()->Unit){
    val host=koinInject<PipelineHost>();val state by host.status.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();var items by remember{mutableStateOf<List<NativeCheckpoint>>(emptyList())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var result by remember{mutableStateOf<String?>(null)};var restore by remember{mutableStateOf<NativeCheckpoint?>(null)}
    BackHandler{back()}
    fun run(action:suspend()->Unit){scope.launch{
        busy=true;error=null
        try{action();items=host.checkpointList()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Checkpoint operation failed"}finally{busy=false}
    }}
    LaunchedEffect(host){try{items=host.checkpointList()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}
    Scaffold(topBar={TopAppBar(title={Text("Checkpoints and recovery")},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                Text("Native CPU prompt-cache checkpoints",style=MaterialTheme.typography.titleMedium)
                Text("Load a model with the native CPU backend and generate first. Save its completed prompt cache, then reload the identical model and settings before restoring.")
                Text("Snapshots are encrypted on this device, at most 128 MiB each, 16 snapshots and 256 MiB total. Native chat reuses matching prompt prefixes. A cache is not conversation history or a task result.")
                Text("The manual replicated journal below is separate from native KV snapshots. Automatic coordinator failover and cross-device/RPC KV restoration remain unavailable.")
                Button(enabled=state.local && !state.starting && !busy,onClick={run{val saved=host.saveCheckpoint();result="Saved ${saved.tokens} cached tokens (${saved.bytes} bytes)"}}){Text("Save completed native cache")}
            }}}
            item { ReplicaRecoveryPanel() }
            if(busy) item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            result?.let{item{Text(it)}}
            error?.let{item{ErrorCard(it){error=null}}}
            if(items.isEmpty()) item{Text("No encrypted native checkpoints yet.")}
            items(items,key={it.id}){checkpoint ->Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                Text("Model ${checkpoint.modelSha256.take(12)} · ${checkpoint.tokens} cached tokens",style=MaterialTheme.typography.titleMedium)
                Text("${checkpoint.options.contextSize} context · ${checkpoint.options.keyCacheType} key cache · ${checkpoint.bytes} bytes")
                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(checkpoint.createdAtMs)),style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    OutlinedButton(enabled=state.local && !state.starting && !busy,onClick={restore=checkpoint}){Text("Restore")}
                    TextButton(enabled=!busy,onClick={run{host.deleteCheckpoint(checkpoint.id);result="Checkpoint deleted"}}){Text("Delete")}
                }
            }}}
        }
    }
    restore?.let{checkpoint ->AlertDialog(onDismissRequest={restore=null},title={Text("Restore this cache?")},
        text={Text("This replaces the loaded native CPU prompt cache. Model hash, executable hash, context size and precision must match. It does not restart tasks or restore conversation history.")},
        confirmButton={TextButton(onClick={restore=null;run{val restored=host.restoreCheckpoint(checkpoint.id);result="Native runtime confirmed ${restored.tokens} restored tokens"}}){Text("Restore cache")}},
        dismissButton={TextButton(onClick={restore=null}){Text("Cancel")}})}
}
