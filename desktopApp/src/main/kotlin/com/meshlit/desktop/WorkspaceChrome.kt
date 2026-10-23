package com.meshlit.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Same live controllers as the workspace; navigation never implies backend availability. */
@Composable internal fun WorkspaceRail(current: String, onSelect: (String) -> Unit, onNewChat: () -> Unit) {
    val colors = MaterialTheme.colors
    Column(Modifier.width(248.dp).fillMaxHeight().background(colors.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Meshlit", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        Text("Your models. Your devices.", style = MaterialTheme.typography.caption, color = colors.onSurface.copy(alpha = .65f))
        Spacer(Modifier.height(12.dp))
        Button(onNewChat, Modifier.fillMaxWidth().heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) { Text("＋ New chat") }
        Spacer(Modifier.height(10.dp))
        listOf("chat" to "Chat", "models" to "Models", "device-dashboard" to "Devices", "monitor" to "Resources", "securityprivacy" to "Security & privacy").forEach { (id, title) ->
            Surface(Modifier.fillMaxWidth().clickable { onSelect(id) }, shape = RoundedCornerShape(12.dp),
                color = if (current == id) colors.primary.copy(alpha = .15f) else Color.Transparent,
                border = if (current == id) BorderStroke(1.dp, colors.primary.copy(alpha = .4f)) else null) {
                Text(title, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontWeight = if (current == id) FontWeight.Medium else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Explore", style = MaterialTheme.typography.overline, color = colors.onSurface.copy(alpha = .6f))
        listOf("crypto" to "Identity & rewards", "management" to "Tools & integrations", "appearance" to "Appearance", "providers" to "Host connection").forEach { (id, title) ->
            TextButton({ onSelect(id) }, Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(title, Modifier.fillMaxWidth()) }
        }
        Divider()
        Text("Experimental desktop · explicit access", style = MaterialTheme.typography.caption)
    }
}

@Composable internal fun WorkspaceHeader(title: String, status: String, onSettings: () -> Unit,
    onLoad: (() -> Unit)?, loadLabel: String, busy: Boolean, onNew: () -> Unit, onSearch: () -> Unit, onManage: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = .65f))
        }
        if (onLoad != null) OutlinedButton(onLoad, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(loadLabel) }
        Box {
            TextButton({ menu = true }) { Text("Menu ▾") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ menu = false; onNew() }) { Text("New chat") }
                DropdownMenuItem({ menu = false; onSearch() }) { Text("Search") }
                DropdownMenuItem({ menu = false; onManage() }) { Text("Tools & integrations") }
                DropdownMenuItem({ menu = false; onSettings() }) { Text("Settings & connection") }
            }
        }
    }
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = .1f))
}
