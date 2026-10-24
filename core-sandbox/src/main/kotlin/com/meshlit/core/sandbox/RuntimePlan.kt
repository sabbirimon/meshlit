package com.meshlit.core.sandbox

import java.io.File

/** Selection never silently falls back to a backend with weaker isolation. */
enum class RuntimeMode {
    APP, ROOT, ROOT_CHROOT, PROOT, BUBBLEWRAP, VM_SSH,
}

data class RuntimeConfig(
    val mode: RuntimeMode = RuntimeMode.APP,
    val executable: String = "",
    val rootfs: String = "",
    val sshPort: Int = 2222,
    val sshUser: String = "meshlit",
    val sshIdentity: String = "",
    val sshKnownHosts: String = "",
)

/** Guest actions must use the forwarding port of the currently configured VM. */
fun RuntimeConfig.requireGuestBinding(vmPort:Int) {
    require(mode==RuntimeMode.VM_SSH && sshPort==vmPort) {"SSH configuration does not match the active VM forwarding port"}
}

data class RuntimePlan(
    val argv: List<String>,
    val workingDirectory: File,
    val environment: Map<String, String>,
    val isolation: String,
)

fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

fun tokenizeCommand(text: String): List<String> {
    val result = mutableListOf<String>()
    val word = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    for (char in text) {
        require(char != '\u0000' && char != '\n' && char != '\r') { "Invalid command character" }
        when {
            escaped -> { word.append(char); escaped = false; started = true }
            char == '\\' && quote != '\'' -> { escaped = true; started = true }
            quote != null -> if (char == quote) quote = null else word.append(char)
            char == '\'' || char == '"' -> { quote = char; started = true }
            char.isWhitespace() -> if (started) { result += word.toString(); word.setLength(0); started = false }
            else -> { word.append(char); started = true }
        }
    }
    require(quote == null && !escaped) { "Unclosed quote or escape" }
    if (started) result += word.toString()
    return result
}

class RuntimePlanner(private val workspace: File) {
    fun plan(config: RuntimeConfig, argv: List<String>, rootConsent: Boolean = false): RuntimePlan {
        require(argv.isNotEmpty() && argv.first().isNotBlank() && !argv.first().startsWith('-')) { "A command is required" }
        require(argv.all { '\u0000' !in it } && argv.sumOf { it.length } <= 32768) { "Command too large or invalid" }
        require(workspace.isDirectory) { "Runtime workspace is unavailable" }
        val environment = mapOf("HOME" to workspace.absolutePath, "TMPDIR" to workspace.absolutePath)
        fun binary(): String {
            val file = File(config.executable)
            require(file.isAbsolute && file.isFile && file.canExecute()) { "Configure an installed executable for ${config.mode}" }
            return file.canonicalPath
        }
        fun rootfs(): String {
            val file = File(config.rootfs)
            require(file.isAbsolute && file.isDirectory && File(file, "bin/sh").isFile) { "Configure a Linux rootfs with bin/sh" }
            return file.canonicalPath
        }
        val command: List<String>
        val isolation: String
        when (config.mode) {
            RuntimeMode.APP -> {
                command = argv
                isolation = "Android app UID; commands can access app data and permitted networks"
            }
            RuntimeMode.ROOT -> {
                require(rootConsent) { "Root execution requires explicit consent for this command" }
                command = listOf(binary(), "-c", "exec " + argv.joinToString(" ", transform = ::shellQuote))
                isolation = "Root host execution; no sandbox"
            }
            RuntimeMode.ROOT_CHROOT -> {
                require(rootConsent) { "Root chroot requires explicit consent for this command" }
                val args = listOf("chroot", rootfs()) + argv
                command = listOf(binary(), "-c", "exec " + args.joinToString(" ", transform = ::shellQuote))
                isolation = "Root chroot changes filesystem view; shares host kernel and privileges"
            }
            RuntimeMode.PROOT -> {
                command = listOf(binary(), "-r", rootfs(), "-w", "/", "--") + argv
                isolation = "PRoot filesystem compatibility; shares app UID/kernel/network, not a security boundary"
            }
            RuntimeMode.BUBBLEWRAP -> {
                command = listOf(binary(), "--unshare-all", "--die-with-parent", "--new-session",
                    "--ro-bind", rootfs(), "/", "--proc", "/proc", "--dev", "/dev",
                    "--tmpfs", "/tmp", "--bind", workspace.canonicalPath, "/workspace",
                    "--chdir", "/workspace", "--setenv", "HOME", "/tmp",
                    "--setenv", "PATH", "/usr/local/bin:/usr/bin:/bin", "--") + argv
                isolation = "Linux namespaces with read-only rootfs, private network and explicit workspace bind; requires kernel support"
            }
            RuntimeMode.VM_SSH -> {
                require(config.sshPort in 1024..65535) { "Invalid guest SSH port" }
                require(config.sshUser.matches(Regex("[a-z_][a-z0-9_-]{0,31}"))) { "Invalid guest username" }
                require(File(config.sshIdentity).isFile && File(config.sshKnownHosts).isFile) { "Configure guest identity and verified known_hosts files" }
                command = listOf(binary(), "-F", "/dev/null", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5",
                    "-o", "StrictHostKeyChecking=yes", "-o", "IdentitiesOnly=yes", "-o", "ForwardAgent=no",
                    "-o", "ClearAllForwardings=yes", "-o", "UserKnownHostsFile=${config.sshKnownHosts}",
                    "-i", config.sshIdentity, "-p", config.sshPort.toString(),
                    "${config.sshUser}@127.0.0.1", "exec " + argv.joinToString(" ", transform = ::shellQuote))
                isolation = "Loopback SSH into a guest; isolation depends on the running VM configuration"
            }
        }
        return RuntimePlan(command, workspace, environment, isolation)
    }
}
