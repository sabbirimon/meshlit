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
    var allTools by rememberSaveable {mutableStateOf(false)}
    val saved by MenuShortcutStore.ids(LocalContext.current).collectAsState()
    val shortcuts=(listOf("models","devices","agents","settings")+saved).distinct()
    val pages=WorkspaceDestinations.search(query)
    LazyColumn(Modifier.fillMaxSize().testTag("app-drawer"),contentPadding=PaddingValues(horizontal=20.dp,vertical=24.dp)) {
        item { Text("Meshlit",style=MaterialTheme.typography.headlineMedium) }
        item { Spacer(Modifier.height(20.dp));OutlinedTextField(query,{query=it.take(200)},Modifier.fillMaxWidth().testTag("workspace-search"),
            placeholder={Text("Find a tool")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true) }
        if(query.isBlank()) item { PhoneMenuRow("New chat",Icons.Default.EditNote,!state.running,onClick=onNew) }
        val visible=if(query.isNotBlank() || allTools) pages else shortcuts.mapNotNull{id->pages.find{it.id==id}}
        items(visible,key={it.id}) { page ->
            PhoneMenuRow(if(page.id=="models") "Models" else page.title,settingsIcon(page.id),tag="workspace-${page.id}") { onSelect(page.id) }
        }
        if(query.isBlank()) {
            item { PhoneMenuRow(if(allTools) "Fewer tools" else "All tools",if(allTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore){allTools=!allTools} }
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
