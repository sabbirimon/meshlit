package com.meshlit.ui.modern

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.meshlit.ui.modern.workspaceStringResource as stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.R
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.di.koinInject
import com.meshlit.models.*
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernModelsScreen(onBack:(()->Unit)?=null) {
    var details by remember{mutableStateOf<LibraryModel?>(null)}
    details?.let{ModelCapabilitiesDialog(it,it.name){details=null}}
    val context=LocalContext.current
    val library=koinInject<ModelLibrary>()
    val coordinator=koinInject<InferenceCoordinator>()
    val entries by library.models.collectAsStateWithLifecycle()
    val startupEnabled by library.startupEnabled.collectAsStateWithLifecycle()
    val startupId by library.startupId.collectAsStateWithLifecycle()
    val startupStatus by library.startupStatus.collectAsStateWithLifecycle()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var showDeviceDetails by remember { mutableStateOf(false) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var addUrl by remember { mutableStateOf(false) }
    var downloadBackend by remember { mutableStateOf(ModelDownloadBackend.RUNANYWHERE) }
    var auth by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var tuning by remember { mutableStateOf<LibraryModel?>(null) }
    var quantFilter by remember { mutableStateOf("All") }
    var pendingDelete by remember { mutableStateOf<LibraryModel?>(null) }
    var loadingId by remember { mutableStateOf<String?>(null) }
    var memory by remember { mutableLongStateOf(-1L) }
    var storage by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(library) { library.ready.await();withContext(Dispatchers.IO) { memory=library.availableMemory();storage=library.freeStorage() } }
    val requestNotifications=com.meshlit.permissions.rememberNotificationRequest()
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) try { uris.forEach{uri -> runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}};library.importFiles(uris) } catch(e:Exception) { message=e.message }
    }
    val loadedPath=when(val s=runtime) { is CoordinatorState.Ready -> s.model.modelPath;else -> null }
    Scaffold(topBar={ if(onBack!=null) TopAppBar(title={ Text(stringResource(R.string.modern_models)) },navigationIcon={
        IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.modern_back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax).testTag("models-list"),
            contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)) {
            item {
                StatusHeroCard("Your model library", "${entries.count { it.installed }} installed · ${entries.count { it.active }} active transfers") {
                    TextButton(onClick = { filter = 2 }) { Text("View installed models") }
                }
                Spacer(Modifier.height(T.small))
                Text(stringResource(R.string.modern_resources,formatBytes(memory),formatBytes(storage)),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { ModelRecommendationPanel(library,onError={message=it}) }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                    OutlinedButton(onClick={ importer.launch(arrayOf("*/*")) },modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.FileOpen,null);Spacer(Modifier.width(T.small));Text(stringResource(R.string.modern_import)) }
                    Button(onClick={ addUrl=true },modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.Add,null);Spacer(Modifier.width(T.small));Text(stringResource(R.string.modern_add_url)) }
                }
                TextButton(onClick={showDownloadOptions=!showDownloadOptions}){Text(if(showDownloadOptions) "Hide download options" else "Download options and access token")}
                if(showDownloadOptions) {
                Text("Download method",style=MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                    FilterChip(downloadBackend==ModelDownloadBackend.RUNANYWHERE,{downloadBackend=ModelDownloadBackend.RUNANYWHERE},label={Text("RunAnywhere")})
                    FilterChip(downloadBackend==ModelDownloadBackend.VERIFIED_HTTP,{downloadBackend=ModelDownloadBackend.VERIFIED_HTTP},label={Text("HTTPS / token")})
                }
                Text("Both verify real GGUF files. Use HTTPS / token for private Hugging Face repositories. SDK partial downloads use its own resume policy.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={ auth=true }) { Text(stringResource(R.string.modern_hf_token)) }
                }
            }
            item {HuggingFacePanel(library)}
            item {
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,
                    placeholder={Text(stringResource(R.string.modern_search_models))},leadingIcon={Icon(Icons.Default.Search,null)},shape=MaterialTheme.shapes.large)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                    listOf(R.string.modern_all,R.string.modern_recommended,R.string.modern_installed).forEachIndexed { i,label ->
                        FilterChip(filter==i,{filter=i},label={Text(stringResource(label))}) }
                }
            }
            if(message!=null) item { ErrorCard(message!!) { message=null } }
            if(runtime is CoordinatorState.Loading) item { Column {
                LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.modern_loading),style=MaterialTheme.typography.bodySmall) } }
            if(runtime is CoordinatorState.Error) item { ErrorCard((runtime as CoordinatorState.Error).message) { message=null } }
            item { Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                listOf("All","Q4","Q8","Other").forEach { quant -> FilterChip(quantFilter==quant,{quantFilter=quant},label={Text(quant)}) }
            } }
            val displayed=entries.filter { e ->
                val quant=e.metadata?.quantization ?: e.name.uppercase()
                val matchesQuant=when(quantFilter){"Q4"->quant.contains("Q4");"Q8"->quant.contains("Q8");"Other"->!quant.contains("Q4") && !quant.contains("Q8");else->true}
                matchesQuant && e.name.contains(search,true) && when(filter) {
                1 -> library.recommend(e)
                2 -> e.installed
                else -> true
            } }
            if(displayed.isEmpty()) item { Text(stringResource(R.string.modern_models_empty),Modifier.padding(vertical=T.section)) }
            items(displayed,key={it.id}) { entry ->
                val loaded=loadedPath==entry.path && entry.path.isNotBlank()
                Card(Modifier.fillMaxWidth().testTag("model-row-${entry.id}"),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                Text("${entry.source} · GGUF · ${formatBytes(entry.sizeBytes)}",style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(loaded) AssistChip({},label={Text(stringResource(R.string.modern_loaded))})
                        }
                        TextButton(onClick={details=entry}){Text("Model details and capabilities")}
                        entry.metadata?.let { metadata ->
                            Text("Weights: ${metadata.quantization ?: "Unknown"} · Training context: ${metadata.maxContext?.toString() ?: "Unknown"} tokens",style=MaterialTheme.typography.bodySmall)
                        }
                        if(entry.installed) {
                            TextButton(onClick={tuning=entry}) { Text("Context and KV cache") }
                            Text(if(entry.runtimeOptions.backend==LocalModelBackend.NATIVE_LOCAL)
                                "Native local · ${entry.runtimeOptions.contextSize} tokens · K ${entry.runtimeOptions.keyCacheType} / V f16"
                                else "RunAnywhere · context and KV precision managed by the SDK",style=MaterialTheme.typography.bodySmall)
                        }
                        if(entry.active) {
                            if(entry.total>0) LinearProgressIndicator(progress={ (entry.bytes.toDouble()/entry.total).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("${entry.phase} · ${formatBytes(entry.bytes)} / ${formatBytes(entry.total)} · ${formatBytes(entry.speed)}/s",
                                style=MaterialTheme.typography.bodySmall)
                        } else Text(if(loaded) stringResource(R.string.modern_loaded) else entry.phase,style=MaterialTheme.typography.labelMedium)
                        if(entry.error!=null) Text(entry.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        if(!entry.installed && entry.sizeBytes>storage && storage>0) Text(stringResource(R.string.modern_disk_warning),color=MaterialTheme.colorScheme.error)
                        if(entry.sizeBytes>0 && memory>0 && entry.sizeBytes*1.3+256L*1024*1024>=memory)
                            Text(stringResource(R.string.modern_memory_warning),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(entry.installed) TextButton(onClick={library.setStartupModel(entry.id)}){Text(if(startupId==entry.id) "Selected startup model" else "Use at startup")}
                        Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                            when {
                                entry.active -> OutlinedButton(onClick={ library.pause(entry.id) }) { Text(stringResource(R.string.modern_pause)) }
                                loaded -> OutlinedButton(onClick={ scope.launch { runCatching { coordinator.unloadModel() }.onFailure { message=it.message } } }) {
                                    Text(stringResource(R.string.modern_unload)) }
                                entry.installed -> Button(enabled=loadingId==null && runtime !is CoordinatorState.Starting,onClick={
                                    loadingId=entry.id;scope.launch { try { library.load(entry.id) } catch(e:Exception) { message=e.message } finally { loadingId=null } }
                                }) { Text(stringResource(R.string.modern_load)) }
                                entry.url.isNotEmpty() -> Button(enabled=storage<=0 || entry.sizeBytes+64L*1024*1024<storage,
                                    onClick={requestNotifications{library.download(entry.id,if(entry.phase=="Paused") ModelDownloadBackend.valueOf(entry.downloadBackend) else downloadBackend)}}) { Text(stringResource(if(entry.phase=="Paused") R.string.modern_resume else R.string.modern_download)) }
                            }
                            if(!entry.bundled && !entry.active && (entry.installed || entry.phase in setOf("Paused","Failed")))
                                IconButton(onClick={pendingDelete=entry}) { Icon(Icons.Default.Delete,stringResource(R.string.modern_delete)) }
                        }
                    }
                }
            }
            item {TextButton(onClick={showDeviceDetails=!showDeviceDetails}){Text(if(showDeviceDetails) "Hide device and startup settings" else "Device and startup settings")}}
            if(showDeviceDetails) {
            item {DeviceRuntimeCard(library)}
            item {Card{Column(Modifier.padding(T.large)){
                Row(verticalAlignment=Alignment.CenterVertically){Text("Load model after app startup",Modifier.weight(1f));Switch(startupEnabled,library::setStartupEnabled)}
                Text(startupStatus,style=MaterialTheme.typography.bodySmall)
                Text("Uses your selected model, last successful load, or the bundled starter. No automatic downloads.",style=MaterialTheme.typography.bodySmall)
            }}}
            }
            item { Text(stringResource(R.string.modern_model_disclaimer),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if(addUrl) AlertDialog(onDismissRequest={addUrl=false},title={Text(stringResource(R.string.modern_add_url))},
        text={Column { Text(stringResource(R.string.modern_url_help));OutlinedTextField(url,{url=it},label={Text("HTTPS URL")},singleLine=false) }},
        confirmButton={TextButton(onClick={try {library.addUrl(url.trim());addUrl=false;url=""}catch(e:Exception){message=e.message;addUrl=false}}) {Text(stringResource(R.string.modern_download))}},
        dismissButton={TextButton(onClick={addUrl=false}) {Text(stringResource(R.string.modern_cancel))}})
    if(auth) AlertDialog(onDismissRequest={auth=false;token=""},title={Text(stringResource(R.string.modern_hf_token))},
        text={Column {Text(stringResource(R.string.modern_token_help));OutlinedTextField(token,{token=it},visualTransformation=PasswordVisualTransformation(),singleLine=true)}},
        confirmButton={TextButton(onClick={scope.launch {try {withContext(Dispatchers.IO){ModelCredentials(context).saveToken(token.trim())};auth=false;token=""}catch(e:Exception){message=e.message;auth=false}}}) {Text(stringResource(R.string.modern_save))}},
        dismissButton={TextButton(onClick={auth=false;token=""}) {Text(stringResource(R.string.modern_cancel))}})
    tuning?.let { entry -> ModelTuningDialog(entry,library.nativeRuntimeAvailable(),library.devicePlan(),onDismiss={tuning=null}) { options ->
        scope.launch { try {library.setRuntimeOptions(entry.id,options);message="Saved. Reload the model to apply these settings.";tuning=null}
            catch(e:CancellationException){throw e}catch(e:Exception){message=e.message;tuning=null} }
    } }
    pendingDelete?.let { entry -> AlertDialog(onDismissRequest={pendingDelete=null},title={Text(stringResource(R.string.modern_delete))},
        text={Text(stringResource(R.string.modern_delete_confirm,entry.name))},confirmButton={TextButton(onClick={pendingDelete=null;scope.launch {runCatching {library.delete(entry.id)}.onFailure {message=it.message}}}) {Text(stringResource(R.string.modern_delete))}},
        dismissButton={TextButton(onClick={pendingDelete=null}) {Text(stringResource(R.string.modern_cancel))}}) }
}

fun formatBytes(value:Long):String=when { value<0 -> "?";value>=1024L*1024*1024 -> "%.1f GB".format(value/1073741824.0);else -> "%.1f MB".format(value/1048576.0) }
@Composable internal fun ErrorCard(message:String,onDismiss:()->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(T.medium),verticalAlignment=Alignment.CenterVertically) {
            Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick=onDismiss) {Icon(Icons.Default.Close,stringResource(R.string.modern_dismiss))}
        }
    }
}

