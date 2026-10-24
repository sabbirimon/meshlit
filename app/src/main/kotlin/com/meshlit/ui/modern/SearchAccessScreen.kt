package com.meshlit.ui.modern

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.search.AppSearchService
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchAccessScreen(onBack:()->Unit) {
    val service=koinInject<AppSearchService>();val scope=rememberCoroutineScope()
    val access by service.access.state.collectAsStateWithLifecycle()
    val articles by service.articles.collectAsStateWithLifecycle()
    var key by remember{mutableStateOf("")};var hasKey by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    var reader by remember{mutableStateOf<String?>(null)}
    reader?.let{ReplyReader(it){reader=null}}
    fun action(work:suspend()->Unit){scope.launch{busy=true;message=null;try{withContext(Dispatchers.IO){work()};hasKey=withContext(Dispatchers.IO){service.hasWebKey()}}
        catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Search settings could not be saved"}finally{busy=false}}}
    LaunchedEffect(service){try{withContext(Dispatchers.IO){service.ready.await()};hasKey=withContext(Dispatchers.IO){service.hasWebKey()}}catch(e:CancellationException){throw e}catch(_:Exception){message="Search content or credentials could not be read"}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) action{service.importArticle(uri);message="Article copied to local search storage"}}
    Scaffold(topBar={TopAppBar(title={Text("Search access and articles")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("search-access"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{
                Text("Local search",style=MaterialTheme.typography.titleLarge)
                Text("Human search works offline. It searches connected app pages, options, model names, saved conversations, imported articles and approved device access records. It does not scan arbitrary files, keys or credentials.")
                Row{Text("Allow agents to search local content",Modifier.weight(1f));Switch(access.agentLocal,{value->action{service.access.saveHuman(service.access.state.value.copy(agentLocal=value))}},enabled=!busy && !com.meshlit.BuildProfile.coreCandidate,modifier=Modifier.testTag("search-agent-local"))}
                Text("This grant exposes matching snippets from your saved chats and imported articles to an agent. Per-chat “Local search tools” is also required when chatting with an on-device model.")
                HorizontalDivider(Modifier.padding(vertical=12.dp))
                if(!com.meshlit.BuildProfile.coreCandidate) {
                Text("Internet search",style=MaterialTheme.typography.titleLarge)
                Row{Text("Internet search",Modifier.weight(1f));Switch(access.webEnabled,{value->action{service.access.saveHuman(service.access.state.value.copy(webEnabled=value))}},enabled=!busy,modifier=Modifier.testTag("search-web-access"))}
                Text("Off by default. Queries go to api.search.brave.com when you tap Search web or an authorized agent invokes web_search. Your whole chat is not uploaded by this adapter. Results contain source URLs and snippets; opening a page or using the separate crawler may send data to that host.")
                Row{Text("Allow agents to use Internet search",Modifier.weight(1f));Switch(access.agentWeb,{value->action{service.access.saveHuman(service.access.state.value.copy(agentWeb=value))}},enabled=!busy && access.webEnabled,modifier=Modifier.testTag("search-agent-web"))}
                Text("Requires your Internet switch, this agent grant and each chat's web tools grant. Agents can pause/resume their search through search_access only within these saved grants; they cannot grant access or change the API key.")
                if(access.agentWebPaused) {Text("Agent Internet search is paused");TextButton(enabled=!busy,onClick={action{service.access.saveHuman(service.access.state.value.copy(agentWebPaused=false))}}){Text("Resume within current grants")}}
                Text("Turning Internet or agent access off cancels matching pending API requests. Existing results do not imply permission for a new request.")
                OutlinedTextField(key,{key=it.take(512)},Modifier.fillMaxWidth().testTag("search-api-key"),label={Text("Brave Search API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,supportingText={Text(if(hasKey) "A key is stored; leave blank to retain it" else "No key stored")})
                Row{Button(enabled=!busy && key.isNotBlank(),onClick={action{service.saveKeyHuman(key);key="";message="Key stored in Android encrypted storage"}}){Text("Save API key")}
                    TextButton(enabled=!busy && hasKey,onClick={action{service.deleteKeyHuman();message="API key deleted"}}){Text("Delete key")}}
                Text("A provider account/API key is required and may incur charges. No account is created or bundled. Keys are encrypted on this device, used only with the fixed Brave API origin and excluded from search results and exports.")
                HorizontalDivider(Modifier.padding(vertical=12.dp))
                } else Text("Core candidate uses offline human search. Internet and agent tools remain in Experimental.")
                Text("Articles",style=MaterialTheme.typography.titleLarge)
                Text("Import up to 20 UTF-8 text or Markdown articles, at most 128 KiB each and 4 MiB for the encoded index. Files are copied locally; no automatic storage scan, PDF parser or web fetch. Retrieved and imported content is untrusted evidence.")
                OutlinedButton(enabled=!busy && articles.size<20,onClick={picker.launch(arrayOf("text/plain","text/markdown"))}){Text("Import article")}
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let{Text(it)}
            }
            items(articles,key={it.id}){article->Card{Column(Modifier.fillMaxWidth().padding(16.dp)){
                Text(article.title,style=MaterialTheme.typography.titleMedium)
                Text("Imported ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(article.importedAtMs))}",style=MaterialTheme.typography.labelSmall)
                Row{TextButton(onClick={reader=article.text}){Text("Read article")};TextButton(enabled=!busy,onClick={action{service.removeArticle(article.id)}}){Text("Remove from search")}}
            }}}
        }
    }
}
