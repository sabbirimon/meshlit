package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.SecuritySkills

@Composable internal fun SecuritySkillsPanel() {
    var selected by remember{mutableStateOf(SecuritySkills.all.first().id)}
    val clipboard=LocalClipboardManager.current
    Text("Security skills",style=MaterialTheme.typography.h6)
    Text("Built-in defensive references for people and models. Copy a reference into your chosen model; it grants no tool or network permission. Studio lab execution and an agent MCP bridge remain separate unfinished adapters.")
    SecuritySkills.all.forEach{skill->
        TextButton(onClick={selected=skill.id}){Text((if(selected==skill.id) "• " else "")+skill.title)}
    }
    val skill=checkNotNull(SecuritySkills.find(selected))
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SelectionContainer {Text(skill.reference())}
        OutlinedButton(onClick={clipboard.setText(AnnotatedString(skill.reference()))}){Text("Copy skill reference")}
    }}
    Text("Reviewed source: ${SecuritySkills.reviewedSource}",style=MaterialTheme.typography.caption)
}
