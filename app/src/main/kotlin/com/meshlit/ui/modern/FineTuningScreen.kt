package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.ssh.SshConnections
import com.meshlit.training.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FineTuningScreen(onBack:()->Unit) {
    val service=koinInject<TrainingHost>();val ssh=koinInject<SshConnections>()
    val saved by service.config.collectAsStateWithLifecycle();val jobs by service.jobs.collectAsStateWithLifecycle();val hosts by ssh.connections.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();var host by remember(saved){mutableStateOf(saved)}
    var base by remember{mutableStateOf("")};var dataset by remember{mutableStateOf("")};var epochs by remember{mutableStateOf("1")};var rank by remember{mutableStateOf("16")};var length by remember{mutableStateOf("512")}
    var quantized by remember{mutableStateOf(false)};var stream by remember{mutableStateOf(false)};var consent by remember{mutableStateOf(false)};var busy by remember{mutableStateOf<Job?>(null)};var output by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    fun run(action:suspend()->String){busy=scope.launch{try{output=action();error=null}catch(e:CancellationException){error="SSH request cancelled. Training may still be running; refresh the saved job before retrying.";throw e}catch(e:Exception){error=e.message?.take(500) ?: "Training host request failed"}finally{busy=null}}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Fine-tuning",style=MaterialTheme.typography.headlineSmall)
            Text("Use an owner-installed Soup 0.75.0 environment and the Meshlit training companion on a Linux/macOS SSH host. The phone manages real host jobs; Android autograd and distributed phone training are unavailable. No datasets or dependencies are silently downloaded.")}
        item{Text("Training host",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){hosts.forEach{c->FilterChip(host.connectionId==c.id,{host=host.copy(connectionId=c.id)},label={Text(c.name)})}};if(hosts.isEmpty()) Text("Add a pinned host in Settings → SSH connections first.")}
        item{OutlinedTextField(host.pythonPath,{host=host.copy(pythonPath=it)},Modifier.fillMaxWidth(),label={Text("Host Python executable (absolute path)")});OutlinedTextField(host.scriptPath,{host=host.copy(scriptPath=it)},Modifier.fillMaxWidth(),label={Text("Host meshlit_training.py path")});OutlinedTextField(host.workspacePath,{host=host.copy(workspacePath=it)},Modifier.fillMaxWidth(),label={Text("Existing host workspace directory")})
            Button(enabled=busy==null,onClick={try{service.save(host);error=null}catch(e:Exception){error=e.message}}){Text("Save host")};OutlinedButton(enabled=busy==null && saved.connectionId.isNotBlank(),onClick={run{service.doctor().toString()}}){Text("Check actual Soup environment")}}
        item{Text("Text SFT / LoRA recipe",style=MaterialTheme.typography.titleMedium);Text("Paths are on the host, inside its workspace. Base must be materialized Transformers safetensors weights, not a GGUF file. Dataset: 10+ real Alpaca JSONL examples, at most 64 MiB. Soup layer streaming is experimental training memory management, separate from phone inference sharding.")}
        item{OutlinedTextField(base,{base=it},Modifier.fillMaxWidth(),label={Text("Local base snapshot directory")});OutlinedTextField(dataset,{dataset=it},Modifier.fillMaxWidth(),label={Text("Local dataset.jsonl")})
            OutlinedTextField(epochs,{epochs=it},label={Text("Epochs (1–5)")});OutlinedTextField(rank,{rank=it},label={Text("LoRA rank (1–64)")});OutlinedTextField(length,{length=it},label={Text("Sequence length (128–4,096)")})
            Row{Text("QLoRA 4-bit (compatible host GPU required)",Modifier.weight(1f));Switch(quantized,{quantized=it})};Row{Text("Experimental Soup layer streaming",Modifier.weight(1f));Switch(stream,{stream=it})}
            Row{Checkbox(consent,{consent=it});Text("I approve this host training job, dataset use and its compute/power consumption.")}
            Button(enabled=consent && busy==null && saved.connectionId.isNotBlank(),onClick={run{val spec=TrainingSpec(UUID.randomUUID().toString(),base.trim(),dataset.trim(),epochs.toIntOrNull() ?: 0,rank.toIntOrNull() ?: 0,if(quantized) "4bit" else "none",stream,length.toIntOrNull() ?: 0);service.start(spec).toString()}}){Text("Start real host training")}}
        item{Text("Saved job references",style=MaterialTheme.typography.titleMedium);Text("A lost connection does not mean training stopped. Refresh status before starting another job. Cancel submits a request; the worker must acknowledge it. One job per host workspace; four-hour execution limit. Logs can contain dataset excerpts: export/share them carefully.")}
        items(jobs.reversed(),key={it.jobId}){ref->Card{Column(Modifier.padding(12.dp)){Text(ref.jobId);Row{TextButton(enabled=busy==null,onClick={run{service.status(ref).toString()}}){Text("Refresh status / logs")};TextButton(enabled=busy==null,onClick={run{service.status(ref,true).toString()}}){Text("Request cancellation")}}}}}
        item{if(busy!=null){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={busy?.cancel()}){Text("Stop SSH request")}};SelectionContainer{Text(output)};error?.let{ErrorCard(it){error=null}}}
        item{Text("Completed requires Soup exit 0 plus real adapter files and hashes. Evaluate the adapter on held-out examples before deployment. Merge/export to GGUF on the host and import it through Models; direct adapter loading is not implemented. No quality, speed, compression ratio or phone training success is claimed.")}
    }
}
