package com.meshlit.ui.modern
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.core.inference.models.*
import com.meshlit.routing.ModelRoutes
import com.meshlit.models.ModelLibrary
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ModelRouterScreen(onBack:()->Unit) {
    val router=koinInject<ModelRoutes>();val routes by router.routes.collectAsStateWithLifecycle();val state by router.state.collectAsStateWithLifecycle()
    val library=koinInject<ModelLibrary>();val models by library.models.collectAsStateWithLifecycle()
    val providers=koinInject<OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();var edit by remember{mutableStateOf<ModelRoute?>(null)}
    var error by remember{mutableStateOf<String?>(null)};var prompt by remember{mutableStateOf("")}
    var scenario by remember{mutableStateOf("general")};var selected by remember{mutableStateOf<String?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    BackHandler{onBack()};DisposableEffect(Unit){onDispose{job?.cancel()}}
    Scaffold(topBar={TopAppBar(title={Text("Model router")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Choose a model for each scenario, chain models, or compare their answers. A route can use up to three steps. This phone has one active local text-model session: local steps swap models sequentially, so they are not resident together. Cloud steps send data to configured providers and may cost money.")
            Button(enabled=!state.running,onClick={edit=ModelRoute(UUID.randomUUID().toString(),"New route",steps=listOf(ModelRouteStep("")))}){Text("Add route")}
            routes.forEach{route->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                Text("${route.name} · ${route.scenario} · ${route.mode}");Text(route.steps.joinToString(" → "){it.modelId})
                Text(if(route.enabled) "Enabled · priority ${route.priority}" else "Disabled")
                Row{TextButton(enabled=!state.running,onClick={edit=route}){Text("Edit")};TextButton(enabled=!state.running,onClick={router.remove(route.id)}){Text("Delete")}}
            }}}
            Text("Run a text task",style=MaterialTheme.typography.titleMedium)
            FilterChip(selected==null,{selected=null},label={Text("Automatic rules")})
            routes.filter{it.enabled}.forEach{route->FilterChip(selected==route.id,{selected=route.id},label={Text(route.name)})}
            OutlinedTextField(scenario,{scenario=it.lowercase()},label={Text("Scenario: general, coding, reasoning…")},singleLine=true)
            OutlinedTextField(prompt,{prompt=it},label={Text("Task")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)
            Row{Button(enabled=!state.running && prompt.isNotBlank(),onClick={job=scope.launch{
                error=null;try {router.execute(selected,scenario,prompt)}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}
            }}){Text("Run")};TextButton(enabled=state.running,onClick={job?.cancel()}){Text("Stop")}}
            if(state.running) Text("${state.routeId} · step ${(state.step ?: 0)+1} · ${state.modelId ?: "preflight"}")
            state.result?.let{result->
                Text(if(result.success) "Completed ${result.steps.size} steps" else "Failed at step ${(result.failedStep ?: 0)+1}: ${result.error}")
                result.steps.forEach{step->Text("${step.modelId} · ${step.durationMs} ms · input ${step.reply.inputTokens ?: "unknown"}, output ${step.reply.outputTokens ?: "unknown"} tokens");Text(step.reply.text)}
            }
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Rules use exact scenario and optional keyword matches, then priority. No hidden provider fallback. Image/audio/video adapters have separate capability gates.")
        }
    }
    edit?.let{initial->
        var route by remember(initial.id){mutableStateOf(initial)}
        var keywords by remember(initial.id){mutableStateOf(initial.keywords.joinToString(","))}
        var problem by remember(initial.id){mutableStateOf<String?>(null)}
        AlertDialog(onDismissRequest={edit=null},title={Text("Route configuration")},text={Column(Modifier.verticalScroll(rememberScrollState())){
            OutlinedTextField(route.name,{route=route.copy(name=it)},label={Text("Name")})
            OutlinedTextField(route.scenario,{route=route.copy(scenario=it.lowercase())},label={Text("Scenario")})
            OutlinedTextField(keywords,{keywords=it},label={Text("Optional keywords, comma separated")})
            Text("Priority ${route.priority}");Slider(route.priority.toFloat(),{route=route.copy(priority=it.toInt())},valueRange=-100f..100f)
            Row{Text("Enabled",Modifier.weight(1f));Switch(route.enabled,{route=route.copy(enabled=it)})}
            Row{Text("Allow delegated agents",Modifier.weight(1f));Switch(route.agentAllowed,{route=route.copy(agentAllowed=it)})}
            ModelRouteMode.entries.forEach{mode->FilterChip(route.mode==mode,{route=route.copy(mode=mode,steps=if(mode==ModelRouteMode.SINGLE) route.steps.take(1) else route.steps)},label={Text(mode.name)})}
            route.steps.forEachIndexed{index,step->
                Text("Step ${index+1}")
                val choices=models.filter{it.installed}.map{it.id to it.name}+profiles.filter{it.enabled}.map{"cloud:${it.id}" to "Online · ${it.name} / ${it.model}"}
                choices.forEach{(id,name)->FilterChip(step.modelId==id,{route=route.copy(steps=route.steps.mapIndexed{i,s->if(i==index) s.copy(modelId=id) else s})},label={Text(name)})}
                if(choices.none{it.first==step.modelId} && step.modelId.isNotBlank()) Text("Unavailable: ${step.modelId}")
                OutlinedTextField(step.instructions,{value->route=route.copy(steps=route.steps.mapIndexed{i,s->if(i==index) s.copy(instructions=value) else s})},label={Text("Step instructions")},maxLines=3)
                if(index>0) TextButton(onClick={route=route.copy(steps=route.steps.filterIndexed{i,_->i!=index})}){Text("Remove step")}
            }
            if(route.mode!=ModelRouteMode.SINGLE && route.steps.size<3) TextButton(onClick={route=route.copy(steps=route.steps+ModelRouteStep(""))}){Text("Add model step")}
            problem?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton(onClick={try{router.save(route.copy(keywords=keywords.split(',').map{it.trim()}.filter{it.isNotBlank()}));edit=null}catch(e:Exception){problem=e.message}}){Text("Save")}},dismissButton={TextButton(onClick={edit=null}){Text("Cancel")}})
    }
}
