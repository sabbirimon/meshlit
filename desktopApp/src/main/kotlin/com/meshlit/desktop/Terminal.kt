package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.*
import java.nio.file.*
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser

internal data class CommandOutput(val exit: Int, val stdout: String, val stderr: String, val truncated: Boolean)
/** Explicit human shell runner. Never registered as an agent/MCP tool or called by model output. */
internal class HumanCommandRunner : AutoCloseable {
    private val lock = Any()
    @Volatile private var child: Process? = null
    @Volatile private var closed = false
    suspend fun run(command: String, directory: Path, shell: Path): CommandOutput = withContext(Dispatchers.IO) {
        require(command.isNotBlank() && command.length <= 32768 && '\u0000' !in command)
        require(directory.isAbsolute && Files.isDirectory(directory) && shell.isAbsolute && Files.isExecutable(shell))
        val argv = if (System.getProperty("os.name").contains("Windows")) listOf(shell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command)
            else listOf(shell.toString(), "-c", command)
        val process = synchronized(lock) {
            check(!closed && child == null)
            ProcessBuilder(argv).directory(directory.toFile()).start().also { child = it }
        }
        try {
            process.outputStream.close() // No passwords or interactive input through this batch runner.
            coroutineScope {
                suspend fun read(input: java.io.InputStream) = async(Dispatchers.IO) {
                    input.use { stream ->
                        val saved = java.io.ByteArrayOutputStream(); val bytes = ByteArray(8192); var truncated = false
                        while (true) {
                            ensureActive(); val count = stream.read(bytes); if (count < 0) break
                            val keep = minOf(count, 65536 - saved.size()); if (keep > 0) saved.write(bytes, 0, keep)
                            if (keep < count) truncated = true
                        }
                        saved.toString(Charsets.UTF_8) to truncated
                    }
                }
                val stdout = read(process.inputStream); val stderr = read(process.errorStream)
                try {
                    val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
                    while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                        ensureActive(); check(System.nanoTime() < deadline) { "Command exceeded five minutes" }
                    }
                    withTimeout(TimeUnit.NANOSECONDS.toMillis((deadline-System.nanoTime()).coerceAtLeast(1)).coerceAtLeast(1)) {
                        val out = stdout.await(); val err = stderr.await()
                        CommandOutput(process.exitValue(), out.first, err.first, out.second || err.second)
                    }
                } finally { stop() }
            }
        } finally { stop() }
    }
    fun stop() = synchronized(lock) {
        val process = child ?: return@synchronized
        // Only this runner's child and its current descendants; no PID-based global process killing.
        val descendants = runCatching { process.descendants().use { it.toList() } }.getOrDefault(emptyList())
        descendants.asReversed().forEach { runCatching { it.destroy() } }; runCatching { process.destroy() }
        runCatching { if (!process.waitFor(500, TimeUnit.MILLISECONDS)) { descendants.asReversed().forEach { it.destroyForcibly() }; process.destroyForcibly() } }
        runCatching { process.inputStream.close(); process.errorStream.close(); process.outputStream.close() }; child = null
    }
    override fun close() { synchronized(lock) { closed = true; stop() } }
}

@Composable internal fun TerminalPanel() {
    val runner = remember { HumanCommandRunner() }; val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }; var busy by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }; var output by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf(System.getProperty("user.home")) }
    var shell by remember { mutableStateOf(if (System.getProperty("os.name").contains("Mac")) "/bin/zsh" else if (System.getProperty("os.name").contains("Windows")) "" else "/bin/sh") }
    DisposableEffect(Unit) { onDispose { job?.cancel(); runner.close() } }
    Text("Terminal · human commands", style = MaterialTheme.typography.h6)
    Text("Ready by default under your OS account. Run directly in this window; no model or agent can submit commands here. This bounded runner has no interactive PTY. Use Ghostty SSH for interactive remote sessions; local interactive embedding is tracked separately.")
    Text("User: ${System.getProperty("user.name")} · host: ${System.getProperty("os.name")} · five-minute limit · 64 KiB per output stream", style = MaterialTheme.typography.caption)
    OutlinedTextField(shell, { shell = it.take(4096) }, label = { Text("Absolute shell executable · choose PowerShell on Windows") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
    TextButton({ val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }; if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) directory = picker.selectedFile.toPath().toAbsolutePath().toString() }, enabled = !busy) { Text("Working directory: $directory") }
    OutlinedTextField(command, { command = it.take(32768) }, label = { Text("Command · runs when you press Run") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, maxLines = 12)
    Row {
        Button({ busy = true; output = ""; val text = command; val cwd = directory; val executable = shell
            job = scope.launch {
                try { val result = runner.run(text, Path.of(cwd), Path.of(executable)); output = "Exit ${result.exit}${if (result.truncated) " · output truncated" else ""}\n${result.stdout}\n${result.stderr}" }
                catch (e: CancellationException) { output = "Stopped"; throw e }
                catch (e: Exception) { output = "Command failed to start or exceeded its limit. Check shell, working directory and OS permissions." }
                finally { busy = false }
            }
        }, enabled = !busy && command.isNotBlank() && shell.isNotBlank()) { Text("Run") }
        TextButton({ job?.cancel(); runner.stop() }, enabled = busy) { Text("Stop") }
        TextButton({ output = "" }, enabled = !busy) { Text("Clear output") }
    }
    if (output.isNotBlank()) Text(output, fontFamily = FontFamily.Monospace)
    Text("Root/admin access uses the OS authentication flow in an interactive terminal. Meshlit does not store an administrator password or silently elevate commands. Command text/output stay in this panel's memory.", style = MaterialTheme.typography.caption)
}
