package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.RecommendedModelArtifact
import com.meshlit.workspace.RecommendedModelCatalog
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path

internal data class DesktopModelAdvice(val model:RecommendedModelArtifact,val memory:Long,val storage:Long,val fits:Boolean,val ready:Boolean)
internal fun desktopModelAdvice(total:Long,available:Long,free:Long,engineAvailable:Boolean,smallest:Boolean):List<DesktopModelAdvice> =
    RecommendedModelCatalog.models.map { model ->
        val memory=(model.bytes*1.2).toLong()+512L*1024*1024
        val storage=model.bytes+1024L*1024*1024
        val fits=engineAvailable && total>0 && memory<total/2
        DesktopModelAdvice(model,memory,storage,fits,fits && memory<available/2 && free>=storage)
    }.sortedWith(compareByDescending<DesktopModelAdvice>{it.ready}.thenByDescending{it.fits}.thenBy{if(smallest)it.model.bytes else -it.model.bytes})

@Composable internal fun ModelRecommendations(onLoad:(SavedModel)->Unit) {
    val scope=rememberCoroutineScope()
    val hardware=remember{oshi.SystemInfo()}
    val directory=remember{Path.of(System.getProperty("meshlit.desktop.dataDir",Path.of(System.getProperty("user.home"),".meshlit","desktop").toString())).resolve("weights")}
    var reading by remember{mutableStateOf(Triple(0L,0L,0L))}
    var smallest by remember{mutableStateOf(false)}
    var expanded by remember{mutableStateOf(false)}
    var installed by remember{mutableStateOf<List<SavedModel>>(emptyList())}
    var status by remember{mutableStateOf("")}
    var active by remember{mutableStateOf<HubClient?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    val engineAvailable=remember{LocalEngine().use{it.available()}}
    LaunchedEffect(Unit){
        while(isActive){
            try {
                val sample=withContext(Dispatchers.IO){
                    val memory=hardware.hardware.memory
                    var ancestor=directory
                    while(!Files.exists(ancestor)) ancestor=ancestor.parent
                    Triple(memory.total,memory.available,Files.getFileStore(ancestor).usableSpace) to ModelStore().load()
                }
                reading=sample.first;installed=sample.second
            } catch(c:CancellationException){throw c} catch(_:Exception){status="Hardware or installed-model inventory unavailable"}
            delay(5000)
        }
    }
    DisposableEffect(Unit){onDispose{active?.close();job?.cancel()}}
    Text("Recommended for this computer",style=MaterialTheme.typography.h6)
    Text("RAM ${formatMemory(reading.first)} · available ${formatMemory(reading.second)} · free disk ${formatMemory(reading.third)}. CPU estimates; GPU/NPU performance is not assumed.",style=MaterialTheme.typography.caption)
    Row { TextButton({smallest=false}){Text(if(!smallest)"✓ Best fit" else "Best fit")};TextButton({smallest=true}){Text(if(smallest)"✓ Save storage" else "Save storage")} }
    val advice=desktopModelAdvice(reading.first,reading.second,reading.third,engineAvailable,smallest)
    (if(expanded) advice else advice.take(1)).forEach { item ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(item.model.name,style=MaterialTheme.typography.subtitle1)
            Text(item.model.purpose)
            Text("Download ${formatMemory(item.model.bytes)} · estimated memory ${formatMemory(item.memory)} · ${if(item.ready)"estimated fit now" else if(item.fits)"close other workloads / free disk first" else "above current engine or memory limits"}",style=MaterialTheme.typography.caption)
            val saved=installed.firstOrNull{it.sha256==item.model.sha256 && it.size==item.model.bytes}
            if(saved!=null) Button(enabled=job?.isActive!=true,onClick={onLoad(saved)}){Text("Load verified model")}
            else Button(enabled=job?.isActive!=true && item.fits && reading.third>=item.storage,onClick={
                val client=HubClient();active=client
                job=scope.launch {
                    try {
                        status="Downloading ${item.model.name}"
                        val file=HubFile(item.model.fileName,item.model.bytes,item.model.sha256)
                        val model=HubModel(item.model.repo,item.model.revision,"Apache-2.0","false",listOf(file))
                        val savedModel=withContext(Dispatchers.IO){client.download(model,file,directory){read,total->status="Downloaded ${formatMemory(read)} / ${formatMemory(total)}"}}
                        withContext(Dispatchers.IO){val store=ModelStore();store.save(store.load().filterNot{it.path==savedModel.path}+savedModel)}
                        installed=installed.filterNot{it.path==savedModel.path}+savedModel;status="Download checksum verified. Choose Load to start inference."
                    } catch(c:CancellationException){status="Download stopped";throw c}
                    catch(e:Exception){status=e.message ?: "Download failed"}
                    finally{client.close();active=null;job=null}
                }
            }){Text("Download pinned GGUF")}
        } }
    }
    TextButton({expanded=!expanded}){Text(if(expanded)"Show best match" else "Compare all models")}
    if(job?.isActive==true) OutlinedButton({active?.close();job?.cancel()}){Text("Stop download")}
    if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.caption)
    Text("Optional public models; no download starts automatically. Phone and computer advice use the same revision/hash pins. Actual load, speed and answer quality still need verification.",style=MaterialTheme.typography.caption)
}
