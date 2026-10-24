package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

enum class DeviceCategory(val label: String) { PHONE("Phone"), DESKTOP("Desktop"), CLUSTER("Cluster"), OTHER("Other") }
data class SavedNode(val id: String, val name: String, val category: DeviceCategory,
                     val endpoint: String, val protocol: HostProtocol, val lastConnectedAtMs: Long? = null, val lastResult: String? = null) {
    fun validate() {
        UUID.fromString(id)
        require(name.length in 1..100 && name == name.trim() && name.none(Char::isISOControl))
        HostEndpoint.parse(endpoint, protocol)
        require(lastConnectedAtMs == null || lastConnectedAtMs >= 0)
        require(lastResult == null || lastResult in setOf("ok","auth","timeout","network","response","tls","http"))
    }
}

/** Saved addresses only: no bearer keys, permission grants or inferred capabilities. */
class NodeStore(private val directory: Path = Path.of(System.getProperty("meshlit.desktop.dataDir",
    Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString()))) {
    private val file get() = directory.resolve("nodes.json")
    fun load(): List<SavedNode> {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(directory) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        require(Files.size(file) <= 262144)
        val root = boundedJson(Files.readString(file))
        require(root["version"]?.jsonPrimitive?.int in 1..2)
        val nodes = root["nodes"]!!.jsonArray
        require(nodes.size <= 64)
        return nodes.map { entry -> entry.jsonObject.let { node -> SavedNode(
            node["id"]!!.jsonPrimitive.content, node["name"]!!.jsonPrimitive.content,
            DeviceCategory.valueOf(node["category"]!!.jsonPrimitive.content),
            node["endpoint"]!!.jsonPrimitive.content, HostProtocol.valueOf(node["protocol"]!!.jsonPrimitive.content),
            node["lastConnectedAtMs"]?.jsonPrimitive?.longOrNull, node["lastResult"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
        ).also { it.validate() } } }.also { require(it.map(SavedNode::id).distinct().size == it.size) }
    }
    fun save(nodes: List<SavedNode>) {
        require(nodes.size <= 64 && nodes.map(SavedNode::id).distinct().size == nodes.size)
        nodes.forEach { it.validate() }
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file))
        Files.createDirectories(directory)
        fun privatePermissions(path: Path, permissions: String) {
            if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null)
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
        }
        privatePermissions(directory, "rwx------")
        val text = buildJsonObject {
            put("version", 2)
            putJsonArray("nodes") { nodes.forEach { node -> add(buildJsonObject {
                put("id", node.id); put("name", node.name); put("category", node.category.name)
                put("endpoint", HostEndpoint.parse(node.endpoint, node.protocol).base.toString()); put("protocol", node.protocol.name)
                node.lastConnectedAtMs?.let { put("lastConnectedAtMs",it) }; node.lastResult?.let { put("lastResult",it) }
            }) } }
        }.toString()
        val temporary = Files.createTempFile(directory, "nodes-", ".tmp")
        try {
            privatePermissions(temporary, "rw-------")
            Files.writeString(temporary, text)
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}

