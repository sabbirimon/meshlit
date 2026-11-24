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
import androidx.compose.ui.graphics.asImageBitmap
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
    var account by rememberSaveable{mutableStateOf(false)}
    if(account){BackHandler{account=false};com.meshlit.ui.modern.HuggingFaceDashboardScreen{account=false};return}
    val sources=listOf("On device","Hugging Face","LM Studio")
    var source by rememberSaveable { mutableStateOf("On device") }
    val focus=LocalFocusManager.current
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().testTag("phone-model-picker")) {
        TabRow(selectedTabIndex=sources.indexOf(source),containerColor=MaterialTheme.colorScheme.background) {
            sources.forEach { name -> Tab(selected=source==name,onClick={focus.clearFocus();source=name},
                text={Text(name,style=MaterialTheme.typography.titleSmall)},modifier=Modifier.heightIn(min=54.dp)) }
        }
        if(source=="On device") PhoneLocalModels(library) else key(source) {
            val catalog=remember { LmStudioCatalog() }
            val hub=remember { HuggingFaceClient() }
            val credentials=remember { ModelCredentials(context) }
            val lm=source=="LM Studio"
            var community by rememberSaveable { mutableStateOf(false) }
            var sort by rememberSaveable { mutableStateOf<String?>(null) }
            var fit by rememberSaveable { mutableStateOf(false) }
            var ggufOnly by rememberSaveable { mutableStateOf(false) }
            val capacity=remember(fit){((library.availableMemory()-256L*1024*1024).coerceAtLeast(0)/1.3).toLong()}
            val familyCatalog=lm && !community
            var query by rememberSaveable { mutableStateOf("") }
            var family by remember { mutableStateOf<CatalogFamily?>(null) }
            var variant by remember { mutableStateOf<CatalogFamily?>(null) }
            var repo by rememberSaveable { mutableStateOf<String?>(null) }
            var families by remember { mutableStateOf(emptyList<CatalogFamily>()) }
            var variants by remember { mutableStateOf(emptyList<CatalogFamily>()) }
            var repos by remember { mutableStateOf(emptyList<String>()) }
            var next by remember{mutableStateOf<String?>(null)}
            var more by remember{mutableStateOf(false)}
            val scope=rememberCoroutineScope()
            var details by remember { mutableStateOf<HubRepository?>(null) }
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var queued by remember { mutableStateOf<String?>(null) }
            var retry by remember { mutableIntStateOf(0) }
            val back:()->Unit={when {repo!=null->repo=null;variant!=null->variant=null;else->family=null}}
            val nested=repo!=null || family!=null
            BackHandler(enabled=nested,onBack=back)
            val searchKey=if(familyCatalog || nested) "" else query.trim()
            LaunchedEffect(family,variant,repo,searchKey,retry,community,sort,fit,ggufOnly) {
                loading=true;error=null;queued=null;next=null;more=false;details=null;repos=emptyList();variants=emptyList()
                try {
                    val id=repo
                    when {
                        id!=null -> details=hub.repository(id,withContext(Dispatchers.IO){credentials.token()})
                        variant!=null -> repos=catalog.repositories(checkNotNull(variant))
                        family!=null -> variants=catalog.variants(checkNotNull(family))
                        familyCatalog -> if(families.isEmpty()) families=catalog.families()
                        else -> {
                            delay(300)
                            val page=PhoneHubCatalog.page(searchKey,null,withContext(Dispatchers.IO){credentials.token()},if(community)"lmstudio-community" else null,sort,community || ggufOnly || fit,capacity.takeIf{fit});repos=page.ids;next=page.next
                        }
                    }
                } catch(cancel:CancellationException){throw cancel}
                catch(e:Exception){currentCoroutineContext().ensureActive();error=e.localizedMessage ?: "Could not load models"}
                finally {if(currentCoroutineContext().isActive) loading=false}
            }
            LazyColumn(contentPadding=PaddingValues(horizontal=22.dp,vertical=18.dp),
                modifier=Modifier.fillMaxSize().testTag("phone-catalog-results")) {
                if(source=="Hugging Face") item { TextButton(onClick={account=true}){Text(if(credentials.token().isBlank()) "Sign in or create an account" else "Hugging Face account")} }
                if(nested) item { TextButton(onClick=back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(8.dp));Text(source) } }
                item { Text(repo ?: variant?.name ?: family?.name ?: "Find a model",
                    style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(vertical=12.dp)) }
                if(!nested) item { OutlinedTextField(query,{query=it.take(150)},Modifier.fillMaxWidth().testTag("phone-catalog-search"),
                    singleLine=true,placeholder={Text("Search $source")}) }
                if(!nested && lm) item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!community,{community=false},label={Text("Catalog")})
                    FilterChip(community,{community=true},label={Text("Community GGUF")})
                } }
                if(!nested && !familyCatalog) item {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        if(!community)FilterChip(ggufOnly,{ggufOnly=!ggufOnly},label={Text(if(ggufOnly)"GGUF only" else "All formats")})
                        FilterChip(fit,{fit=!fit},label={Text("Fits this phone")})
                    }
                    if(fit)Text("Estimated GGUF memory fit · ${formatBytes(capacity)} weight budget",style=MaterialTheme.typography.bodyMedium)
                    var open by remember { mutableStateOf(false) }
                    val choices=mapOf(null to "Best match","downloads" to "Most downloads","likes" to "Most likes","lastModified" to "Recently updated")
                    Box { TextButton(onClick={open=true}){Text(choices[sort] ?: "Sort")}
                        DropdownMenu(open,{open=false}) { choices.forEach { (value,label)->
                            DropdownMenuItem(text={Text(label)},onClick={sort=value;open=false})
                        } }
                    }
                }
                if(family!=null && repo==null) item {
                    val selected=family!!
                    Text(selected.summary,style=MaterialTheme.typography.bodyLarge)
                    ModelBadges(selected.capabilities.split(" · ").filter{it.isNotBlank()})
                    ModelFacts(listOfNotNull(selected.sizes.takeIf{it.isNotBlank()}?.let{"Parameters" to it},
                        selected.downloads?.let{"Downloads" to formatCount(it)}, selected.updated.takeIf{it.isNotBlank()}?.let{"Updated" to it}))
                    SectionTitle(if(variant==null)"Variants" else "Repositories")
                }
                if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=20.dp)) }
                else if(error!=null) item { Text(checkNotNull(error),Modifier.padding(vertical=12.dp));TextButton(onClick={retry++}){Text("Retry")} }
                else if(details!=null) {
                    val model=checkNotNull(details)
                    item { RepositoryDetails(model);SectionTitle("Download options") }
                    val files=model.files
                    if(files.isEmpty()) item { Text("This repository requires another runtime. Open the publisher model card for files and setup.") }
                    if(queued!=null) item { Text(checkNotNull(queued),Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.primary) }
                    items(files,key={it.fileName}) { artifact ->
                        ModelChoice(artifact.fileName,artifact.sizeBytes?.let(::formatBytes)?.let{"Download · $it"} ?: "Download") {
                            try {library.addHubArtifact(artifact,ModelDownloadBackend.VERIFIED_HTTP);queued="Queued in On device"}
                            catch(e:Exception){error=e.message ?: "Could not queue download"}
                        }
                    }
                } else if(familyCatalog && variant==null) {
                    val rows=if(family==null) families.filter{it.name.contains(query,true)} else variants
                    if(rows.isEmpty()) item { Text("No matching models.",Modifier.padding(vertical=16.dp)) }
                    items(rows,key={it.path}) { row -> ModelChoice(row.name,listOf(row.capabilities,row.summary).filter{it.isNotBlank()}.joinToString("\n"),row.publisher) { focus.clearFocus();if(family==null)family=row else variant=row } }
                } else {
                    if(repos.isEmpty()) item { Text(if(fit && next!=null)"No estimated fits on this page. Load more to continue." else "No matching repositories.",Modifier.padding(vertical=16.dp)) }
                    items(repos,key={it}) { id -> ModelChoice(id,publisher=id.substringBefore('/')){focus.clearFocus();repo=id} }
                }
                if(next!=null && !familyCatalog && !nested)item {
                    val cursor=next
                    TextButton(enabled=!more,onClick={scope.launch {
                        more=true
                        try {val page=PhoneHubCatalog.page(searchKey,cursor,withContext(Dispatchers.IO){credentials.token()},if(community)"lmstudio-community" else null,sort,community || ggufOnly || fit,capacity.takeIf{fit})
                            if(next==cursor){repos=(repos+page.ids).distinct();next=page.next}}
                        catch(c:CancellationException){throw c}catch(e:Exception){if(next==cursor)error=e.message}
                        finally{if(next==cursor || !loading)more=false}
                    }}){Text(if(more)"Loading…" else "Load more models")}
                }
            }
        }
    }
    }
}

