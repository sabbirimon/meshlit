package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.chat.ChatState
import com.meshlit.di.koinInject
import com.meshlit.search.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun GlobalSearchDialog(state:ChatState,onResult:(AppSearchResult,String)->Unit,onSettings:()->Unit,onClose:()->Unit) {
    val service=koinInject<AppSearchService>()
    val articles by service.articles.collectAsStateWithLifecycle()
    val library=koinInject<com.meshlit.models.ModelLibrary>()
    val models by library.models.collectAsStateWithLifecycle()
    val access by service.access.state.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf<SearchCategory?>(null)}
    var results by remember{mutableStateOf<List<AppSearchResult>>(emptyList())}
    var webRows by remember{mutableStateOf<List<WebSearchRow>>(emptyList())}
    var remoteRows by remember{mutableStateOf<List<RemoteSettingsSnapshot>>(emptyList())}
    var remoteErrors by remember{mutableStateOf<List<String>>(emptyList())}
    var remoteLoading by remember{mutableStateOf(false)}
    var remoteJob by remember{mutableStateOf<Job?>(null)}
    var webQuery by remember{mutableStateOf("")}
    var fetchedAt by remember{mutableLongStateOf(0)}
    var error by remember{mutableStateOf<String?>(null)}
    var localError by remember{mutableStateOf<String?>(null)}
    var loading by remember{mutableStateOf(false)}
    var webJob by remember{mutableStateOf<Job?>(null)}
    var reader by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope();val uri=LocalUriHandler.current
    reader?.let{ReplyReader(it){reader=null}}
    LaunchedEffect(query,state.conversations,models,articles) {
        localError=null
        try{results=service.local(query)}catch(e:CancellationException){throw e}catch(_:Exception){results=emptyList();localError="Local search index could not be read"}
    }
    // Never associate an old web result with a newly typed query or a revoked grant.
    LaunchedEffect(query,access.webEnabled){webJob?.cancel();webRows=emptyList();webQuery="";error=null;loading=false}
    DisposableEffect(Unit){onDispose{webJob?.cancel();remoteJob?.cancel()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("global-search"),color=MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight().widthIn(max=840.dp).fillMaxWidth().safeDrawingPadding()) {
                TopAppBar(title={Text("Search Meshlit")},navigationIcon={IconButton(onClick=onClose){Icon(Icons.Default.Close,"Close global search")}},
                    actions={IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"Search access settings")}})
                OutlinedTextField(query,{query=it.take(600)},Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("global-search-query"),singleLine=true,
                    label={Text("Options, chats, articles or devices")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear global search")}})
                FlowRow(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(category==null,{category=null},label={Text("All local")})
                    SearchCategory.entries.forEach{item->FilterChip(category==item,{category=item},modifier=Modifier.testTag("global-search-tab-${item.name}"),label={Text(item.label)})}
                }
                if(category==SearchCategory.DEVICES) Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Saved device access is searchable offline. Live settings reads use only your authenticated MCP routes and the exact device_settings_read allowlist. No scan or permission change occurs.",style=MaterialTheme.typography.bodySmall)
                    Row {
                        OutlinedButton(enabled=!remoteLoading,onClick={remoteJob=scope.launch{remoteLoading=true;remoteRows=emptyList();remoteErrors=emptyList();try{val (rows,errors)=service.remoteSettingsHuman();remoteRows=rows;remoteErrors=errors}
                            catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive();remoteErrors=listOf("Remote settings read timed out")}
                            catch(e:CancellationException){throw e}catch(e:Exception){remoteErrors=listOf(e.message ?: "Remote settings unavailable")}finally{remoteLoading=false}}},modifier=Modifier.testTag("global-device-refresh")){Text("Read approved device settings")}
                        if(remoteLoading) TextButton(onClick={remoteJob?.cancel()}){Text("Stop")}
                    }
                    if(remoteLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    remoteErrors.forEach{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                }
                if(category==SearchCategory.WEB) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(access.webEnabled) "Internet search on · query sent to Brave Search only when you tap Search web" else "Internet search is off. Local search remains available.",style=MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(enabled=access.webEnabled && query.isNotBlank() && !loading,onClick={
                                webJob=scope.launch{loading=true;error=null;webRows=emptyList();try{val submitted=query;val rows=service.web(submitted);ensureActive();webRows=rows;webQuery=submitted;fetchedAt=System.currentTimeMillis()}
                                    catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Search failed"}finally{loading=false}}
                            },modifier=Modifier.testTag("global-web-search")){Text("Search web")}
                            if(loading) TextButton(onClick={webJob?.cancel()}){Text("Stop")}
                            TextButton(onClick=onSettings){Text("Access and API key")}
                        }
                        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                        if(webQuery.isNotBlank()) Text("Brave Search · ${webRows.size} results · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(fetchedAt))}. Snippets are not full fetched articles.",style=MaterialTheme.typography.labelSmall)
                    }
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(webRows,key={it.url}){row->Card {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text(row.title,style=MaterialTheme.typography.titleMedium);Text(row.snippet);Text(row.url,style=MaterialTheme.typography.labelSmall)
                            Row{TextButton(onClick={runCatching{uri.openUri(row.url)}.onFailure{error="No browser can open this link"}}){Text("Open source")}
                                TextButton(onClick={scope.launch{try{service.addArticle(row.title.take(160),"# ${row.title}\n\n${row.snippet}\n\nSource: ${row.url}\n\nSearch snippet fetched ${java.util.Date(fetchedAt)}. Full page not retrieved.");error="Saved search snippet to Articles"}catch(e:Exception){if(e is CancellationException) throw e;error=e.message}}}){Text("Save snippet")}}
                        }}}
                    }
                } else {
                    val visible=results.filter{category==null || it.category==category}
                    LazyColumn(Modifier.weight(1f).testTag("global-search-results"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        item{Text(if(query.isBlank()) "Search all connected app pages and options, saved model entries, the latest 40 chats, imported UTF-8 articles and approved device access records. Web is separate." else "${visible.size} local matches · up to 200 per query",style=MaterialTheme.typography.bodySmall)
                            localError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                            if(query.isNotBlank() && visible.isEmpty()) Text("No local matches")
                            if(category==SearchCategory.ARTICLES) TextButton(onClick=onSettings){Text("Import or manage articles")}}
                        if(category==SearchCategory.DEVICES) items(remoteRows.filter{AppSearchIndex.matches(query,it.route+" "+it.fields.entries.joinToString{entry->"${entry.key}: ${entry.value}"})},key={"remote:${it.route}"}){row->Card{Column(Modifier.padding(16.dp)){
                            Text("${row.route} · device settings",style=MaterialTheme.typography.titleMedium)
                            Text("Received ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.receivedAtMs))}; read-only snapshot, not continuous monitoring",style=MaterialTheme.typography.labelSmall)
                            row.fields.forEach{(name,value)->Text("$name: $value")}
                        }}}
                        items(visible,key={it.id}){row->Card(onClick={if(row.articleId!=null) reader=articles.firstOrNull{it.id==row.articleId}?.text else onResult(row,query)}) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(row.category.label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                Text(row.title,style=MaterialTheme.typography.titleMedium);Text(row.detail,style=MaterialTheme.typography.bodyMedium)
                            }
                        }}
                    }
                }
            }
            }
        }
    }
}