@Composable internal fun NodesPanel(onSelect: (SavedNode) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val store = remember { NodeStore() }
    val initial = remember { runCatching { store.load() } }
    var nodes by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
    var failure by remember { mutableStateOf(initial.isFailure) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var testToken by remember { mutableStateOf("") }
    var tokenOrigin by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DeviceCategory.PHONE) }
    var protocol by remember { mutableStateOf(HostProtocol.OPENAI) }
    fun test(text: String, kind: HostProtocol, node: SavedNode? = null) {
        val parsed = runCatching { HostEndpoint.parse(text.trim(), kind) }.getOrElse { diagnostic = it.message ?: "Invalid host address"; return }
        val key = if (tokenOrigin == parsed.base.toString()) testToken else ""
        if ((!parsed.loopback || kind != HostProtocol.OPENAI) && key.isBlank()) { diagnostic = "Authentication required for ${parsed.base}. Enter a key bound to this address, or Select & authenticate."; return }
        checking = true; diagnostic = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { HostClient(parsed, key).use { it.health() } }
                diagnostic = when (result) { is HostConnectionResult.Ready -> "${result.models.size} models reachable · ${parsed.base}"; is HostConnectionResult.Failed -> result.message }
                if (node != null) {
                    val updated = nodes.map { if (it.id == node.id) it.copy(lastConnectedAtMs = (result as? HostConnectionResult.Ready)?.observedAtMs ?: it.lastConnectedAtMs, lastResult = if (result is HostConnectionResult.Ready) "ok" else (result as HostConnectionResult.Failed).code) else it }
                    withContext(Dispatchers.IO) { store.save(updated) }; nodes = updated
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { diagnostic = "Cannot complete or save the connection check" }
            finally { checking = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nodes · phones, desktops & clusters", style = MaterialTheme.typography.h6)
        Text("Add the inference API address shown by your device or cluster. Select it, enter that host's client token in Settings, enable access and Connect. Saving an address grants no network or command permission.")
        Text("LAN and Internet endpoints require trusted HTTPS and authentication. Internet use needs a reachable HTTPS service or your own authenticated relay. A phone's private LAN address alone is not reachable over the Internet. This connects to models; SSH and pooled memory are separate capabilities.", style = MaterialTheme.typography.caption)
        if (failure) Text("Cannot read or save nodes. Check the address, name and local storage. Existing data is retained.", color = MaterialTheme.colors.error)
        nodes.forEach { node ->
            Surface(elevation = 1.dp) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${node.name} · ${node.category.label}")
                Text(node.endpoint, style = MaterialTheme.typography.caption)
                node.lastResult?.let { Text("Last check: $it${node.lastConnectedAtMs?.let { at -> " · " + java.time.Instant.ofEpochMilli(at) }.orEmpty()}", style = MaterialTheme.typography.caption) }
                Row {
                    TextButton({ test(node.endpoint, node.protocol, node) }, enabled = !checking) { Text("Test connection") }
                    TextButton({ onSelect(node) }) { Text("Select & authenticate") }
                    TextButton({
                        runCatching { store.save(nodes.filterNot { it.id == node.id }) }.onSuccess {
                            nodes = nodes.filterNot { it.id == node.id }; failure = false
                        }.onFailure { failure = true }
                    }, enabled = initial.isSuccess) { Text("Forget address") }
                }
            } }
        }
        Divider()
        Text("Easy add · select a connection type", style = MaterialTheme.typography.h6)
        Row {
            TextButton({ category = DeviceCategory.PHONE; protocol = HostProtocol.MESHLIT }) { Text("Meshlit phone") }
            TextButton({ category = DeviceCategory.CLUSTER; protocol = HostProtocol.OPENAI }) { Text("Remote cluster") }
            TextButton({ category = DeviceCategory.DESKTOP; protocol = HostProtocol.OPENAI }) { Text("Desktop host") }
        }
        Text("Use the host's reachable HTTPS API address and a scoped client key. Meshlit phone gateways use buffered replies; expose them only through your authenticated HTTPS transport. Saving does not deploy a tunnel or enroll a worker.", style = MaterialTheme.typography.caption)
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Device name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { DeviceCategory.entries.forEach { item -> TextButton({ category = item }) { Text((if (category == item) "✓ " else "") + item.label) } } }
        Row { HostProtocol.entries.forEach { item -> TextButton({ protocol = item }) { Text((if (protocol == item) "✓ " else "") + item.label) } } }
        OutlinedTextField(endpoint, { endpoint = it.take(2048); testToken = ""; tokenOrigin = null; diagnostic = null }, label = { Text("https://device:port${protocol.basePath}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(testToken, { testToken = it.take(8192); tokenOrigin = runCatching { HostEndpoint.parse(endpoint.trim(), protocol).base.toString() }.getOrNull() }, label = { Text("Test key for this endpoint · session only") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton({ test(endpoint,protocol) }, enabled = !checking && endpoint.isNotBlank()) { Text(if(checking) "Checking…" else "Test connection") }
        diagnostic?.let { Text(it, style = MaterialTheme.typography.caption) }
        TextButton({
            val pasted = clipboard.getText()?.text?.trim().orEmpty()
            runCatching { HostEndpoint.parse(pasted, protocol) }.onSuccess { endpoint = HostEndpoint.parse(pasted,protocol).base.toString(); testToken = ""; tokenOrigin = null; failure = false }.onFailure { failure = true }
        }) { Text("Paste API address") }
        Button({
            val node = SavedNode(UUID.randomUUID().toString(), name.trim(), category, runCatching { HostEndpoint.parse(endpoint.trim(),protocol).base.toString() }.getOrDefault(endpoint.trim()), protocol)
            runCatching { store.save(nodes + node) }.onSuccess {
                nodes = nodes + node; name = ""; endpoint = ""; failure = false
            }.onFailure { failure = true }
        }, enabled = initial.isSuccess && nodes.size < 64 && name.isNotBlank() && endpoint.isNotBlank()) { Text("Save device address") }
        Text("Addresses persist locally. Tokens and access approvals remain session-only. Device type is your label; model availability is verified when you connect.", style = MaterialTheme.typography.caption)
    }
}