@Composable
private fun ModelChoice(title:String,description:String?=null,publisher:String?=null,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=onClick).padding(vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(publisher!=null) PublisherLogo(publisher)
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
            if(!description.isNullOrBlank()) Text(description,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}


@Composable
internal fun PublisherLogo(publisher:String) {
    key(publisher) {
        val bitmap by produceState<android.graphics.Bitmap?>(null,publisher){value=try{PhoneHubCatalog.logo(publisher)}catch(c:CancellationException){throw c}catch(_:Exception){null}}
            val logo=bitmap
            if(logo!=null)androidx.compose.foundation.Image(logo.asImageBitmap(),"$publisher logo",Modifier.size(32.dp))
            else Text(publisher.take(2).uppercase(),Modifier.width(34.dp),style=MaterialTheme.typography.titleSmall)
    }
}

private fun formatCount(value:Long)=java.text.NumberFormat.getIntegerInstance().format(value)

@Composable
private fun RepositoryDetails(model:HubRepository) {
    val publisher=model.id.substringBefore('/')
    val uri=androidx.compose.ui.platform.LocalUriHandler.current
    var expanded by remember(model.id){mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            PublisherLogo(publisher)
            Text(publisher,style=MaterialTheme.typography.titleMedium)
        }
        val stats=listOfNotNull(model.downloads?.let{"${formatCount(it)} downloads"}, model.likes?.let{"${formatCount(it)} likes"},
            model.updated?.take(10)?.let{"Updated $it"})
        if(stats.isNotEmpty())Text(stats.joinToString("  ·  "),style=MaterialTheme.typography.bodyMedium)
        ModelBadges(model.formats.map{it.uppercase()})
        ModelFacts(listOfNotNull(model.task?.let{"Model type" to it.replace('-', ' ')},
            model.architecture?.let{"Architecture" to it}, model.parameters?.let{"Parameters" to formatCount(it)},
            model.contextLength?.let{"Model context" to (formatCount(it)+" tokens")}, model.license?.let{"License" to it},
            "Access" to when {model.private->"Private";model.gated in listOf("manual","auto","true")->"Gated";else->"Public"}))
        val features=modelFeatures(model)
        if(features.isNotEmpty()){
            Text("Capabilities",style=MaterialTheme.typography.titleMedium)
            ModelBadges(features)
        }
        Row {
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide details" else "More details")}
            TextButton(onClick={uri.openUri("https://huggingface.co/"+model.id)}){Text("Publisher model card")}
        }
        if(expanded) androidx.compose.foundation.text.selection.SelectionContainer { Column {
            Text("Revision · ${model.revision}",style=MaterialTheme.typography.bodyMedium)
            if(model.tags.isNotEmpty())Text(model.tags.joinToString("  ·  "),style=MaterialTheme.typography.bodyMedium)
        } }
    }
}
