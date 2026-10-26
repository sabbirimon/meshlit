package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.nio.file.*
import java.util.prefs.Preferences
import javax.swing.JFileChooser

@Composable internal fun CodingWorkspace(modifier: Modifier, onDraft: (String) -> Unit, onClose: () -> Unit, onDirty: (Boolean) -> Unit) {
    val preferences = remember { Preferences.userRoot().node("com/meshlit/studio-code") }
    var layout by remember { mutableStateOf(preferences.get("layout", "Code")) }
    var explorer by remember { mutableStateOf(preferences.getBoolean("explorer", true)) }
    var terminal by remember { mutableStateOf(preferences.getBoolean("terminal", false)) }
    var workspace by remember { mutableStateOf<CodeFiles?>(null) }
    var directory by remember { mutableStateOf<Path?>(null) }
    var entries by remember { mutableStateOf(emptyList<Path>()) }
    var document by remember { mutableStateOf<CodeDocument?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val dirty = document?.let { it.text != text } == true
    LaunchedEffect(dirty) { onDirty(dirty) }
    DisposableEffect(Unit) { onDispose { onDirty(false) } }
    fun guarded(action: () -> Unit) { if(dirty) pending = action else action() }
    fun attempt(action: () -> Unit) { try { action(); error = null } catch(e: Exception) { error = e.message ?: "File operation failed" } }
    fun openFolder(path: Path) = attempt { val opened = CodeFiles(path); val files = opened.entries(); workspace = opened; directory = opened.root; entries = files; document = null; text = "" }
    fun saveLayout() = attempt { preferences.put("layout", layout); preferences.putBoolean("explorer", explorer); preferences.putBoolean("terminal", terminal); preferences.flush() }
    LaunchedEffect(Unit) { System.getProperty("meshlit.workspace")?.let { openFolder(Path.of(it)) } }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Coding workspace", style = MaterialTheme.typography.h6)
        Row {
            TextButton({ guarded { val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }; if(picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) openFolder(picker.selectedFile.toPath()) } }) { Text("Open folder") }
            TextButton({ guarded(onClose) }) { Text("Close workspace") }
        }
        Row { listOf("Focus", "Code", "Review", "Custom").forEach { choice -> TextButton({ layout = choice; if(choice != "Custom") { explorer = choice == "Code"; terminal = false }; saveLayout() }) { Text((if(layout == choice) "✓ " else "") + choice) } } }
        if (layout == "Custom") Row {
            Checkbox(explorer, { explorer = it; saveLayout() }); Text("Files")
            Checkbox(terminal, { terminal = it; saveLayout() }); Text("Terminal")
        } else TextButton({ terminal = !terminal; saveLayout() }) { Text(if(terminal) "Hide terminal" else "Show terminal") }
        Text(workspace?.root?.toString() ?: "Choose a project folder. Files stay local until you add code to chat.", style = MaterialTheme.typography.caption)
        error?.let { Text(it, color = MaterialTheme.colors.error) }
        if (explorer && workspace != null) Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
            if(directory != workspace!!.root) TextButton({ attempt { val parent = checkNotNull(directory).parent; entries = workspace!!.entries(parent); directory = parent } }) { Text("↑ Parent folder") }
            entries.forEach { file -> TextButton({ attempt {
                if(Files.isDirectory(file)) { entries = workspace!!.entries(file); directory = file }
                else guarded { attempt { document = workspace!!.read(file); text = document!!.text } }
            } }) { Text((if(Files.isDirectory(file)) "▸ " else "") + file.fileName.toString()) } }
        }
        if (workspace != null && !explorer) TextButton({ guarded { val picker = JFileChooser(workspace!!.root.toFile()); if(picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) attempt { document = workspace!!.read(picker.selectedFile.toPath()); text = document!!.text } } }) { Text("Open file") }
        document?.let { opened ->
            Text(opened.path.fileName.toString() + if(dirty) " · unsaved" else "", style = MaterialTheme.typography.subtitle2)
            if(layout == "Review") OutlinedTextField(opened.text, {}, readOnly = true, modifier = Modifier.weight(.6f).fillMaxWidth(), textStyle = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace), label = { Text("Original on open · compare before saving") })
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f).fillMaxWidth(), textStyle = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace), label = { Text("UTF-8 source") })
            Row {
                Button({ attempt { document = workspace!!.save(opened, text) } }, enabled = dirty) { Text("Save") }
                TextButton({ guarded { attempt { document = workspace!!.read(opened.path); text = document!!.text } } }) { Text("Reload") }
                TextButton({ onDraft("Review this code from ${opened.path.fileName}:\n\n```\n${text.take(10000)}\n```" ) }) { Text(if(text.length > 10000) "Add first 10,000 characters to chat" else "Add to chat draft") }
            }
        } ?: Spacer(Modifier.weight(1f))
        if (terminal) Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) { TerminalPanel() }
    }
    pending?.let { action -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Unsaved changes") }, text = { Text("Save your file first, or discard these edits before continuing.") },
        confirmButton = { TextButton({ pending = null; action() }) { Text("Discard and continue") } }, dismissButton = { TextButton({ pending = null }) { Text("Keep editing") } }) }
}
