package com.meshlit.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.core.inference.models.HubRepository
import com.meshlit.core.inference.models.HuggingFaceClient
import com.meshlit.models.*
import com.meshlit.di.koinInject
import com.meshlit.ui.modern.ModernModelsScreen
import com.meshlit.ui.modern.formatBytes
import kotlinx.coroutines.*

@Composable
internal fun PhoneModelPicker() {
    val context=LocalContext.current
    val library=koinInject<ModelLibrary>()
    val sources=listOf("On device","Hugging Face","LM Studio")
    var source by rememberSaveable { mutableStateOf("On device") }
    val focus=LocalFocusManager.current
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().testTag("phone-model-picker")) {
        TabRow(selectedTabIndex=sources.indexOf(source),containerColor=MaterialTheme.colorScheme.background) {
            sources.forEach { name -> Tab(selected=source==name,onClick={focus.clearFocus();source=name},
                text={Text(name,style=MaterialTheme.typography.titleSmall)},modifier=Modifier.heightIn(min=54.dp)) }
        }
        if(source=="On device") ModernModelsScreen() else key(source) {
            val catalog=remember { LmStudioCatalog() }
            val hub=remember { HuggingFaceClient() }
            val credentials=remember { ModelCredentials(context) }
            val lm=source=="LM Studio"
            var query by rememberSaveable { mutableStateOf("") }
            var family by remember { mutableStateOf<CatalogFamily?>(null) }
            var variant by remember { mutableStateOf<CatalogFamily?>(null) }
            var repo by rememberSaveable { mutableStateOf<String?>(null) }
            var families by remember { mutableStateOf(emptyList<CatalogFamily>()) }
            var variants by remember { mutableStateOf(emptyList<CatalogFamily>()) }
            var repos by remember { mutableStateOf(emptyList<String>()) }
            var details by remember { mutableStateOf<HubRepository?>(null) }
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var queued by remember { mutableStateOf<String?>(null) }
            var retry by remember { mutableIntStateOf(0) }
            val back:()->Unit={when {repo!=null->repo=null;variant!=null->variant=null;else->family=null}}
            val nested=repo!=null || family!=null
            BackHandler(enabled=nested,onBack=back)
            val searchKey=if(lm || nested) "" else query.trim()
            LaunchedEffect(family,variant,repo,searchKey,retry) {
                loading=true;error=null;queued=null;details=null;repos=emptyList();variants=emptyList()
                try {
                    val id=repo
                    when {
                        id!=null -> details=hub.repository(id,withContext(Dispatchers.IO){credentials.token()})
                        variant!=null -> repos=catalog.repositories(checkNotNull(variant))
                        family!=null -> variants=catalog.variants(checkNotNull(family))
                        lm -> if(families.isEmpty()) families=catalog.families()
                        else -> {
                            delay(300)
                            repos=hub.search(searchKey,withContext(Dispatchers.IO){credentials.token()})
                        }
                    }
                } catch(cancel:CancellationException){throw cancel}
                catch(e:Exception){currentCoroutineContext().ensureActive();error=e.localizedMessage ?: "Could not load models"}
                finally {if(currentCoroutineContext().isActive) loading=false}
            }
            LazyColumn(contentPadding=PaddingValues(horizontal=22.dp,vertical=18.dp),
                modifier=Modifier.fillMaxSize().testTag("phone-catalog-results")) {
                if(nested) item { TextButton(onClick=back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(8.dp));Text(source) } }
                item { Text(repo ?: variant?.name ?: family?.name ?: "Find a model",
                    style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(vertical=12.dp)) }
                if(!nested) item { OutlinedTextField(query,{query=it.take(150)},Modifier.fillMaxWidth().testTag("phone-catalog-search"),
                    singleLine=true,placeholder={Text("Search $source")}) }
                if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=20.dp)) }
                else if(error!=null) item { Text(checkNotNull(error),Modifier.padding(vertical=12.dp));TextButton(onClick={retry++}){Text("Retry")} }
                else if(details!=null) {
                    val files=checkNotNull(details).files
                    if(files.isEmpty()) item { Text("This repository has no single-file GGUF for the phone runtime.") }
                    if(queued!=null) item { Text(checkNotNull(queued),Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.primary) }
                    items(files,key={it.fileName}) { artifact ->
                        ModelChoice(artifact.fileName,artifact.sizeBytes?.let(::formatBytes)?.let{"Download · $it"} ?: "Download") {
                            try {library.addHubArtifact(artifact,ModelDownloadBackend.VERIFIED_HTTP);queued="Queued in On device"}
                            catch(e:Exception){error=e.message ?: "Could not queue download"}
                        }
                    }
                } else if(lm && variant==null) {
                    val rows=if(family==null) families.filter{it.name.contains(query,true)} else variants
                    if(rows.isEmpty()) item { Text("No matching models.",Modifier.padding(vertical=16.dp)) }
                    items(rows,key={it.path}) { row -> ModelChoice(row.name) { focus.clearFocus();if(family==null)family=row else variant=row } }
                } else {
                    if(repos.isEmpty()) item { Text("No matching GGUF repositories.",Modifier.padding(vertical=16.dp)) }
                    items(repos,key={it}) { id -> ModelChoice(id){focus.clearFocus();repo=id} }
                }
            }
        }
    }
    }
}

@Composable
private fun ModelChoice(title:String,description:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=onClick).padding(vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
            if(description!=null) Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
