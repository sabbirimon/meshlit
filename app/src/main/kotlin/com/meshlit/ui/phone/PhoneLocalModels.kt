package com.meshlit.ui.phone

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.meshlit.core.inference.models.HuggingFaceClient
import com.meshlit.core.inference.models.HubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.models.*
import com.meshlit.core.inference.*
import com.meshlit.di.koinInject
import com.meshlit.ui.modern.ModernModelsScreen
import com.meshlit.ui.modern.formatBytes
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable internal fun PhoneLocalModels(library:ModelLibrary) {
    val models by library.models.collectAsStateWithLifecycle()
    val coordinator=koinInject<InferenceCoordinator>()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val loaded=(runtime as? CoordinatorState.Ready)?.model?.modelPath
    val scope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    var advanced by remember{mutableStateOf(false)}
    var offerUrl by remember{mutableStateOf(false)}
    var url by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty())runCatching {
            uris.forEach{runCatching{context.contentResolver.takePersistableUriPermission(it,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
            library.importFiles(uris)
        }.onFailure{error=it.message}
    }
    if(advanced){BackHandler{advanced=false};ModernModelsScreen(onBack={advanced=false});return}
    if(offerUrl)AlertDialog(onDismissRequest={offerUrl=false},title={Text("Import from URL")},
        text={OutlinedTextField(url,{url=it},label={Text("HTTPS model URL")},singleLine=true)},
        confirmButton={TextButton(onClick={runCatching{library.addUrl(url.trim())}.onSuccess{offerUrl=false;url=""}.onFailure{error=it.message}}){Text("Download")}},
        dismissButton={TextButton(onClick={offerUrl=false}){Text("Cancel")}})
    val installed=models.filter{it.installed || it.bundled || it.active || it.phase=="Paused"}
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = installed.find { it.id == choice } ?: installed.find { it.path.isNotBlank() && it.path==loaded } ?: installed.firstOrNull()
    val hub = remember { HuggingFaceClient() }
    val credentials = remember { ModelCredentials(context) }
    var choose by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Model library",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                Box {
                    OutlinedButton(onClick={importMenu=true}) { Text("Import") }
                    DropdownMenu(expanded=importMenu,onDismissRequest={importMenu=false}) {
                        DropdownMenuItem(text={Text("Choose files")},onClick={importMenu=false;picker.launch(arrayOf("*/*"))})
                        DropdownMenuItem(text={Text("Paste a link")},onClick={importMenu=false;offerUrl=true})
                    }
                }
            }
        }
        if(installed.size>1) item {
            Box {
                OutlinedButton(onClick={choose=true}){Text("Choose model · "+installed.size)}
                DropdownMenu(expanded=choose,onDismissRequest={choose=false}) {
                    installed.forEach { entry -> DropdownMenuItem(text={Text(entry.name)},onClick={choice=entry.id;choose=false}) }
                }
            }
        }
        if(selected==null) item { Text("Import a file or select a model in Hugging Face or LM Studio.") }
        else item(key=selected.id) {
            val model=selected
            val repoId=remember(model.url) {
                val link=android.net.Uri.parse(model.url)
                link.pathSegments.takeIf { link.scheme=="https" && link.host=="huggingface.co" &&
                    it.size>=3 && it[2] in listOf("resolve","blob") }?.take(2)?.joinToString("/")
            }
            val repo by produceState<HubRepository?>(null,repoId) {
                value=repoId?.let {
                    try {hub.repository(it,withContext(Dispatchers.IO){credentials.token()})}
                    catch(c:CancellationException){throw c}catch(_:Exception){null}
                }
            }
            val isLoaded=model.path.isNotBlank() && model.path==loaded
            val action=when {model.active->"Pause";isLoaded->"Unload";model.installed->"Load";else->"Download"}
            InstalledModelDetails(model,repo,repoId,if(isLoaded)(runtime as? CoordinatorState.Ready)?.model?.contextSize else null,
                action,loading==null) {
                scope.launch {
                    loading=model.id
                    try {when(action){"Load"->library.load(model.id);"Unload"->coordinator.unloadModel();"Pause"->library.pause(model.id);else->library.download(model.id)}}
                    catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}finally{loading=null}
                }
            }
        }
        item{HorizontalDivider();DestinationRow("configuration","Storage and runtime","Manage model files and runtime options."){advanced=true}}
    }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Model library")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("Close")}})}
}
