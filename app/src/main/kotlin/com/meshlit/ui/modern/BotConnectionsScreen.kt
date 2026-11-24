package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.bots.BotConnections
import com.meshlit.core.inference.models.*
import com.meshlit.di.koinInject
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun BotConnectionsScreen(back:()->Unit,open:(String)->Unit) {
    val connections=koinInject<BotConnections>()
    val profiles by connections.profiles.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var edit by remember{mutableStateOf<BotConnection?>(null)}
    var key by remember{mutableStateOf("")}
    var selectedId by remember{mutableStateOf<String?>(null)}
    val selected=profiles.firstOrNull{it.id==selectedId && it.enabled}
    var session by remember(selected){mutableStateOf(UUID.randomUUID().toString())}
    var input by remember(selected){mutableStateOf("")}
    var reply by remember(selected){mutableStateOf<String?>(null)}
    var status by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<Pair<BotConnection,String>?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    fun editProfile(profile:BotConnection){edit=profile;key="";status=null}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick=back){Text("Back")}
            Text("Bots and connections",style=MaterialTheme.typography.headlineSmall)
            Text("Create your own bot instructions or connect a bot you operate elsewhere. Meshlit sends a request when you press Send. Messaging channels and bot skills stay in the host you configured.")
            TextButton(onClick={open("agents")}){Text("Create and edit Meshlit bots")}
            TextButton(onClick={open("bot-inbox")}){Text("Bot inbox and background receiver")}
            TextButton(onClick={open("openclaw")}){Text("Connect an OpenClaw gateway")}
            TextButton(onClick={open("providers")}){Text("Connect a model provider, including Grok")}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                BotConnectionKind.entries.forEach{kind->OutlinedButton(onClick={editProfile(BotConnection(kind=kind))},enabled=job==null){Text("Add ${kind.label}")}}
            }
        }
        items(profiles,key={it.id}){profile->Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(profile.name,style=MaterialTheme.typography.titleMedium)
            Text("${profile.kind.label} · ${profile.destination().host} · ${if(profile.enabled)"enabled" else "disabled"}")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(enabled=profile.enabled && job==null,onClick={selectedId=profile.id;status=null}){Text("Use bot")}
                TextButton(enabled=job==null,onClick={editProfile(profile)}){Text("Edit")}
                TextButton(enabled=job==null,onClick={scope.launch{
                    try {
                        withContext(Dispatchers.IO){connections.remove(profile.id)}
                        if(selectedId==profile.id)selectedId=null;status="Connection removed"
                    } catch(e:CancellationException){throw e}
                    catch(_:Exception){status="Could not remove the saved connection"}
                }}){Text("Remove connection")}
            }
        }}}
        selected?.let{profile->item {Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(profile.name,style=MaterialTheme.typography.titleMedium)
            Text(if(profile.kind==BotConnectionKind.ASTRBOT)"Uses a private AstrBot chat session. Replies and host-side tools follow your AstrBot configuration."
                else "The routine can act on its host. Acknowledgement means it started; check Grok Bot for the result.")
            OutlinedTextField(input,{input=it.take(12000)},Modifier.fillMaxWidth(),label={Text(if(profile.kind==BotConnectionKind.ASTRBOT)"Message" else "Routine input")},enabled=job==null,minLines=2)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=job==null && input.isNotBlank(),onClick={pending=profile to input.trim()}){Text(if(profile.kind==BotConnectionKind.ASTRBOT)"Review and send" else "Review and run")}
                if(job!=null)TextButton(onClick={job?.cancel()}){Text("Stop waiting")}
                else if(profile.kind==BotConnectionKind.ASTRBOT)TextButton(onClick={session=UUID.randomUUID().toString();input="";reply=null;status="New bot session"}){Text("New session")}
            }
            reply?.let{Text(it)}
        }}}}
        status?.let{item{Text(it)}}
    }
    edit?.let{profile->AlertDialog(onDismissRequest={edit=null;key=""},title={Text(profile.kind.label)},
        text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedTextField(profile.name,{edit=profile.copy(name=it.take(60))},label={Text("Name")},singleLine=true)
            OutlinedTextField(profile.endpoint,{edit=profile.copy(endpoint=it.take(2048))},label={Text(if(profile.kind==BotConnectionKind.ASTRBOT)"AstrBot HTTPS base URL" else "Exact routine webhook URL")},singleLine=true)
            if(profile.kind==BotConnectionKind.ASTRBOT)OutlinedTextField(profile.configId,{edit=profile.copy(configId=it.take(80))},label={Text("AstrBot config ID (optional)")},singleLine=true)
            OutlinedTextField(key,{key=it.take(4096)},label={Text("Bearer key (enter for a new route)")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            Text(if(profile.kind==BotConnectionKind.ASTRBOT)"Use an AstrBot API key with chat scope. HTTP is allowed only through a loopback tunnel."
                else "Paste the routine's own webhook URL and key. This is separate from the Grok model API.",style=MaterialTheme.typography.bodySmall)
            Row {Text("Enable",Modifier.weight(1f));Switch(profile.enabled,{edit=profile.copy(enabled=it)})}
        }},confirmButton={TextButton(onClick={
            val token=key.trim().takeIf{it.isNotBlank()};val draft=profile.copy(name=profile.name.trim(),endpoint=profile.endpoint.trim(),configId=profile.configId.trim())
            scope.launch{
                try {
                    withContext(Dispatchers.IO){connections.save(draft,token)}
                    edit=null;key="";status="Connection saved"
                } catch(e:CancellationException){throw e}
                catch(_:Exception){status="Could not save: check the address, name, scope and key for this exact route."}
            }
        }){Text("Save")}},dismissButton={TextButton(onClick={edit=null;key=""}){Text("Cancel")}})}
    pending?.let{(profile,text)->AlertDialog(onDismissRequest={pending=null},title={Text("Send to ${profile.name}?")},
        text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("${profile.destination().host} will receive this text${if(profile.kind==BotConnectionKind.GROK_ROUTINE)" as the routine input" else ""}.")
            Text(text)
        }},
        confirmButton={TextButton(onClick={
            pending=null;reply=null;status="Waiting for ${profile.name}"
            job=scope.launch {
                try {
                    when(val result=connections.send(profile,text,session)) {
                        is BotResult.Reply->{reply=result.text;status="AstrBot returned a completed text reply"}
                        BotResult.RoutineAccepted->status="Routine accepted. Check Grok Bot for its result."
                    }
                    input=""
                } catch(e:CancellationException){status="Stopped waiting. Check the bot before sending again.";throw e}
                catch(_:Exception){status="No verified reply or acknowledgement. Check the host and key, and inspect the bot before retrying."}
                finally{job=null}
            }
        }){Text(if(profile.kind==BotConnectionKind.ASTRBOT)"Send" else "Run routine")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}
