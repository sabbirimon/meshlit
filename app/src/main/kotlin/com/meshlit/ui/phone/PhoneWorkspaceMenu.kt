package com.meshlit.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.meshlit.ui.modern.*

@Composable
internal fun PhoneWorkspaceMenu(state:ChatState,onSelect:(String)->Unit,onNew:()->Unit,
                               onChat:(String)->Unit,onDelete:(String)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var opened by rememberSaveable {mutableStateOf<String?>(null)}
    val saved by MenuShortcutStore.ids(LocalContext.current).collectAsState()
    val pages=WorkspaceDestinations.search(query)
    LazyColumn(Modifier.fillMaxSize().testTag("app-drawer"),contentPadding=PaddingValues(horizontal=20.dp,vertical=24.dp)) {
        item { Text("Meshlit",style=MaterialTheme.typography.headlineMedium) }
        item { Spacer(Modifier.height(20.dp));OutlinedTextField(query,{query=it.take(200)},Modifier.fillMaxWidth().testTag("workspace-search"),
            placeholder={Text("Find a tool")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true) }
        val core=listOf("chat","models","devices","agents")
        if(query.isBlank()){
            item { SectionTitle("Core") }
            item { PhoneMenuRow("New chat",Icons.Default.EditNote,!state.running,onClick=onNew) }
            items(core.mapNotNull{id->pages.find{it.id==id}},key={it.id}){entry->
                DestinationRow(entry.id,entry.title,entry.description){onSelect(entry.id)}
            }
        }
        val extra=saved.filter{it !in core && it!="settings"}
        if(query.isBlank() && extra.isNotEmpty()){
            item{SectionTitle("Shortcuts")}
            items(extra.mapNotNull{id->pages.find{it.id==id}},key={"pin:"+it.id}){entry->
                DestinationRow(entry.id,entry.title,entry.description){onSelect(entry.id)}
            }
        }
        for(group in SettingsGroup.entries){
            val entries=pages.filter{entry-> (query.isNotBlank() || entry.id !in core+extra)
                && SettingsDestinations.all.find{it.id==entry.id}?.group==group}
            if(entries.isNotEmpty()){
                item(key=group.name){PhoneMenuRow(group.title,phoneIcon(entries.first().id)){opened=if(opened==group.name)null else group.name}}
                if(opened==group.name || query.isNotBlank())items(entries,key={it.id}){entry->
                    DestinationRow(entry.id,entry.title,entry.description){onSelect(entry.id)}
                }
            }
        }
        val other=pages.filter{entry-> SettingsDestinations.all.none{it.id==entry.id} && (query.isNotBlank() || entry.id !in core+extra)}
        if(other.isNotEmpty()){
            item{SectionTitle("Workspace")}
            items(other,key={it.id}){entry->DestinationRow(entry.id,entry.title,entry.description){onSelect(entry.id)}}
        }
        if(query.isBlank()) {
            if(state.conversations.isNotEmpty()) item {
                HorizontalDivider(Modifier.padding(vertical=16.dp))
                Text("Recent chats",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
            }
            items(state.conversations,key={"history:${it.id}"}) { chat ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(chat.title,Modifier.weight(1f).clickable(enabled=!state.running){onChat(chat.id)}.padding(vertical=16.dp),
                        maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                    TouchIcon(Icons.Default.DeleteOutline,"Delete chat",enabled=!state.running){onDelete(chat.id)}
                }
            }
        }
    }
}

@Composable private fun PhoneMenuRow(label:String,icon:ImageVector,enabled:Boolean=true,tag:String=label,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag).clickable(enabled=enabled,onClick=onClick),
        horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,style=MaterialTheme.typography.bodyLarge)
    }
}
