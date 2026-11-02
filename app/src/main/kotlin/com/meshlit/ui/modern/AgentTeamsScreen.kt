package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.teams.*
import com.meshlit.core.mcp.control.*
import kotlinx.coroutines.*

@Composable internal fun AgentTeamsScreen(onBack:()->Unit,onOpen:(String)->Unit={}) {
    val context=LocalContext.current;val teams=remember{AgentTeams.get(context)};val runs by teams.runs.collectAsState()
    val scope=rememberCoroutineScope();var goal by remember{mutableStateOf("")}
    var mode by remember{mutableStateOf(TeamMode.SINGLE_DEVICE)}
    var control by remember{mutableStateOf(TeamControl.USER_DIRECTED)}
    var rounds by remember{mutableIntStateOf(2)};var parallel by remember{mutableIntStateOf(2)}
    var profileIds by remember{mutableStateOf(List<String?>(4){null})}
    var targets by remember{mutableStateOf(teams.approvedTargetIds())}
    var delegation by remember{mutableStateOf(teams.delegatedSetup())}
    var error by remember{mutableStateOf<String?>(null)};var starting by remember{mutableStateOf(false)}
    val profiles=remember{runCatching{teams.profiles()}.getOrElse{emptyList()}}
    fun setup()=TeamSetup(mode,control,rounds,parallel,profileIds,targets)
    fun action(work:suspend()->Unit){scope.launch{try{work();error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Team action failed"}}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("agent-teams"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick=onBack){Text("Back")};Text("Agent teams",style=MaterialTheme.typography.headlineSmall)
            Text("Run agents for your task, or let a supervising manager assign and review bounded rounds. Every role uses approved model jobs and returns attributed text analysis.")
            Text("Cluster modes use selected targets. Model layer sharding has its own runtime and setup.")
            TextButton(onClick={onOpen("agent-catalog")}){Text("Browse and adapt role templates")}
            TextButton(onClick={onOpen("agents")}){Text("Enable saved role templates")}
        }
        item {
            Text("Execution location",style=MaterialTheme.typography.titleMedium)
            TeamMode.entries.forEach{choice->FilterChip(mode==choice,{mode=choice},label={Text(choice.name.replace('_',' '))})}
            Text("Control",style=MaterialTheme.typography.titleMedium)
            FilterChip(control==TeamControl.USER_DIRECTED,{control=TeamControl.USER_DIRECTED},label={Text("User directed")})
            FilterChip(control==TeamControl.SUPERVISED_AUTONOMOUS,{control=TeamControl.SUPERVISED_AUTONOMOUS},label={Text("Autonomous · supervised")})
            FilterChip(control==TeamControl.AGENT_DIALOGUE,{control=TeamControl.AGENT_DIALOGUE},label={Text("Agent conversation")})
            Text(when(control){
                TeamControl.USER_DIRECTED->"Planner → workers → reviewer, once."
                TeamControl.SUPERVISED_AUTONOMOUS->"The manager chooses tasks for existing workers, reviews findings and can request revisions. Invalid manager JSON stops the run. The manager cannot add devices, grant tools or change limits."
                TeamControl.AGENT_DIALOGUE->"The lead and selected agents can ask each other for bounded tasks and reply, including return requests. Messages stay within this team's approved roles and sources; call, depth and time limits apply."
            })
            if(control==TeamControl.SUPERVISED_AUTONOMOUS){Text("Maximum worker rounds");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{n->FilterChip(rounds==n,{rounds=n},label={Text("$n")})}}}
            Text("Parallel workers");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..2).forEach{n->FilterChip(parallel==n,{parallel=n},label={Text("$n")})}}
            Text("Ten-minute deadline · 256 output tokens per model call. Local inference shares the CPU work queue. Pause allows active calls to finish, then holds new calls; the deadline keeps running.")
        }
        item {
            Text("Roles",style=MaterialTheme.typography.titleMedium)
            val roleLabels=if(control==TeamControl.AGENT_DIALOGUE)listOf("Lead","Agent 2","Agent 3","Agent 4") else listOf("Manager / planner","Worker 1","Worker 2","Reviewer")
            roleLabels.forEachIndexed{index,label->
                var expanded by remember{mutableStateOf(false)}
                Box {
                    OutlinedButton(onClick={expanded=true}){Text("$label: ${profiles.firstOrNull{it.id==profileIds[index]}?.name ?: "Default"}")}
                    DropdownMenu(expanded,onDismissRequest={expanded=false}){
                        DropdownMenuItem(text={Text("Default")},onClick={profileIds=profileIds.toMutableList().also{it[index]=null};expanded=false})
                        profiles.forEach{profile->DropdownMenuItem(text={Text(profile.name)},onClick={profileIds=profileIds.toMutableList().also{it[index]=profile.id};expanded=false})}
                    }
                }
            }
            Text("Only enabled templates appear. Role guidance does not grant capabilities. Local templates must match the loaded model.")
        }
        item {
            Text("Approved remote targets",style=MaterialTheme.typography.titleMedium)
            teams.approvedTargetIds().forEach{id->Row{Checkbox(id in targets,{checked->targets=if(checked)targets+id else targets-id});Text(teams.targetLabel(id))}}
            Text("MCP and web peers require separate owner approval on both ends. Tasks and preceding role outputs are sent to selected peers.")
            teams.sources().forEach{source->
                var model by remember(source){mutableStateOf(teams.remoteModel(source.id))}
                var revision by remember{mutableIntStateOf(0)}
                Text(source.name);OutlinedTextField(model,{model=it},label={Text("Remote loaded model ID")})
                Row{Switch(remember(source,model,revision){teams.allowed(source,model)},enabled=model.isNotBlank(),onCheckedChange={enabled->
                    try{teams.approve(source,model,enabled);revision++;targets=if(enabled)targets+source.id else targets-source.id}catch(e:Exception){error=e.message}
                });Text("Allow this source/model for team text jobs")}
            }
        }
        item {
            Text("Allow other agents to use a saved team setup",style=MaterialTheme.typography.titleMedium)
            Row{Switch(delegation!=null,{enabled->try{
                val saved=if(enabled)setup() else null;teams.saveDelegatedSetup(saved);delegation=saved
            }catch(e:Exception){error=e.message}});Text(if(delegation!=null)"Delegation enabled" else "Off")}
            Text("Agents can submit a goal using this saved setup, inspect their runs and pause, resume or stop them. Models delegation and app-wide agent access are also required. Settings and permissions remain owner-controlled.")
            delegation?.let{saved->
                Text("Saved: ${saved.mode.name} · ${saved.control.name} · ${saved.maxRounds} rounds")
                if(saved!=setup())OutlinedButton(onClick={try{val next=setup();teams.saveDelegatedSetup(next);delegation=next}catch(e:Exception){error=e.message}}){Text("Replace saved setup with current choices")}
            }
            TextButton(onClick={onOpen("agents")}){Text("Agent permissions and operations")}
        }
        item {
            OutlinedTextField(goal,{if(it.length<=4000)goal=it},label={Text("Team task")},modifier=Modifier.fillMaxWidth())
            Button(enabled=!starting && goal.isNotBlank(),onClick={starting=true;scope.launch{try{teams.start(goal,setup());error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Team could not start"}finally{starting=false}}}){Text("Start team")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        runs.asReversed().forEach{run->item(key=run.id){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
            Text("${run.plan.control.name} · ${run.phase}",style=MaterialTheme.typography.titleMedium)
            Text(run.plan.goal);Text("${if(run.agentInitiated)"Agent initiated" else "User initiated"} · ${run.id.take(8)} · active ${run.activeMembers.joinToString().ifBlank{"none"}}")
            if(run.phase in setOf("QUEUED","RUNNING","PAUSING","PAUSED")){
                if(run.phase in setOf("PAUSING","PAUSED"))TextButton(onClick={action{teams.resume(run.id)}}){Text("Resume")}
                else TextButton(onClick={action{teams.pause(run.id)}}){Text("Pause after active calls")}
                Button(onClick={teams.stop(run.id)}){Text("Stop team")}
            }
            run.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(run.phase=="LIMIT_REACHED")Text("Budget reached. Review findings before starting another run.")
            if(run.remoteStopUnconfirmed.isNotEmpty())Text("Remote stop not confirmed: ${run.remoteStopUnconfirmed.joinToString()}. Inspect the receiving node after reconnect.",color=MaterialTheme.colorScheme.error)
            if(run.messages.isNotEmpty()){
                Text("Agent conversation",style=MaterialTheme.typography.titleSmall)
                run.messages.forEach{message->
                    Text("${message.from} → ${message.to} · ${message.kind} · call ${message.callIndex}",style=MaterialTheme.typography.labelLarge)
                    Text(message.body)
                    message.replyTo?.let{Text("Reply to $it",style=MaterialTheme.typography.labelSmall)}
                }
            }
            run.outputs.sortedBy{it.callIndex}.forEach{output->Text("${output.memberId} · ${output.sourceId} · call ${output.callIndex}",style=MaterialTheme.typography.labelLarge);Text(output.text)}
        }}}}
    }
}
