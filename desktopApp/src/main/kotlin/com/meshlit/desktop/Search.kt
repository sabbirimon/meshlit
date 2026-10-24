package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path
import java.util.Locale

internal enum class SearchType(val label: String) {
    ALL("All"), SETTINGS("Settings & features"), CHAT("Current chat"), MODELS("Saved models"), DEVICES("Saved devices")
}
internal data class SearchEntry(val id: String, val type: SearchType, val title: String, val text: String,
                                val destination: String, val available: Boolean = true)
internal data class SearchHit(val entry: SearchEntry, val excerpt: String)
internal object DesktopSearch {
    private val aliases = mapOf(
        "firewall" to "security firewall inbound outbound egress rules protection",
        "gateway" to "A2A agent to agent MCP API client hub tools",
        "runtime" to "VM virtual machine sandbox QEMU isolation",
        "network" to "VPN LAN Internet remote overseas networking pairing connection",
        "nodes" to "add device phone desktop server cluster NAS IoT",
        "monitor" to "CPU RAM memory resource health graphs charts gauges",
        "tasks" to "process PID task manager CPU RAM RSS",
        "recovery" to "self healing auto repair restart reset recovery rollback checkpoints",
        "discover" to "Hugging Face HF model download library free paid gated",
        "ssh" to "SSH terminal Ghostty remote shell inbound outbound",
        "providers" to "Ollama LM Studio Open WebUI Jan API provider credentials",
        "containers" to "Docker Podman incubator image volume container",
        "kubernetes" to "Kubernetes k8s namespace deployment cluster",
        "guardrails" to "censored uncensored policy model native instructions",
        "appearance" to "theme colour color font style language English Chinese",
        "agents" to "bots automation permissions tools agent management",
    )
    fun settings() = DesktopDestinations.all.map {
        SearchEntry(it.id, SearchType.SETTINGS, it.title,
            "${desktopGroup(it.id)} · ${it.description} ${aliases[it.id].orEmpty()}", it.id, it.available)
    }
    fun entries(chat: List<Pair<String, String>>, models: List<SavedModel>, nodes: List<SavedNode>): List<SearchEntry> =
        settings() + chat.takeLast(256).mapIndexed { index, (role, text) ->
            SearchEntry("chat-$index", SearchType.CHAT, "$role · message ${index + 1}", text.take(131072), "chat")
        } + models.take(64).map { SearchEntry(it.sha256, SearchType.MODELS,
            Path.of(it.path).fileName.toString(), "${it.path} · ${it.size} bytes · SHA-256 ${it.sha256}", "models") } +
        nodes.take(64).map { SearchEntry(it.id, SearchType.DEVICES, it.name,
            "${it.category.label} · ${it.endpoint} · ${it.protocol.label}", "nodes") }

    fun words(query: String) = query.take(256).trim().lowercase(Locale.ROOT).split(Regex("\\s+"))
        .filter(String::isNotEmpty).distinct().take(16)
    fun find(entries: List<SearchEntry>, query: String, type: SearchType): List<SearchHit> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        return entries.asSequence().filter { type == SearchType.ALL || type == it.type }.mapNotNull { entry ->
            val title = entry.title.lowercase(Locale.ROOT)
            val text = entry.text.lowercase(Locale.ROOT)
            if (words.any { it !in title && it !in text }) return@mapNotNull null
            val score = words.sumOf { if (it in title) 4 else 1 }
            val offset = words.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
            val start = (offset - 90).coerceAtLeast(0)
            val excerpt = (if (start > 0) "…" else "") + entry.text.drop(start).take(420) +
                (if (entry.text.length > start + 420) "…" else "")
            score to SearchHit(entry, excerpt)
        }.sortedByDescending { it.first }.take(100).map { it.second }.toList()
    }
    fun webUri(query: String): URI {
        require(query.isNotBlank() && query.length <= 256 && query.none(Char::isISOControl))
        return URI("https://duckduckgo.com/?q=" + URLEncoder.encode(query, Charsets.UTF_8))
    }
}