@Composable private fun ModelTuningDialog(model:LibraryModel,nativeAvailable:Boolean,devicePlan:DeviceRuntimePlan,onDismiss:()->Unit,onSave:(ModelRuntimeOptions)->Unit) {
    var options by remember(model.id){mutableStateOf(model.runtimeOptions)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Context and KV cache")},text={
        androidx.compose.foundation.rememberScrollState().let { scroll ->
            Column(Modifier.heightIn(max=480.dp).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(T.small)) {
                Text(model.name)
                Text("Settings apply on the next load. Larger contexts use more memory; they do not improve the weights' quality.")
                Row { FilterChip(options.backend==LocalModelBackend.RUNANYWHERE,{options=ModelRuntimeOptions()},label={Text("RunAnywhere")}) }
                Row { FilterChip(options.backend==LocalModelBackend.NATIVE_LOCAL,{options=options.copy(backend=LocalModelBackend.NATIVE_LOCAL,contextSize=minOf(devicePlan.recommendedContext,model.metadata?.maxContext ?: devicePlan.recommendedContext))},enabled=nativeAvailable,label={Text("Native local")}) }
                if(!nativeAvailable) Text("Native local runtime is not installed for this device ABI.")
                if(options.backend==LocalModelBackend.RUNANYWHERE) Text("The pinned SDK exposes neither native context sizing nor KV precision. This app does not report an invented capacity.")
                else {
                    Text("Context tokens · model limit ${model.metadata?.maxContext ?: "unknown"}")
                    listOf(512,1024,2048,4096,8192).filter{it<=devicePlan.maxContext}.filter{model.metadata?.maxContext?.let{limit -> it<=limit} ?: true}.forEach { tokens ->
                        Row {RadioButton(options.contextSize==tokens,{options=options.copy(contextSize=tokens)});Text(tokens.toString(),Modifier.padding(top=T.medium))}
                    }
                    Text("Key cache precision (value cache stays f16)")
                    Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {listOf("f16","q8_0","q4_0").forEach { type ->
                        FilterChip(options.keyCacheType==type,{options=options.copy(keyCacheType=type)},label={Text(type)})
                    }}
                    Text("Estimated KV payload: ${model.metadata?.kvBytes(options.contextSize,options.keyCacheType)?.let(::formatBytes) ?: "Unavailable"}. Excludes native padding, weights and compute buffers.",style=MaterialTheme.typography.bodySmall)
                    Text("Quantized key caches may reduce quality. The native runtime may reject an unsupported model/cache combination; errors are surfaced without a silent precision fallback.",style=MaterialTheme.typography.bodySmall)
                }
                Text("KV cache belongs to the loaded session. Unload to clear it. Prefix reuse, disk snapshots and cache replication across phones are not enabled.",style=MaterialTheme.typography.bodySmall)
            }
        }
    },confirmButton={TextButton(onClick={onSave(options)}){Text("Save for next load")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
