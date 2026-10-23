package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.chat.PersonalMemory
import com.meshlit.di.koinInject

@Composable fun PersonalizationScreen(back:()->Unit) {
    val memory=koinInject<PersonalMemory>();val state by memory.state.collectAsState()
    var fact by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var style by remember(state.policy.style){mutableStateOf(state.policy.style)}
    fun action(block:()->Unit){try{block();error=null}catch(e:Exception){error=e.message}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick=back){Text("Back")};Text("Memory and personality",style=MaterialTheme.typography.headlineSmall)}
        item {Text("Remember preferences over time on this installation. Facts are encrypted locally, can be reviewed/deleted, and apply only to on-device chat. This is recall and personalization, not model retraining. Nothing is learned from web pages automatically.")}
        item {Row{Text("Long-term memory",Modifier.weight(1f));Switch(state.policy.memory,{value->action{memory.policy(state.policy.copy(memory=value,agentCanManage=if(!value) false else state.policy.agentCanManage))}})}}
        item {Row{Text("Save explicit ‘Remember that …’ requests",Modifier.weight(1f));Switch(state.policy.rememberRequests,{action{memory.policy(state.policy.copy(rememberRequests=it))}},enabled=state.policy.memory)}}
        item {Text("Other messages are not automatically stored as facts. Do not save passwords, keys or sensitive information; the basic credential filter is not a complete privacy classifier.")}
        item {OutlinedTextField(fact,{if(it.length<=512) fact=it},Modifier.fillMaxWidth(),label={Text("Preference or fact to remember")});Button(enabled=state.policy.memory && fact.isNotBlank(),onClick={action{memory.remember(fact);fact=""}}){Text("Remember")}}
        item {Row{Text("Personality profile",Modifier.weight(1f));Switch(state.policy.personality,{value->action{memory.policy(state.policy.copy(personality=value,agentCanManage=if(!value) false else state.policy.agentCanManage))}})}}
        item {OutlinedTextField(style,{if(it.length<=600) style=it},Modifier.fillMaxWidth(),label={Text("Response style")},minLines=2,maxLines=5);TextButton(onClick={action{memory.policy(state.policy.copy(style=style))}}){Text("Save profile")}}
        item {Row{Text("Automatic local model recovery",Modifier.weight(1f));Switch(state.policy.recovery,{value->action{memory.policy(state.policy.copy(recovery=value,agentCanManage=if(!value) false else state.policy.agentCanManage))}})}}
        item {Text("Retries a failed local text request once after reloading the same installed model. It does not modify app code, repair Android, download replacements, retry tools or bypass permission/Stop controls.")}
        item {Row{Text("Let delegated agents manage these switches",Modifier.weight(1f));Switch(state.policy.agentCanManage,{action{memory.policy(state.policy.copy(agentCanManage=it))}},enabled=!com.meshlit.BuildConfig.PLAY_REVIEW)}}
        item {Text("A delegated agent can enable or disable memory, personality and recovery, and add/remove facts. Also enable Personal memory tools in that chat. Turning any main switch off manually revokes agent management; stored facts remain until deleted.")}
        state.facts.forEach {entry->item(key=entry.id){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(entry.text);TextButton(onClick={action{memory.forget(entry.id)}}){Text("Forget")}}}}}
        item {OutlinedButton(enabled=state.facts.isNotEmpty(),onClick={action{memory.clear()}}){Text("Clear all saved facts")}}
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
}
