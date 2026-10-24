package com.meshlit.ui.modern

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.chat.ChatState
import com.meshlit.ui.theme.ChatTokens as T
import com.meshlit.ui.theme.SidebarStyle
import com.meshlit.ui.theme.WorkspaceLayout

data class WorkspaceDestination(val id:String,val title:String,val description:String,val group:String,val keywords:String="")

/** Shared phone/wide-window inventory of connected modern routes. */
object WorkspaceDestinations {
    private val primary=listOf(
        WorkspaceDestination("chat","Chat","Your model conversations","Workspace"),
        WorkspaceDestination("models","Models","Downloads, imports and local runtime","Workspace","catalog GGUF load"),
        WorkspaceDestination("devices","Devices","Discovery, pairing and device inventory","Workspace","nearby list QR approval cluster"),
        WorkspaceDestination("monitor","Monitor","Actual device and model readings","Operations","metrics RAM thermal battery"),
        WorkspaceDestination("voice","Voice conversation","Hands-free input, installed voices and previews","Models and media","STT TTS microphone"),
        WorkspaceDestination("vision","Photo and camera","Images for capable vision models","Models and media","vision VLM"),
        WorkspaceDestination("structured","Structured output","Generate and validate local JSON","Models and media"),
        WorkspaceDestination("settings","Settings","All settings and preferences","Preferences")
    )
    private fun group(id:String)=when(id) {
        "tasks","ide","files","agents","agent-registry","agent-logs" -> "Workspace"
        "media","training","router","behavior","guardrails","acceleration","hyperl" -> "Models and media"
        "network","sharding","radar","host-map","device-dashboard","resources","ssh","securitylab","runtime","packages","commands","operations","power","firewall","recovery","logs","audit" -> "Operations"
        "meshchat" -> "Connections"
        "securityprivacy","guardrails","permissions","legal","identity-recovery" -> "Security & privacy"
        "cloud","huggingface","providers","gateway","tools-automation","mcp-catalog","rag","external","external-services","web-bridge","peers","openclaw","automation","termux","crawler","hooks" -> "Connections"
        else -> "Preferences"
    }
    val all:List<WorkspaceDestination> = primary.filter { com.meshlit.BuildProfile.routeAllowed(it.id) }+SettingsDestinations.all.filter{entry->primary.none{it.id==entry.id}}.map { entry ->
        WorkspaceDestination(entry.id,when(entry.id){"network"->"Networking";"securitylab"->"Security Lab";else->entry.title},entry.description,group(entry.id),entry.keywords)
    }
    val settingsIds=SettingsDestinations.all.map{it.id}.toSet()-setOf("models","monitor")
    fun search(query:String):List<WorkspaceDestination> {
        val words=query.trim().split(Regex("\\s+")).filter{it.isNotBlank()}
        return all.filter{entry->words.all{"${entry.title} ${entry.description} ${entry.keywords}".contains(it,true)}}
    }
    fun wideSidebar(widthDp:Float,layout:WorkspaceLayout)=widthDp>=900f && layout==WorkspaceLayout.ADAPTIVE
}

private fun workspaceIcon(id:String):ImageVector=when(id) {
    "chat"->Icons.Default.ChatBubbleOutline
    "models","catalog"->Icons.Default.Storage
    "devices"->Icons.Default.Devices
    "monitor"->Icons.Default.Insights
    "network","network-capture"->Icons.Default.Lan
    "voice"->Icons.Default.Mic
    "vision"->Icons.Default.Image
    "structured","ide","hyperl"->Icons.Default.Code
    "sessions","commands","ssh"->Icons.Default.Terminal
    "securitylab","packages","runtime"->Icons.Default.Shield
    "jobs","tasks"->Icons.Default.Checklist
    else->settingsIcon(id)
}

