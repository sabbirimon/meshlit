package com.meshlit.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.*
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable internal fun FrameWindowScope.StudioMenus(action: (String) -> Unit, quit: () -> Unit, coding: Boolean = false) {
    val mac = System.getProperty("os.name").startsWith("Mac")
    fun shortcut(key: Key, shift: Boolean = false) = KeyShortcut(key, meta = mac, ctrl = !mac, shift = shift)
    MenuBar {
        Menu("File") {
            Item("New chat", onClick = { action("new-chat") }, shortcut = shortcut(Key.N))
            Item("Open coding workspace", onClick = { action("code") })
            Item("Open folder…", onClick = { action("open-folder") }, shortcut = shortcut(Key.O))
            Item("Save file", onClick = { action("save-file") }, enabled = coding, shortcut = shortcut(Key.S))
            Separator()
            Item("Open project in VS Code…", onClick = { action("vscode") })
            Separator()
            Item("Quit Meshlit", onClick = quit, shortcut = shortcut(Key.Q))
        }
        Menu("Edit") {
            Item("Paste into chat draft", onClick = { action("paste-draft") })
            Item("Copy chat draft", onClick = { action("copy-draft") })
            Item("Clear chat draft", onClick = { action("clear-draft") })
        }
        Menu("View") {
            Item("Assistant mode & approvals", onClick = { action("assistant-controls") })
            Item("Model services", onClick = { action("providers") })
            Item("Local models", onClick = { action("models") })
            Item("Usage & budgets", onClick = { action("usage") })
            Item("Dashboards", onClick = { action("management") })
            Item("Settings", onClick = { action("appearance") }, shortcut = shortcut(Key.Comma))
            if(coding) { Separator(); Item("Focus editor", onClick = { action("focus-editor") }); Item("Show file explorer", onClick = { action("show-files") }) }
        }
        Menu("Terminal") { Item("Open command runner", onClick = { action("terminal") }) }
        Menu("Window") {
            Item("Minimize", onClick = { window.extendedState = java.awt.Frame.ICONIFIED })
            Item("Bring to front", onClick = { window.toFront() })
        }
        Menu("Help") { Item("About Meshlit", onClick = { action("about") }); Item("Meshlit guide", onClick = { action("guide") }); Item("VS Code documentation", onClick = { action("vscode-guide") }) }
    }
}

/** Opens an installed workbench through its registered file URL; never runs a shell or a project script. */
internal fun vscodeFolderUri(path: Path): java.net.URI {
    val folder = path.toRealPath()
    require(Files.isDirectory(folder)) { "Choose a project folder." }
    return java.net.URI("vscode", "file", folder.toString().replace('\\', '/').let { if(it.startsWith('/')) it else "/$it" }, null)
}
internal fun openVsCode(folder: Path? = null) {
    val selected = folder ?: JFileChooser().let { picker ->
        picker.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if(picker.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        picker.selectedFile.toPath()
    }
    java.awt.Desktop.getDesktop().browse(vscodeFolderUri(selected))
}
