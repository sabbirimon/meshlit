package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.lang.management.ManagementFactory
import java.nio.file.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFileChooser
import javax.swing.JOptionPane

@Composable internal fun DesktopMenu(selected: String, onSelect: (String) -> Unit, compact: Boolean = false, advanced: Boolean = false) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(query, { query = it.take(200) }, singleLine = true, placeholder = { Text("Find settings or features") }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val matches = DesktopSearch.settings().filter { query.isBlank() || DesktopSearch.find(listOf(it), query, SearchType.SETTINGS).isNotEmpty() }.map { entry -> DesktopDestinations.all.first { it.id == entry.id } }.filter { query.isNotBlank() || advanced || it.id in BASIC_DESKTOP_SECTIONS }
            val groups = matches.groupBy { desktopGroup(it.id) }
            groups.forEach { (group, entries) ->
                var expanded by remember(group, advanced) { mutableStateOf(!advanced || group in setOf("Models & inference", "Devices & clusters")) }
                TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) {
                    Text("${if (expanded || query.isNotBlank()) "▾" else "▸"} $group", Modifier.fillMaxWidth(), color = MaterialTheme.colors.primary)
                }
                if (expanded || query.isNotBlank()) entries.filter { query.isNotBlank() || it.available || advanced && !compact }.forEach { item ->
                    TextButton({ onSelect(item.id) }, Modifier.fillMaxWidth()) {
                        Text((if (selected == item.id) "• " else "") + item.title + (if (item.available) "" else " · port pending"), Modifier.fillMaxWidth(), color = MaterialTheme.colors.onSurface.copy(alpha = if (item.available) 1f else .5f))
                    }
                }
            }
        }
    }
}
internal fun desktopGroup(id: String) = when (id) {
    "discover", "models", "providers", "tokens", "guardrails", "dolphin", "behavior", "router", "training", "media", "colibri", "acceleration", "personalization", "engine" -> "Models & inference"
    "nodes", "device-dashboard", "radar", "meshchat", "device", "health", "memory", "monitor", "power", "external", "network", "p2p", "peers", "remotecommands", "sharding" -> "Devices & clusters"
    "agents", "hooks", "gateway", "runners", "gibberlink", "openclaw", "automation" -> "Agents & integrations"
    "ssh", "crypto", "securityprivacy", "securitylab", "firewall", "packets", "permissions", "audit" -> "Security & access"
    "terminal", "commands", "containers", "kubernetes", "hyperl", "runtime", "packages", "operations", "recovery", "logs", "termux" -> "Runtime & operations"
    "files", "ide", "tasks", "configuration", "search", "cloud", "crawler" -> "Workspace & services"
    else -> "Preferences & help"
}
internal val BASIC_DESKTOP_SECTIONS = setOf("rag", "crypto", "management", "terminal", "models", "discover", "providers", "appearance", "tokens", "nodes", "device-dashboard", "securityprivacy", "health", "search", "help", "legal", "about")

internal fun hostReadings(): List<Pair<String, String>> {
    fun bytes(value: Long?) = value?.takeIf { it >= 0 }?.let { "%.2f GiB".format(it / 1073741824.0) } ?: "Unavailable"
    val os = ManagementFactory.getOperatingSystemMXBean()
    val physical = os as? com.sun.management.OperatingSystemMXBean
    val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
    val disk = runCatching { Files.getFileStore(Path.of(System.getProperty("user.home"))) }.getOrNull()
    return listOf(
        "Host" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}",
        "Logical CPU processors" to os.availableProcessors.toString(),
        "System CPU load" to (physical?.cpuLoad?.takeIf { it >= 0 }?.let { "%.1f%%".format(it * 100) } ?: "Unavailable"),
        "Physical RAM total" to bytes(physical?.totalMemorySize),
        "Physical RAM free · OS reading" to bytes(physical?.freeMemorySize),
        "Java heap used / maximum" to "${bytes(heap.used)} / ${bytes(heap.max)}",
        "Home volume free / total" to "${bytes(disk?.usableSpace)} / ${bytes(disk?.totalSpace)}",
        "App uptime" to "${ManagementFactory.getRuntimeMXBean().uptime / 1000} seconds",
        "GPU/NPU and pooled memory" to "Not qualified by this desktop runtime",
        "Battery / temperature / thermal state" to "Unavailable from this adapter"
    )
}

@Composable internal fun HostMonitor() {
    var values by remember { mutableStateOf(hostReadings()) }
    Text("Measured host health, memory & storage", style = MaterialTheme.typography.h6)
    Text("Local OS/JVM readings. Java heap excludes native model memory; free physical RAM is not pooled cluster capacity.", style = MaterialTheme.typography.caption)
    Button({ values = hostReadings() }) { Text("Refresh readings") }
    values.forEach { (key, value) -> Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) { Text(key, style = MaterialTheme.typography.caption); Text(value) }
    } }
}