@Composable
internal fun WorkspaceSidebar(state:ChatState,current:String,style:SidebarStyle,onSelect:(String)->Unit,onNew:()->Unit,
    onChat:(String)->Unit,onDelete:(String)->Unit) {
    val context = LocalContext.current
    val shortcutIds by MenuShortcutStore.ids(context).collectAsState()
    val shortcuts = shortcutIds.filter { id -> WorkspaceDestinations.all.any { it.id == id } }
    var query by rememberSaveable {mutableStateOf("")}
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(T.large),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=RoundedCornerShape(10.dp),color=colors.primaryContainer) {
                Icon(Icons.Default.AutoAwesome,null,Modifier.padding(8.dp).size(20.dp),tint=colors.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(start=T.medium)) {
                Text("Meshlit",style=MaterialTheme.typography.titleMedium)
                Text(com.meshlit.BuildProfile.name,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
            }
            IconButton(onClick={onSelect("settings")}) {Icon(Icons.Default.Settings,"Settings")}
        }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=T.medium).testTag("workspace-search"),singleLine=true,
            textStyle=MaterialTheme.typography.bodyMedium,placeholder={Text("Find a page or tool")},leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={if(query.isNotBlank()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear menu search")}},shape=RoundedCornerShape(16.dp))
        FilledTonalButton(onClick=onNew,enabled=!state.running,modifier=Modifier.fillMaxWidth().padding(T.medium)) {
            Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(T.small));Text("New chat")
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("app-drawer"),contentPadding=PaddingValues(horizontal=T.medium,vertical=T.tiny),
            verticalArrangement=Arrangement.spacedBy(T.small)) {
            val matches=WorkspaceDestinations.search(query)
            if(query.isNotBlank()) {
                if(matches.isEmpty()) item{Text("No matching workspace",style=MaterialTheme.typography.bodyMedium)}
                items(matches,key={it.id}) {entry->WorkspaceNavigationCard(entry,current==entry.id,onSelect,Modifier.fillMaxWidth(),rows=true)}
            } else {
                item{Text("Quick access",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)}
                items(shortcuts,key={"quick:$it"}) {id ->
                    WorkspaceNavigationCard(WorkspaceDestinations.all.single{it.id==id},current==id,onSelect,Modifier.fillMaxWidth(),rows=true)
                }
                for(group in listOf("Workspace","Models and media","Operations","Connections","Preferences")) {
                    val entries=matches.filter{it.group==group && it.id !in shortcuts}
                    item(key="group:$group") {WorkspaceGroup(group,entries,current,style,onSelect)}
                }
                item{HorizontalDivider(color=colors.outlineVariant);Text("Recent chats",Modifier.padding(top=T.medium),style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)}
                if(state.conversations.isEmpty()) item{Text("Your chats stay on this device",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
                items(state.conversations,key={"chat:${it.id}"}) {conversation ->
                    Surface(onClick={onChat(conversation.id)},shape=RoundedCornerShape(12.dp),
                        color=if(current=="chat" && state.selectedId==conversation.id) colors.surfaceContainerHigh else colors.surface) {
                        Row(Modifier.fillMaxWidth().heightIn(min=T.touch).padding(start=T.medium),verticalAlignment=Alignment.CenterVertically) {
                            Text(conversation.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                            IconButton(enabled=!state.running,onClick={onDelete(conversation.id)}){Icon(Icons.Default.Delete,"Delete chat",Modifier.size(18.dp))}
                        }
                    }
                }
            }
        }
        HorizontalDivider(color=colors.outlineVariant)
        TextButton(onClick={onSelect("appearance")},modifier=Modifier.fillMaxWidth().heightIn(min=T.touch)) {
            Icon(Icons.Default.Palette,null,Modifier.size(18.dp));Spacer(Modifier.width(T.small));Text("Customize appearance")
        }
    }
}

@Composable private fun WorkspaceGroup(title:String,entries:List<WorkspaceDestination>,current:String,style:SidebarStyle,onSelect:(String)->Unit) {
    var expanded by rememberSaveable(title) {mutableStateOf(false)}
    LaunchedEffect(current){if(entries.any{it.id==current}) expanded=true}
    Column(verticalArrangement=Arrangement.spacedBy(T.small)) {
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,"${if(expanded) "Collapse" else "Expand"} $title",Modifier.size(18.dp))
        }
        if(expanded) {
            if(style==SidebarStyle.ROWS) entries.forEach{WorkspaceNavigationCard(it,current==it.id,onSelect,Modifier.fillMaxWidth(),rows=true)}
            else entries.chunked(2).forEach {pair -> Row(horizontalArrangement=Arrangement.spacedBy(T.small)) {
                pair.forEach{WorkspaceNavigationCard(it,current==it.id,onSelect,Modifier.weight(1f))}
                if(pair.size==1) Spacer(Modifier.weight(1f))
            }}
        }
    }
}

@Composable private fun WorkspaceNavigationCard(entry:WorkspaceDestination,selected:Boolean,onSelect:(String)->Unit,modifier:Modifier,rows:Boolean=false) {
    val colors=MaterialTheme.colorScheme
    Surface(onClick={onSelect(entry.id)},modifier=modifier.testTag("workspace-${entry.id}"),shape=RoundedCornerShape(14.dp),
        color=if(selected) colors.secondaryContainer else colors.surfaceContainerLow,
        border=BorderStroke(1.dp,if(selected) colors.primary.copy(alpha=0.55f) else colors.outlineVariant.copy(alpha=0.28f))) {
        if(rows) Row(Modifier.heightIn(min=T.touch).padding(horizontal=T.medium,vertical=T.small),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(T.medium)) {
            SettingsIconTile(entry.id)
            Text(entry.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        } else Column(Modifier.heightIn(min=88.dp).padding(10.dp),verticalArrangement=Arrangement.spacedBy(T.small)) {
            SettingsIconTile(entry.id)
            Text(entry.title,style=MaterialTheme.typography.labelLarge)
        }
    }
}
