package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.control.*
import com.meshlit.di.koinInject
import com.meshlit.models.ModelLibrary
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun TaskManagerScreen(onBack:()->Unit,onOperations:(()->Unit)?=null){
    val backend=koinInject<AgentBackend>();val board=backend.taskBoard;val scope=rememberCoroutineScope()
    val tasks by board.tasks.collectAsStateWithLifecycle()
    val humanJobs by backend.humanController.jobs.collectAsStateWithLifecycle()
    val agentJobs by backend.controller.jobs.collectAsStateWithLifecycle()
    var page by rememberSaveable{mutableIntStateOf(0)};var query by rememberSaveable{mutableStateOf("")}
    var phase by rememberSaveable{mutableStateOf<TaskPhase?>(null)};var selected by remember{mutableStateOf(setOf<String>())}
    var selectedJobs by remember{mutableStateOf(setOf<String>())};var priority by rememberSaveable{mutableStateOf<TaskPriority?>(null)}
    var sort by rememberSaveable{mutableStateOf("Updated")};var editor by remember{mutableStateOf<ManagedTask?>(null)}
    var create by remember{mutableStateOf(false)};var operation by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf<ManagedTask?>(null)}
    fun action(block:suspend()->Unit){scope.launch{try{block()}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message ?: "Operation failed"}}}
    LaunchedEffect(board){board.ready.await();backend.humanController.ready.await();backend.controller.ready.await()}
    val jobs=humanJobs.map{true to it}+agentJobs.map{false to it}
    Scaffold(topBar={TopAppBar(title={Text("Task manager")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
        actions={IconButton(onClick={if(page==0) create=true else operation=true}){Icon(Icons.Default.Add,if(page==0) "Create task" else "Start operation")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){
                listOf("Tasks","Operations").forEachIndexed{i,label ->SegmentedButton(selected=page==i,onClick={page=i;query=""},shape=SegmentedButtonDefaults.itemShape(i,2)){Text(label)}}
            }}
            item{Text(if(page==0) "${tasks.count{it.phase !in setOf(TaskPhase.DONE,TaskPhase.CANCELLED)}} open · ${tasks.count{it.phase==TaskPhase.DONE}} done · ${tasks.size}/200 records"
                else "${jobs.count{!it.second.terminal}} active · ${jobs.size} recorded operations",style=MaterialTheme.typography.titleMedium)
                Text(if(page==0) "Planning records you control. Marking Done does not claim that a model or agent executed the task."
                else "Real model and agent jobs. Stop requests cancellation; completed actions are not undone. Pause/resume is unavailable for these jobs; retry is a new attempt.",style=MaterialTheme.typography.bodySmall)
                if(page==1 && onOperations!=null) OutlinedButton(onClick=onOperations){Text("Whole-app emergency stop")}}
            item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text(if(page==0) "Search title, notes or tags" else "Search operation, ID or error")},leadingIcon={Icon(Icons.Default.Search,null)})}
            error?.let{item{ErrorCard(it){error=null}}}
            if(page==0){
                item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    FilterChip(phase==null,{phase=null},label={Text("All")});TaskPhase.entries.forEach{p ->FilterChip(phase==p,{phase=if(phase==p) null else p},label={Text(p.name.replace('_',' ').lowercase())})}
                }}
                item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    TaskPriority.entries.forEach{p ->FilterChip(priority==p,{priority=if(priority==p) null else p},label={Text(p.name.lowercase())})}
                    listOf("Updated","Priority","Due").forEach{s ->FilterChip(sort==s,{sort=s},label={Text(s)})}
                }}
                val filtered=tasks.filter{(phase==null || it.phase==phase) && (priority==null || it.priority==priority) &&
                    "${it.title} ${it.notes} ${it.tags.joinToString(" ")}".contains(query,true)}.let{list ->when(sort){"Priority"->list.sortedByDescending{it.priority.ordinal};"Due"->list.sortedBy{it.dueAtMs ?: Long.MAX_VALUE};else->list.sortedByDescending{it.updatedAtMs}}}
                item{FlowRow(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    OutlinedButton(onClick={selected=filtered.map{it.id}.toSet()}){Text("Select visible")}
                    TextButton(onClick={selected=emptySet()}){Text("Clear selection")}
                    if(selected.isNotEmpty()){
                        Button(onClick={action{board.batch(selected.toList(),TaskPhase.DONE);selected=emptySet()}}){Text("Done (${selected.size})")}
                        OutlinedButton(onClick={action{board.batch(selected.toList(),TaskPhase.CANCELLED);selected=emptySet()}}){Text("Cancel tasks")}
                        TextButton(onClick={action{board.batch(selected.toList(),TaskPhase.OPEN);selected=emptySet()}}){Text("Reopen")}
                    }
                }}
                if(filtered.isEmpty()) item{Text("No matching tasks. Create a task to plan model work, pairing or development.")}
                items(filtered,key={it.id}){task ->Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(T.medium),verticalArrangement=Arrangement.spacedBy(T.small)){
                        Row{Checkbox(task.id in selected,{checked ->selected=if(checked) selected+task.id else selected-task.id});Column(Modifier.weight(1f)){
                            Text(task.title,style=MaterialTheme.typography.titleMedium);Text("${task.phase.name.replace('_',' ')} · ${task.priority}",style=MaterialTheme.typography.labelMedium)
                        };IconButton(onClick={editor=task}){Icon(Icons.Default.Edit,"Edit ${task.title}")}}
                        if(task.notes.isNotBlank()) Text(task.notes.take(400))
                        if(task.tags.isNotEmpty()) Text(task.tags.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        task.dueAtMs?.let{due ->Text("Due ${DateFormat.getDateInstance().format(Date(due))}${if(due<System.currentTimeMillis() && task.phase !in setOf(TaskPhase.DONE,TaskPhase.CANCELLED)) " · overdue" else ""}")}
                        task.parentId?.let{parent ->Text("Subtask of ${tasks.firstOrNull{it.id==parent}?.title ?: parent}",style=MaterialTheme.typography.bodySmall)}
                        task.linkedJobId?.let{id ->Text("Linked operation: $id · ${jobs.firstOrNull{it.second.command.requestId==id}?.second?.phase ?: "not in local history"}",style=MaterialTheme.typography.bodySmall)}
                        FlowRow{TextButton(onClick={action{board.update(TaskMutation(id=task.id,expectedRevision=task.revision,phase=if(task.phase==TaskPhase.DONE) TaskPhase.OPEN else TaskPhase.DONE))}}){Text(if(task.phase==TaskPhase.DONE) "Reopen" else "Mark done")}
                            TextButton(onClick={editor=ManagedTask("","",parentId=task.id);create=true}){Text("Add subtask")}
                            TextButton(onClick={deleting=task}){Text("Delete")}}
                    }
                }}
            }else{
                item{FlowRow{OutlinedButton(onClick={selectedJobs=jobs.filter{!it.second.terminal}.map{(human,job)->"${if(human) "human" else "agent"}:${job.command.requestId}"}.toSet()}){Text("Select active")}
                    if(selectedJobs.isNotEmpty()) Button(onClick={action{jobs.filter{(human,job)->"${if(human) "human" else "agent"}:${job.command.requestId}" in selectedJobs}.forEach{(human,job)->(if(human) backend.humanController else backend.controller).cancel(job.command.requestId)};selectedJobs=emptySet()}}){Text("Stop selected (${selectedJobs.size})")}
                    TextButton(onClick={selectedJobs=emptySet()}){Text("Clear")}}}
                val filtered=jobs.filter{"${it.second.command.operation} ${it.second.command.requestId} ${it.second.error.orEmpty()}".contains(query,true)}.sortedByDescending{it.second.updatedAtMs}
                if(filtered.isEmpty()) item{Text("No recorded operations. Start an operation, or submit a typed agent command.")}
                items(filtered,key={"${it.first}:${it.second.command.requestId}"}){(human,job)->Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(T.medium),verticalArrangement=Arrangement.spacedBy(T.small)){
                        val key="${if(human) "human" else "agent"}:${job.command.requestId}"
                        Row{Checkbox(key in selectedJobs,{selectedJobs=if(it) selectedJobs+key else selectedJobs-key},enabled=!job.terminal);Column(Modifier.weight(1f)){
                            Text(job.command.operation.name.replace('_',' '),style=MaterialTheme.typography.titleSmall);Text("${if(human) "Human" else "Agent"} · ${job.phase}");Text(job.command.requestId,style=MaterialTheme.typography.labelSmall)}}
                        job.error?.let{Text("${job.errorCode}: $it",color=MaterialTheme.colorScheme.error)}
                        job.result?.let{result ->var detail by remember(job.command.requestId){mutableStateOf(false)};TextButton(onClick={detail=!detail}){Text(if(detail) "Hide result" else "View result")};if(detail) Text(result.toString().take(6000),style=MaterialTheme.typography.bodySmall)}
                        if(!job.terminal) TextButton(onClick={action{(if(human) backend.humanController else backend.controller).cancel(job.command.requestId)}}){Text("Stop")}
                        if(job.phase in setOf(AgentJobPhase.FAILED,AgentJobPhase.CANCELLED,AgentJobPhase.INTERRUPTED)) TextButton(onClick={action{(if(human) backend.humanController else backend.controller).retry(job.command.requestId,"retry_${UUID.randomUUID()}")}}){Text("Retry with new ID")}
                    }
                }}
            }
        }
    }
    if(create || editor!=null) TaskEditor(if(create) editor?.takeIf{it.id.isEmpty()} else editor,tasks,
        onClose={create=false;editor=null},onSave={input ->action{if(create) board.create(input) else board.update(input);create=false;editor=null}})
    deleting?.let{task ->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete task?")},text={Text("Delete ${task.title} from this task board. Linked operations are not cancelled. Child tasks must be removed first.")},
        confirmButton={TextButton(onClick={action{board.delete(task.id,task.revision);selected=selected-task.id;deleting=null}}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Keep")}})}
    if(operation) StartOperationDialog(onClose={operation=false},onSubmit={command ->action{backend.humanController.submit(command);operation=false}})
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable private fun TaskEditor(existing:ManagedTask?,tasks:List<ManagedTask>,onClose:()->Unit,onSave:(TaskMutation)->Unit){
    var title by remember(existing){mutableStateOf(existing?.title.orEmpty())};var notes by remember(existing){mutableStateOf(existing?.notes.orEmpty())}
    var tags by remember(existing){mutableStateOf(existing?.tags?.joinToString(", ").orEmpty())};var priority by remember(existing){mutableStateOf(existing?.priority ?: TaskPriority.NORMAL)}
    var phase by remember(existing){mutableStateOf(existing?.phase ?: TaskPhase.OPEN)};var due by remember(existing){mutableStateOf(existing?.dueAtMs)}
    var datePicker by remember{mutableStateOf(false)};var linked by remember(existing){mutableStateOf(existing?.linkedJobId.orEmpty())}
    AlertDialog(onDismissRequest=onClose,title={Text(if(existing?.id?.isNotEmpty()==true) "Edit task" else "Create task")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(T.small)){
        OutlinedTextField(title,{if(it.length<=200) title=it},label={Text("Title")});OutlinedTextField(notes,{if(it.length<=6000) notes=it},label={Text("Notes")},minLines=3,maxLines=8)
        OutlinedTextField(tags,{tags=it},label={Text("Tags, separated by commas")});FlowRow{TaskPriority.entries.forEach{p->FilterChip(priority==p,{priority=p},label={Text(p.name.lowercase())})}}
        if(existing?.id?.isNotEmpty()==true) FlowRow{TaskPhase.entries.forEach{p->FilterChip(phase==p,{phase=p},label={Text(p.name.replace('_',' ').lowercase())})}}
        TextButton(onClick={datePicker=true}){Text(due?.let{"Due ${DateFormat.getDateInstance().format(Date(it))}"} ?: "Set due date")}
        if(due!=null) TextButton(onClick={due=null}){Text("Clear due date")}
        OutlinedTextField(linked,{if(it.length<=80) linked=it},label={Text("Linked operation ID (optional)")})
        existing?.parentId?.let{Text("Parent: ${tasks.firstOrNull{task->task.id==it}?.title ?: it}")}
    }},confirmButton={TextButton(enabled=title.isNotBlank(),onClick={onSave(TaskMutation(id=existing?.id,expectedRevision=existing?.revision,title=title.trim(),notes=notes,phase=phase,priority=priority,
        tags=tags.split(',').map{it.trim()}.filter{it.isNotBlank()}.toSet(),dueAtMs=due,clearDue=due==null,parentId=existing?.parentId,linkedJobId=linked.ifBlank{null}))}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
    if(datePicker){val state=rememberDatePickerState(initialSelectedDateMillis=due);DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton(onClick={due=state.selectedDateMillis;datePicker=false}){Text("Choose")}}){DatePicker(state)}}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StartOperationDialog(onClose:()->Unit,onSubmit:(AgentCommand)->Unit){
    val library=koinInject<ModelLibrary>();val models by library.models.collectAsStateWithLifecycle()
    val available=listOf(AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_LOAD,AgentOperation.MODEL_GENERATE,AgentOperation.MODEL_UNLOAD,AgentOperation.CLUSTER_PLAN,AgentOperation.CLUSTER_START,AgentOperation.CLUSTER_WORKER_START,AgentOperation.CLUSTER_STOP)
    var operation by remember{mutableStateOf(AgentOperation.MODEL_LOAD)};var id by remember{mutableStateOf("")};var prompt by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text("Start an operation")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())){
        Text("Real execution through the shared backend. Downloads and native cluster operations require their configured runtime and grants.")
        FlowRow{available.forEach{op->FilterChip(operation==op,{operation=op},label={Text(op.name.replace('_',' ').lowercase())})}}
        models.filter{if(operation==AgentOperation.MODEL_DOWNLOAD) it.url.isNotBlank() else it.installed}.forEach{model->Row{RadioButton(id==model.id,{id=model.id});Text(model.name)}}
        if(operation==AgentOperation.MODEL_GENERATE) OutlinedTextField(prompt,{prompt=it},label={Text("Prompt for the loaded model")})
    }},confirmButton={TextButton(onClick={onSubmit(AgentCommand("human_${UUID.randomUUID()}",operation,modelId=id.ifBlank{null},prompt=prompt.ifBlank{null}))}){Text("Start")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
