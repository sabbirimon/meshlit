package com.meshlit.desktop

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.sp

/** Same live controllers as the workspace; navigation never implies backend availability. */
@Composable internal fun WorkspaceRail(current: String, onSelect: (String) -> Unit, onNewChat: () -> Unit) {
    val colors = MaterialTheme.colors
    Column(Modifier.width(208.dp).fillMaxHeight().background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Meshlit", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        Text("Studio", style = MaterialTheme.typography.caption, color = colors.onSurface.copy(alpha = .5f))
        Spacer(Modifier.height(12.dp))
        Button(onNewChat, Modifier.fillMaxWidth().heightIn(min = 44.dp), colors = ButtonDefaults.buttonColors(backgroundColor = colors.primary.copy(alpha = .14f), contentColor = colors.primary), elevation = null, shape = RoundedCornerShape(24.dp)) { Text("＋  New chat") }
        Spacer(Modifier.height(10.dp))
        listOf("chat" to "Chat", "code" to "Workspace", "models" to "Models", "management" to "Dashboards", "device-dashboard" to "Devices").forEach { (id, title) ->
            Surface(Modifier.fillMaxWidth().clickable { onSelect(id) }, shape = RoundedCornerShape(12.dp),
                color = if (current == id) colors.primary.copy(alpha = .15f) else Color.Transparent,
                border = null) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavigationMark(id, if (current == id) colors.primary else colors.onSurface.copy(alpha = .65f))
                    Text(title, fontWeight = if (current == id) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        listOf("providers" to "Online models", "appearance" to "Settings").forEach { (id, title) ->
            TextButton({ onSelect(id) }, Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text(title, Modifier.fillMaxWidth()) }
        }
        Divider()
        Text("Meshlit preview", style = MaterialTheme.typography.caption, color = colors.onSurface.copy(alpha = .45f))
    }
}

@Composable internal fun WorkspaceHeader(title: String, status: String, onSettings: () -> Unit,
    onLoad: (() -> Unit)?, loadLabel: String, busy: Boolean, onNew: () -> Unit, onSearch: () -> Unit, onManage: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = .65f))
        }
        if (onLoad != null) OutlinedButton(onLoad, enabled = !busy, shape = RoundedCornerShape(12.dp)) { Text(loadLabel) }
        Box {
            TextButton({ menu = true }) { Text("•••") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ menu = false; onNew() }) { Text("New chat") }
                DropdownMenuItem({ menu = false; onSearch() }) { Text("Search") }
                DropdownMenuItem({ menu = false; onManage() }) { Text("Dashboards") }
                DropdownMenuItem({ menu = false; onSettings() }) { Text("Settings") }
            }
        }
    }

}

/** Small line icons remain sharp at each display scale; labels provide their accessible names. */
@Composable private fun NavigationMark(id: String, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val line = 1.5.dp.toPx()
        fun segment(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(w*x,w*y), Offset(w*x2,w*y2), line)
        when (id) {
            "chat" -> { drawRoundRect(tint, Offset(w*.12f,w*.12f), Size(w*.76f,w*.62f), androidx.compose.ui.geometry.CornerRadius(w*.12f), style = Stroke(line)); segment(.25f,.74f,.25f,.92f); segment(.25f,.92f,.45f,.74f) }
            "code" -> { segment(.35f,.25f,.1f,.5f); segment(.1f,.5f,.35f,.75f); segment(.65f,.25f,.9f,.5f); segment(.9f,.5f,.65f,.75f) }
            "models" -> { drawCircle(tint,w*.18f,Offset(w*.5f,w*.28f),style=Stroke(line)); drawCircle(tint,w*.18f,Offset(w*.27f,w*.72f),style=Stroke(line)); drawCircle(tint,w*.18f,Offset(w*.73f,w*.72f),style=Stroke(line)) }
            "management" -> { segment(.2f,.85f,.2f,.5f); segment(.5f,.85f,.5f,.15f); segment(.8f,.85f,.8f,.35f) }
            else -> { drawRoundRect(tint,Offset(w*.22f,w*.08f),Size(w*.56f,w*.84f),androidx.compose.ui.geometry.CornerRadius(w*.1f),style=Stroke(line)); segment(.4f,.78f,.6f,.78f) }
        }
    }
}

@Composable internal fun ChatWelcome(coding: Boolean, onPrompt: (String) -> Unit, onCode: () -> Unit) {
    val colors = MaterialTheme.colors
    BoxWithConstraints(Modifier.widthIn(max = 820.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp)) {
        val narrow = maxWidth < 560.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (coding) "Let's build" else "Meshlit", style = MaterialTheme.typography.h4.copy(
                fontSize = MaterialTheme.typography.h4.fontSize * if(narrow) .9f else 1.45f,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5796FF), Color(0xFFB496EF), Color(0xFFE77BA5)))))
            Text(if (coding) "Think through your next change" else "A little curiosity. A new possibility.",
                style = MaterialTheme.typography.h6, color = colors.onSurface.copy(alpha = .65f))
            Spacer(Modifier.height(8.dp))
            val prompts = if(coding) listOf("Explain code" to "Help me understand this code: ", "Plan a change" to "Help me plan an implementation for ")
                else listOf("Explain an idea" to "Explain a complex idea in simple terms: ", "Help me write" to "Help me write a first draft of ", "Make a plan" to "Help me make a practical plan for ", "Explore code" to "")
            prompts.chunked(if(narrow) 2 else 4).forEach { items ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, (title, draft) ->
                        Surface(Modifier.weight(1f).heightIn(min = if(narrow) 90.dp else 120.dp).clickable { if(draft.isEmpty()) onCode() else onPrompt(draft) },
                            color = colors.surface, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(title, style = MaterialTheme.typography.body1)
                                Spacer(Modifier.height(14.dp))
                                NavigationMark(if (draft.isEmpty() || coding) "code" else if(index == 2) "management" else "chat", colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