internal object DesktopCrypto {
    fun hash(text: String, algorithm: String): String {
        require(algorithm in setOf("SHA-256", "SHA-512") && text.length <= 65536)
        return MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun key() = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun transform(text: String, key: String, operation: String): String {
        require(text.length <= 131072 && key.length <= 100)
        val secret = Base64.getDecoder().decode(key).also { require(it.size == 32) }
        return when (operation) {
            "HMAC-SHA256" -> Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(secret, "HmacSHA256")); doFinal(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            }
            "Encrypt AES-GCM" -> {
                require(text.toByteArray(Charsets.UTF_8).size <= 32768)
                val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce)) }
                "meshlit-gcm-v1." + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
            }
            "Decrypt AES-GCM" -> {
                require(text.startsWith("meshlit-gcm-v1."))
                val payload = Base64.getDecoder().decode(text.removePrefix("meshlit-gcm-v1.")).also { require(it.size >= 28) }
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
                    doFinal(payload.copyOfRange(12, payload.size)).decodeToString(throwOnInvalidSequence = true)
                }
            }
            else -> error("Unsupported operation")
        }
    }
}

@Composable internal fun CryptoPanel() {
    var input by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Text("Local cryptography", style = MaterialTheme.typography.h6)
    Text("UTF-8 hashes, HMAC-SHA256 and AES-256-GCM with a fresh nonce. Input and keys stay in this panel's memory; no agent or network access. Keep the key separately to decrypt later.")
    OutlinedTextField(input, { input = it.take(65536) }, label = { Text("Text or encrypted envelope") }, modifier = Modifier.fillMaxWidth(), maxLines = 8)
    OutlinedTextField(key, { key = it.take(100) }, label = { Text("Base64 256-bit key · session only") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    Row { TextButton({ key = DesktopCrypto.key(); output = "" }) { Text("Generate key") }; TextButton({ clipboard.setText(AnnotatedString(key)) }, enabled = key.isNotBlank()) { Text("Copy key") } }
    listOf("SHA-256", "SHA-512", "HMAC-SHA256", "Encrypt AES-GCM", "Decrypt AES-GCM").chunked(3).forEach { group -> Row { group.forEach { operation -> TextButton({
        runCatching { if (operation.startsWith("SHA-")) DesktopCrypto.hash(input, operation) else DesktopCrypto.transform(input, key, operation) }
            .onSuccess { output = it; error = false }.onFailure { output = ""; error = true }
    }) { Text(operation) } } } }
    if (error) Text("Invalid key/envelope or authentication failed. No plaintext was returned.", color = MaterialTheme.colors.error)
    if (output.isNotBlank()) { OutlinedTextField(output, {}, readOnly = true, modifier = Modifier.fillMaxWidth(), maxLines = 10); TextButton({ clipboard.setText(AnnotatedString(output)) }) { Text("Copy result") } }
}

@Composable internal fun FilePanel() {
    var text by remember { mutableStateOf("") }; var name by remember { mutableStateOf("Untitled") }
    var query by remember { mutableStateOf("") }; var error by remember { mutableStateOf(false) }
    Text("Files & code workspace", style = MaterialTheme.typography.h6)
    Text("Open or edit a UTF-8 source/text file up to 1 MiB. Saving requires your chosen path. No execution, background indexing or upload.")
    Row {
        Button({
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) runCatching {
                val path = chooser.selectedFile.toPath(); require(Files.isRegularFile(path) && Files.size(path) <= 1048576)
                Files.readAllBytes(path).decodeToString(throwOnInvalidSequence = true).also { require('\u0000' !in it) }
            }.onSuccess { text = it; name = chooser.selectedFile.name; error = false }.onFailure { error = true }
        }) { Text("Open text file") }
        TextButton({
            val chooser = JFileChooser().apply { selectedFile = java.io.File(name) }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val path = chooser.selectedFile.toPath()
                val approved = !Files.exists(path) || JOptionPane.showConfirmDialog(null, "Replace ${path.fileName}?", "Save file", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                if (approved) runCatching { require(text.toByteArray(Charsets.UTF_8).size <= 1048576); Files.writeString(path, text) }.onSuccess { name = path.fileName.toString(); error = false }.onFailure { error = true }
            }
        }) { Text("Save as…") }
    }
    if (error) Text("File could not be read or saved. Use UTF-8 text within the size limit.", color = MaterialTheme.colors.error)
    Text(name)
    OutlinedTextField(query, { query = it.take(200) }, label = { Text("Find in file") }, singleLine = true)
    if (query.isNotBlank()) Text("${Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).count()} matches")
    OutlinedTextField(text, { text = it.take(1048576) }, modifier = Modifier.fillMaxWidth().height(420.dp), textStyle = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace))
}

@Composable internal fun FeatureStatus(section: String) {
    val item = DesktopDestinations.all.first { it.id == section }
    Text(item.title, style = MaterialTheme.typography.h6)
    Text(item.description)
    Text("Android implementation exists. Its desktop backend has not been ported or qualified in this build. This is an availability record, not an enabled feature.", color = MaterialTheme.colors.onSurface.copy(alpha = .7f))
    Text("The desktop port must preserve user scopes, credentials, cancellation and real device evidence before this control can run. No agent, SSH, VM or Internet permission is implied by opening this page.")
}
