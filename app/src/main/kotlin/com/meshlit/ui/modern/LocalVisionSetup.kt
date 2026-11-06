package com.meshlit.ui.modern

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.media.LocalVisionHost
import kotlinx.coroutines.*

@Composable internal fun LocalVisionSetup(back:()->Unit) {
    val host=koinInject<LocalVisionHost>();val installed by host.model.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf("")}
    var model by remember{mutableStateOf<Uri?>(null)};var projector by remember{mutableStateOf<Uri?>(null)}
    var result by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    val selectModel=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){if(it!=null)model=it}
    val selectProjector=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){if(it!=null)projector=it}
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TextButton(onClick=back){Text("Back")}
        Text("Offline photo model",style=MaterialTheme.typography.headlineSmall)
        Text("Import a compatible multimodal GGUF and its matching projector GGUF. Both files stay on this device. Attach a photo in Chat and choose On-device photo. A text-only model cannot analyze photos.")
        Text("Choose a small pair your phone can fit. The model can be at most 2 GiB and the projector 512 MiB. Meshlit checks available memory before each run.",style=MaterialTheme.typography.bodySmall)
        if(!host.available())Text("The packaged native vision runtime is unavailable on this device.")
        installed?.let {pair->Text("Configured: ${pair.label} · ${formatBytes(pair.modelBytes+pair.projectorBytes)} on disk")}
        OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),label={Text("Model display name")},enabled=job==null,singleLine=true)
        OutlinedButton(enabled=job==null && host.available(),onClick={selectModel.launch("*/*")}){Text(if(model==null)"Choose model GGUF" else "Model selected · change")}
        OutlinedButton(enabled=job==null && host.available(),onClick={selectProjector.launch("*/*")}){Text(if(projector==null)"Choose projector GGUF" else "Projector selected · change")}
        Button(enabled=job==null && host.available() && name.isNotBlank() && model!=null && projector!=null,onClick={
            val main=checkNotNull(model);val accessory=checkNotNull(projector);val label=name
            job=scope.launch{
                try{result="Copying and checking GGUF files…";host.install(label,main,accessory);model=null;projector=null;result="Pair configured. Open Chat and attach a photo to try it."}
                catch(e:CancellationException){result="Import stopped";throw e}
                catch(e:Exception){result=e.message ?: "Import failed"}
                finally{job=null}
            }
        }){Text("Import offline vision pair")}
        if(job!=null)OutlinedButton(onClick={job?.cancel()}){Text("Stop import")}
        if(installed!=null && job==null)TextButton(onClick={job=scope.launch{
            try{host.clear();result="Stored vision pair removed"}catch(e:CancellationException){throw e}
            catch(e:Exception){result=e.message ?: "Could not remove the stored vision pair"}finally{job=null}
        }}){Text("Remove stored pair")}
        result?.let{Text(it)}
    }
}
