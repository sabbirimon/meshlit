package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
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

@Composable internal fun CodingWorkspace(modifier: Modifier, onDraft: (String) -> Unit, onClose: () -> Unit, onDirty: (Boolean) -> Unit, command: Pair<Int, String> = 0 to "") {
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
    LaunchedEffect(command) {
        when(command.second) {
            "open-folder" -> guarded { val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }; if(picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) openFolder(picker.selectedFile.toPath()) }
            "save-file" -> document?.let { opened -> attempt { document = workspace!!.save(opened, text) } }
            "vscode" -> attempt { openVsCode(workspace?.root) }
            "terminal" -> { terminal = true; saveLayout() }
            "focus-editor" -> { layout = "Focus"; explorer = false; saveLayout() }
            "show-files" -> { layout = "Code"; explorer = true; saveLayout() }
        }
    }
    val colors = MaterialTheme.colors
    Column(modifier.background(colors.background)) {
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workspace?.root?.fileName?.toString() ?: "Coding workspace", style = MaterialTheme.typography.subtitle1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Local files", style = MaterialTheme.typography.caption, color = colors.onSurface.copy(alpha = .55f))
            }
            TextButton({ guarded { val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }; if(picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) openFolder(picker.selectedFile.toPath()) } }) { Text("Open folder") }
            TextButton({ attempt { openVsCode(workspace?.root) } }) { Text("VS Code ↗") }
            TextButton({ guarded(onClose) }) { Text("Back to chat") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Focus", "Code", "Review", "Custom").forEach { choice -> TextButton({ layout = choice; if(choice != "Custom") { explorer = choice == "Code"; terminal = false }; saveLayout() },
                colors = ButtonDefaults.textButtonColors(contentColor = if(layout == choice) colors.primary else colors.onSurface.copy(alpha = .55f))) { Text(choice) } }
            if (layout == "Custom") {
                Checkbox(explorer, { explorer = it; saveLayout() }); Text("Files", style = MaterialTheme.typography.caption)
            }
            TextButton({ terminal = !terminal; saveLayout() }) { Text(if(terminal) "Hide terminal" else "Terminal") }
        }
        Divider(color = colors.onSurface.copy(alpha = .08f))
        error?.let { Text(it, Modifier.padding(12.dp), color = colors.error) }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (explorer && workspace != null) Column(Modifier.width(174.dp).fillMaxHeight().background(colors.surface).padding(8.dp).verticalScroll(rememberScrollState())) {
                Text("Files", Modifier.padding(8.dp), style = MaterialTheme.typography.subtitle2)
                if(directory != workspace!!.root) TextButton({ attempt { val parent = checkNotNull(directory).parent; entries = workspace!!.entries(parent); directory = parent } }) { Text("↑ Parent folder") }
                entries.forEach { file ->
                    val selected = document?.path == file
                    Surface(Modifier.fillMaxWidth().clickable { attempt {
                        if(Files.isDirectory(file)) { entries = workspace!!.entries(file); directory = file }
                        else guarded { attempt { document = workspace!!.read(file); text = document!!.text } }
                    } }, color = if(selected) colors.primary.copy(alpha = .12f) else colors.surface, shape = RoundedCornerShape(6.dp)) {
                        Text((if(Files.isDirectory(file)) "▸  " else "   ") + file.fileName.toString(), Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (workspace != null && !explorer) TextButton({ guarded { val picker = JFileChooser(workspace!!.root.toFile()); if(picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) attempt { document = workspace!!.read(picker.selectedFile.toPath()); text = document!!.text } } }) { Text("Open file") }
                document?.let { opened ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(opened.path.fileName.toString() + if(dirty) " •" else "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.subtitle2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton({ attempt { document = workspace!!.save(opened, text) } }, enabled = dirty) { Text("Save") }
                        TextButton({ guarded { attempt { document = workspace!!.read(opened.path); text = document!!.text } } }) { Text("Reload") }
                    }
                    if(layout == "Review") OutlinedTextField(opened.text, {}, readOnly = true, modifier = Modifier.weight(.6f).fillMaxWidth(), textStyle = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace), label = { Text("Saved version") })
                    OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f).fillMaxWidth(), textStyle = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace), label = { Text("Editor") })
                    TextButton({ onDraft("Review this code from ${opened.path.fileName}:\n\n```\n${text.take(10000)}\n```" ) }) { Text(if(text.length > 10000) "Add first 10,000 characters to chat" else "Add file to chat draft") }
                } ?: Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your next idea starts here", style = MaterialTheme.typography.h6)
                    Spacer(Modifier.height(12.dp))
                    Text(if(workspace == null) "Open a project folder to get started." else "Choose a file from the explorer.", color = colors.onSurface.copy(alpha = .6f))
                    Spacer(Modifier.height(8.dp))
                    Text("Add code to the assistant when you're ready.", style = MaterialTheme.typography.caption, color = colors.onSurface.copy(alpha = .5f))
                }
            }
        }
        if (terminal) Column(Modifier.fillMaxWidth().heightIn(max = 250.dp).background(colors.surface).verticalScroll(rememberScrollState()).padding(12.dp)) { TerminalPanel() }
        Divider(color = colors.onSurface.copy(alpha = .08f))
        Text(workspace?.root?.toString() ?: "No folder open", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.caption,
            color = colors.onSurface.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    pending?.let { action -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Unsaved changes") }, text = { Text("Save your file first, or discard these edits before continuing.") },
        confirmButton = { TextButton({ pending = null; action() }) { Text("Discard and continue") } }, dismissButton = { TextButton({ pending = null }) { Text("Keep editing") } }) }
}