@Composable internal fun SearchPanel(chat: List<Pair<String, String>>, onNavigate: (String, String) -> Unit) {
    var query by remember { mutableStateOf("") }; var type by remember { mutableStateOf(SearchType.ALL) }
    var models by remember { mutableStateOf(emptyList<SavedModel>()) }; var nodes by remember { mutableStateOf(emptyList<SavedNode>()) }
    var errors by remember { mutableStateOf(emptyList<String>()) }; var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }; var web by remember { mutableStateOf(false) }
    var webFailed by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) {
        loading = true
        try {
            val loaded = withContext(Dispatchers.IO) { runCatching { ModelStore().load() } to runCatching { NodeStore().load() } }
            models = loaded.first.getOrDefault(emptyList()); nodes = loaded.second.getOrDefault(emptyList())
            errors = buildList { if (loaded.first.isFailure) add("models"); if (loaded.second.isFailure) add("devices") }
        } finally { loading = false }
    }
    val entries = remember(chat, models, nodes) { DesktopSearch.entries(chat, models, nodes) }
    val hits = remember(entries, query, type) { DesktopSearch.find(entries, query, type) }
    Text("Global search", style = MaterialTheme.typography.h6)
    Text("Search every settings category, this session's chat, saved model references and saved device addresses. Local results stay on this computer; no disk crawl or requests to other devices.", style = MaterialTheme.typography.caption)
    OutlinedTextField(query, { query = it.take(256) }, label = { Text("Find anything in this workspace") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    SearchType.entries.chunked(3).forEach { group -> Row { group.forEach { scope ->
        TextButton({ type = scope }) { Text((if (type == scope) "✓ " else "") + scope.label) }
    } } }
    TextButton({ refresh++ }, enabled = !loading) { Text(if (loading) "Reading saved references…" else "Refresh models & devices") }
    if (errors.isNotEmpty()) Text("Could not read saved ${errors.joinToString(" and ")}. Settings/chat results remain available; stored data was retained.", color = MaterialTheme.colors.error)
    if (query.isNotBlank()) Text("${hits.size} matches${if (hits.size == 100) " · first 100 shown" else ""}", style = MaterialTheme.typography.caption)
    hits.forEach { hit -> Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${hit.entry.type.label} · ${if (hit.entry.available) "available" else "desktop port pending"}", style = MaterialTheme.typography.caption)
            SearchHighlight(hit.entry.title, query); SearchHighlight(hit.excerpt, query)
            TextButton({ onNavigate(hit.entry.destination, if (hit.entry.type == SearchType.CHAT) query else "") }) {
                Text(if (hit.entry.type == SearchType.CHAT) "Show matching chat" else "Open management page")
            }
        }
    } }
    Divider()
    Row { Checkbox(web, { web = it; webFailed = false }); Text("Allow this search in a browser") }
    Text("When you click Search web, the query is sent to DuckDuckGo in your browser. This does not fetch articles into chat, index remote settings or authorize agent browsing.", style = MaterialTheme.typography.caption)
    Button({ runCatching { java.awt.Desktop.getDesktop().browse(DesktopSearch.webUri(query)) }.onFailure { webFailed = true } }, enabled = web && query.isNotBlank()) { Text("Search web ↗") }
    if (webFailed) Text("The browser could not open. Check the query and your OS browser configuration.", color = MaterialTheme.colors.error)
}

@Composable private fun SearchHighlight(text: String, query: String) {
    val words = DesktopSearch.words(query); val color = MaterialTheme.colors.primary
    val annotated = remember(text, query, color) { buildAnnotatedString {
        append(text)
        // Regex IGNORE_CASE preserves offsets even for Unicode case mappings.
        words.forEach { word -> Regex(Regex.escape(word), RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
        } }
    } }
    Text(annotated)
}

@Composable internal fun ManagementPanel(onNavigate: (String) -> Unit) {
    Text("Management dashboards", style = MaterialTheme.typography.h6)
    Text("Choose a category. Each feature page reports its actual desktop availability; opening a page grants no agent, network or administrative permission.", style = MaterialTheme.typography.caption)
    val groups = remember { DesktopDestinations.all.filter { it.id != "management" }.groupBy { desktopGroup(it.id) }.toList() }
    var expanded by remember { mutableStateOf<String?>(null) }
    groups.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { (category, entries) ->
            Surface(elevation = 2.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(category, style = MaterialTheme.typography.subtitle1)
                    Text("${entries.count { it.available }} available · ${entries.count { !it.available }} pending", style = MaterialTheme.typography.caption)
                    if (expanded == category) entries.forEach { item -> TextButton({ onNavigate(item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(item.title + if (item.available) "" else " · pending", modifier = Modifier.fillMaxWidth())
                    } } else Text(entries.take(3).joinToString(" · ") { it.title }, style = MaterialTheme.typography.caption)
                    TextButton({ expanded = if (expanded == category) null else category }) {
                        Text(if (expanded == category) "Collapse ↑" else "Manage category →")
                    }
                }
            }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    } }
}
