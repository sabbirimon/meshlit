package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.nio.ByteBuffer
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Base64
import javax.swing.JFileChooser

/** Terminal is external. Never pass a private key value, shell text, or ambient SSH configuration. */
internal object GhosttySsh {
    fun knownHost(host: String, port: Int, publicKey: String, pin: String): String {
        require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.:-]*")))
        require(port in 1..65535 && pin.matches(Regex("SHA256:[A-Za-z0-9+/]{43}")))
        require(publicKey.length in 1..8192 && publicKey.none(Char::isISOControl))
        val fields = publicKey.trim().split(Regex(" +"))
        require(fields.size >= 2 && fields[0] in setOf("ssh-ed25519", "ecdsa-sha2-nistp256", "ssh-rsa"))
        val wire = Base64.getDecoder().decode(fields[1]); require(wire.size in 16..6144)
        val data = ByteBuffer.wrap(wire); val size = data.int
        require(size in 1..64 && size <= data.remaining())
        val type = ByteArray(size).also { data.get(it) }.toString(Charsets.US_ASCII)
        require(type == fields[0])
        val actual = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(wire))
        require(MessageDigest.isEqual(actual.toByteArray(), pin.toByteArray())) { "Host public key does not match the independently verified pin" }
        return "${if (port == 22) host else "[$host]:$port"} ${fields[0]} ${fields[1]}\n"
    }
    fun arguments(terminal: Path, ssh: Path, host: String, port: Int, user: String, identity: Path, knownHosts: Path): List<String> {
        require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.:-]*")))
        require(user.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}")) && port in 1..65535)
        listOf(terminal, ssh, identity, knownHosts).forEach { require(it.isAbsolute && it.toString().none(Char::isISOControl)) }
        val options = listOf("StrictHostKeyChecking=yes", "UserKnownHostsFile=${knownHosts}", "GlobalKnownHostsFile=/dev/null",
            "IdentitiesOnly=yes", "IdentityAgent=none", "PreferredAuthentications=publickey", "AddKeysToAgent=no",
            "ClearAllForwardings=yes", "ForwardAgent=no", "ForwardX11=no", "PermitLocalCommand=no", "ProxyCommand=none",
            "ProxyJump=none", "UpdateHostKeys=no", "ConnectTimeout=15", "ServerAliveInterval=15", "ServerAliveCountMax=3")
        return listOf(terminal.toString(), "--config-default-files=false", "--shell-integration=none", "--clipboard-read=deny", "--clipboard-write=deny", "-e", ssh.toString(), "-F", "/dev/null", "-tt") +
            options.flatMap { listOf("-o", it) } + listOf("-i", identity.toString(), "-p", port.toString(), "-l", user, host)
    }
    fun launch(terminal: Path, ssh: Path, host: String, port: Int, user: String, identity: Path, publicKey: String, pin: String): Process {
        check(System.getProperty("os.name").let { it.contains("Mac") || it.contains("Linux") }) { "Ghostty GUI adapter currently supports macOS/Linux" }
        require(Files.isExecutable(terminal) && Files.isExecutable(ssh))
        require(Files.isRegularFile(identity, LinkOption.NOFOLLOW_LINKS) && Files.size(identity) in 1..1048576)
        // Public trust material only. Keep it for the external terminal's lifetime; never copy a private key.
        val directory = Path.of(System.getProperty("meshlit.desktop.dataDir", Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString()), "ssh-pins")
        require(!Files.isSymbolicLink(directory)); Files.createDirectories(directory)
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val trust = Files.createTempFile(directory, "ghostty-", ".known_hosts")
        try {
            if (Files.getFileStore(trust).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(trust, PosixFilePermissions.fromString("rw-------"))
            Files.writeString(trust, knownHost(host, port, publicKey, pin))
            val process = ProcessBuilder(arguments(terminal, ssh, host, port, user, identity, trust))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.onExit().thenRun { runCatching { Files.deleteIfExists(trust) } }
            return process
        } catch (e: Exception) { Files.deleteIfExists(trust); throw e }
    }
}

@Composable internal fun GhosttyPanel(host: String, port: String, user: String, pin: String) {
    var terminal by remember { mutableStateOf(if (System.getProperty("os.name").contains("Mac")) "/Applications/Ghostty.app/Contents/MacOS/ghostty" else "/usr/bin/ghostty") }
    var ssh by remember { mutableStateOf("/usr/bin/ssh") }
    var identity by remember { mutableStateOf("") }; var publicKey by remember { mutableStateOf("") }
    var allowed by remember(host, port, user, pin) { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
    Text("Ghostty · interactive SSH terminal", style = MaterialTheme.typography.h6)
    Text("Uses an installed Ghostty and OpenSSH with the target and pin above. Select an existing private-key file and paste the target's public host key. The terminal opens separately; close it or type exit there to end the SSH session. Meshlit's Stop and agent grants do not control this external session.")
    OutlinedTextField(terminal, { terminal = it.take(4096); allowed = false }, label = { Text("Installed Ghostty executable") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(ssh, { ssh = it.take(4096); allowed = false }, label = { Text("Installed OpenSSH executable") }, modifier = Modifier.fillMaxWidth())
    TextButton({ val chooser = JFileChooser(); if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) { identity = chooser.selectedFile.toPath().toAbsolutePath().toString(); allowed = false } }) { Text("Select private-key file") }
    Text(if (identity.isBlank()) "No key selected" else identity)
    OutlinedTextField(publicKey, { publicKey = it.take(8192); allowed = false }, label = { Text("Target public host key · must match SHA256 pin") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    Row { Checkbox(allowed, { allowed = it }); Text("Allow my interactive terminal on this target") }
    Button({ status = runCatching { GhosttySsh.launch(Path.of(terminal), Path.of(ssh), host, port.toInt(), user, Path.of(identity), publicKey, pin); "Ghostty launched. Connection and login outcome appear in its window." }.getOrElse { "Cannot launch: check executables, target, key file and matching host-key pin." }; allowed = false }, enabled = allowed && identity.isNotBlank()) { Text("Open Ghostty SSH") }
    if (status.isNotBlank()) Text(status)
    Text("Human-only. Ghostty is MIT licensed and is an optional external application; no Ghostty binary is bundled. Windows terminal support and embedding libghostty remain separate work.", style = MaterialTheme.typography.caption)
}
