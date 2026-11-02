package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.agent.*
import com.meshlit.models.ModelLibrary
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.di.koinInject
import com.meshlit.workspace.agents.*
import kotlinx.coroutines.*
@Composable internal fun AgencyAgentCatalogScreen(onBack:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val store=remember{AgentProfileStore(context)}
    val library=koinInject<ModelLibrary>();val inference=koinInject<InferenceCoordinator>()
    var catalog by remember{mutableStateOf<AgencyCatalog?>(null)};var query by remember{mutableStateOf("")};var message by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<AgencyRole?>(null)};var instruction by remember{mutableStateOf("")}
    LaunchedEffect(Unit){try{catalog=withContext(Dispatchers.IO){context.assets.open("agency/catalog.json").bufferedReader().use{AgencyRoles.parse(it.readText())}}}catch(e:Exception){if(e is CancellationException)throw e;message="Role catalog could not load"}}
    selected?.let{role->AlertDialog(onDismissRequest={selected=null},title={Text(role.name)},text={Column{Text("Editable role guidance. Saving creates a disabled template; tools and device grants remain separate.");OutlinedTextField(instruction,{if(it.length<=1200)instruction=it},label={Text("Instructions · up to 1200 characters")})}},confirmButton={TextButton(onClick={scope.launch{try{
        val model=library.models.value.firstOrNull{it.installed && it.path==inference.loadedModel()?.modelPath} ?: error("Load a local model before saving a role")
        withContext(Dispatchers.IO){val current=store.load();store.save(current+AgentProfile(name=role.name.take(48),modelId=model.id,instruction=instruction,enabled=false))}
        message="Saved disabled role template. Enable and run it from Agent management.";selected=null
    }catch(e:Exception){if(e is CancellationException)throw e;message=e.message}}}){Text("Save role template")}},dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back")};Text("Agent role library",style=MaterialTheme.typography.headlineSmall)
            Text("${catalog?.roles?.size ?: 0} role templates adapted from Agency Agents · MIT. Templates describe behavior, not verified professional qualifications. No tool or device authority is imported.")
            OutlinedTextField(query,{query=it},label={Text("Search role, division or specialty")},modifier=Modifier.fillMaxWidth());message?.let{Text(it)}
        }
        items(catalog?.roles.orEmpty().filter{query.isBlank() || "${it.name} ${it.division} ${it.description}".contains(query,true)}){role->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(role.name,style=MaterialTheme.typography.titleMedium);Text(role.division);Text(role.description);TextButton(onClick={selected=role;instruction=AgencyRoles.boundedInstruction(role).take(1200)}){Text("Adapt and save")}}}}
        item{Text("Source commit ${catalog?.commit ?: "loading"}. Bounded editable excerpts and the license are bundled with source paths and hashes; full source documents remain in the pinned upstream repository.")}
    }
}
