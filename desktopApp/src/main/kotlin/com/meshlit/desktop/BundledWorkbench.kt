package com.meshlit.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Resolve resources relative to the installed launcher, independent of the workspace and cwd. */
internal class BundledWorkbench(private val root: Path, private val editor: Path) {
    fun configure(process: ProcessBuilder): ProcessBuilder {
        val java = root.resolve("toolchain")
        check(Files.isDirectory(java.resolve("bin"))) { "Studio development kit is incomplete. Extract the entire archive." }
        return process.apply {
            environment()["JAVA_HOME"] = java.toString()
            environment()["PATH"] = java.resolve("bin").toString() + File.pathSeparator + environment()["PATH"].orEmpty()
        }
    }

    fun command(arguments: List<String>) = configure(ProcessBuilder(listOf(editor.toString()) + arguments))

    fun open(folder: Path) {
        val profile = Path.of(System.getProperty("user.home"), ".meshlit", "workbench")
        command(listOf("--user-data-dir", profile.toString(), "--new-window", folder.toRealPath().toString())).start()
    }

    companion object {
        fun installed(): BundledWorkbench? {
            val launcher = System.getProperty("jpackage.app-path")?.let { Path.of(it).toRealPath() } ?: return null
            val os = System.getProperty("os.name").lowercase()
            val (root, relative) = when {
                os.startsWith("mac") -> launcher.parent.parent.parent.parent to "editor/VSCodium.app/Contents/MacOS/VSCodium"
                os.startsWith("windows") -> launcher.parent.parent to "editor/VSCodium.exe"
                else -> launcher.parent.parent.parent to "editor/codium"
            }
            if (!Files.isRegularFile(root.resolve("STUDIO-CONTENTS.json"))) return null
            val editor = root.resolve(relative)
            check(Files.isRegularFile(editor)) { "Bundled IDE is missing. Extract the entire Studio archive." }
            return BundledWorkbench(root, editor)
        }
    }
}
