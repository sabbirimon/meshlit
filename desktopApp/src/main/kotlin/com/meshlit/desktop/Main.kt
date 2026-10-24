package com.meshlit.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.meshlit.workspace.*
import com.meshlit.workspace.richtext.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

private data class Message(val role: String, val text: String, val completed: Boolean = true)
fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "--cpu-benchmark-check") { cpuBenchmarkCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--engine-options-check") { engineOptionsCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--monitor-check") { monitorCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--hub-check") { hubCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--render-management-check") { renderManagementCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--render-check") { renderCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--hyperl-check") { hyperLCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--local-check") { localCheck(args[1]); return }
    if (args.size == 2 && args[0] == "--models-check") { modelsCheck(args[1]); return }
    if (args.firstOrNull() == "--cli") { runCli(args.drop(1).toTypedArray()); return }
    application {
    Window(onCloseRequest = ::exitApplication, title = "Meshlit · Experimental desktop",
        state = rememberWindowState(width = 1120.dp, height = 800.dp)) { Workspace() }
    }
}
@Composable internal fun Workspace() {
    val preferences = remember { DesktopPreferences() }
    var language by remember { mutableStateOf(preferences.language()) }
    var look by remember { mutableStateOf(preferences.look()) }
    var scale by remember { mutableStateOf(preferences.scale()) }
    var advanced by remember { mutableStateOf(preferences.advanced()) }
    fun t(en: String, zh: String) = language.text(en, zh)
    var preferencesError by remember { mutableStateOf(false) }
    fun save() { preferencesError = runCatching { preferences.save(language, look, scale) }.isFailure }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var prompt by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var hostProtocol by remember { mutableStateOf(HostProtocol.OPENAI) }
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var colibriHost by remember { mutableStateOf(false) }
    var colibriMode by remember { mutableStateOf(ColibriMode.OFF) }
    var observedAt by remember { mutableStateOf<Long?>(null) }
    val localEngine = remember { LocalEngine() }
    val knowledge = remember { DesktopKnowledge() }
    val enginePreferences = remember { EnginePreferences() }
    var engineOptions by remember { mutableStateOf(enginePreferences.load()) }
    var sendAfterLoad by remember { mutableStateOf(false) }
    var localMode by remember { mutableStateOf(true) }
    var localSession by remember { mutableStateOf<LocalSession?>(null) }
    var customModel by remember { mutableStateOf<java.nio.file.Path?>(null) }
    var customModelHash by remember { mutableStateOf<String?>(null) }
    var localLoading by remember { mutableStateOf(false) }
    var network by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var model by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf(2048f) }
    var settings by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("models") }
    var responsePolicy by remember { mutableStateOf(ResponsePolicy.ASSISTANT) }
    var instructions by remember { mutableStateOf(DesktopStarter.prompt) }
    fun openSection(id: String) { section = id; settings = true }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(GenerationUsage()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var client by remember { mutableStateOf<HostClient?>(null) }
    var revision by remember { mutableStateOf(0) }
    fun stop(unloadLocal: Boolean = false) {
        revision++; sendAfterLoad = false; client?.close(); job?.cancel(); client = null
        if (unloadLocal || localMode && busy) { localEngine.stop(); localSession = null; connected = false; models = emptyList(); model = "" }
        busy = false; localLoading = false
    }
    fun loadLocal() {
        stop(true); failure = false; busy = true; localLoading = true
        val currentRevision = revision
        val selected = customModel
        val selectedHash = customModelHash
        val selectedOptions = engineOptions
        job = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { localEngine.start(selected, selectedHash, selectedOptions) }
                if (revision != currentRevision) throw CancellationException("Load superseded")
                localSession = loaded; connected = true; models = listOf(DesktopStarter.alias); model = DesktopStarter.alias
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == currentRevision) failure = true }
            finally { if (revision == currentRevision) { busy = false; localLoading = false } }
        }
    }
    fun selectGguf() {
        val picker = javax.swing.JFileChooser().apply {
            dialogTitle = t("Choose a trusted GGUF model", "选择可信的 GGUF 模型")
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("GGUF model", "gguf")
            isAcceptAllFileFilterUsed = false
        }
        if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            stop(true); messages = emptyList(); usage = GenerationUsage(); customModel = picker.selectedFile.toPath(); customModelHash = null
        }
    }
    fun requestsAllowed() = if (localMode) localSession != null && connected else network && connected
    DisposableEffect(Unit) { onDispose { client?.close(); job?.cancel(); localEngine.close(); knowledge.lock() } }
    fun colibriReady() = connected && observedAt?.let { System.nanoTime() - it in 0..300_000_000_000L } == true
    fun hostAllowed() = localMode || !colibriHost || colibriDecision(colibriMode, network, colibriReady(), false).route == ColibriRoute.HOST
    fun connect() {
        if (!network || colibriHost && colibriMode == ColibriMode.OFF) return
        stop(); failure = false; connected = false; models = emptyList(); model = ""; busy = true
        val currentRevision = revision
        job = scope.launch {
            var active: HostClient? = null
            try {
                val host = HostEndpoint.parse(endpoint, hostProtocol); val connectedClient = HostClient(host, token); active = connectedClient; client = connectedClient
                val choices = withContext(Dispatchers.IO) { connectedClient.models() }
                if (revision != currentRevision) throw CancellationException("Request superseded")
                models = choices; model = choices.firstOrNull().orEmpty(); connected = true; observedAt = System.nanoTime()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == currentRevision) failure = true }
            finally { active?.close(); if (revision == currentRevision) { client = null; busy = false } }
        }
    }
    fun send() {
        if (busy || !requestsAllowed() || !hostAllowed() || prompt.isBlank()) return
        val system = if (localMode) runCatching { responsePolicy.localPrompt(instructions) }.getOrElse { failure = true; return } else null
        if (localMode && budget.toInt() >= (localSession?.context ?: engineOptions.context)) { failure = true; return }
        val context = messages.filter { it.completed }.map { ChatTurn(it.role, it.text) } + ChatTurn("user", prompt.trim())
        if (context.size + (if (system == null) 0 else 1) > 64 || context.sumOf { it.content.length.toLong() } + (system?.length ?: 0) > 131072) { failure = true; return }
        messages = messages + Message("user", prompt.trim()) + Message("assistant", "", false)
        prompt = ""; usage = GenerationUsage(); failure = false; busy = true
        val currentRevision = revision
        job = scope.launch {
            var active: HostClient? = null
            try {
                val session = if (localMode) checkNotNull(localSession) else null
                val generationClient = HostClient(session?.endpoint ?: HostEndpoint.parse(endpoint, hostProtocol), session?.token ?: token); active = generationClient; client = generationClient
                val reply = ReplyBuffer()
                val updates = MutableStateFlow("")
                val display = launch {
                    updates.collect { text ->
                        if (revision == currentRevision && text.isNotEmpty()) messages = messages.dropLast(1) + messages.last().copy(text = text)
                    }
                }
                val result = try {
                    withContext(Dispatchers.IO) {
                        val references = knowledge.context(context.last().content, remote = !localMode)
                        val groundedSystem = (system.orEmpty() + references).takeIf { it.isNotBlank() }
                        generationClient.generateConfigured(model, context, GenerationBudget(budget.toInt()), groundedSystem) { chunk ->
                            reply.append(chunk)?.let { updates.value = it }
                        }
                    }
                } finally { display.cancelAndJoin() }
                if (revision != currentRevision) throw CancellationException("Request superseded")
                messages = messages.dropLast(1) + messages.last().copy(text = reply.snapshot(), completed = true); usage = result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == currentRevision) failure = true }
            finally { active?.close(); if (revision == currentRevision) { client = null; busy = false } }
        }
    }
    LaunchedEffect(localSession, busy, engineOptions.idleMinutes) {
        if (localSession != null && !busy) {
            val startedIdle = System.nanoTime()
            while (true) {
                delay(2000)
                if (!localEngine.isRunning()) { stop(true); failure = true; break }
                if (engineOptions.idleMinutes > 0 && System.nanoTime()-startedIdle >= engineOptions.idleMinutes*60_000_000_000L) {
                    stop(true); break
                }
            }
        }
    }
    LaunchedEffect(localSession, busy, sendAfterLoad) {
        if (sendAfterLoad && !busy) {
            sendAfterLoad = false
            if (localSession != null) send()
        }
    }
    fun requestSend() {
        if (localMode && localSession == null && !busy && prompt.isNotBlank()) { loadLocal(); sendAfterLoad = true }
        else send()
    }
    val colors = if (look == WorkspaceLook.PAPER) lightColors(primary = Color(look.accent), background = Color(look.background),
        surface = Color(look.surface), onBackground = Color(look.foreground), onSurface = Color(look.foreground))
        else darkColors(primary = Color(look.accent), background = Color(look.background), surface = Color(look.surface),
            onBackground = Color(look.foreground), onSurface = Color(look.foreground), onPrimary = Color(0xFF101113))
    MaterialTheme(colors = colors, typography = Typography(defaultFontFamily = FontFamily.SansSerif,
        body1 = TextStyle(fontSize = (16 * scale).sp, lineHeight = (25 * scale).sp))) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            BoxWithConstraints {
                val wide = maxWidth > 860.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide) WorkspaceRail(if(settings) section else "chat", onSelect = { id ->
                        if (id == "chat") settings = false else openSection(id)
                    }, onNewChat = { stop(); messages = emptyList(); usage = GenerationUsage(); settings = false })
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        WorkspaceHeader(
                            title = if (localMode) customModel?.fileName?.toString() ?: DesktopStarter.label else model.ifBlank { "Meshlit" },
                            status = if (localMode) if (localSession != null) "Offline · ${localSession?.backend} · ready" else if (localLoading) "Verifying and loading local model…" else "Offline model · load when ready"
                                else if (connected) "Connected · selected host" else "Remote host · awaiting connection",
                            onSettings = { settings = true },
                            onLoad = if(localMode) ({ if(localSession == null) loadLocal() else stop(true) }) else null,
                            loadLabel = if(localSession == null) "Load" else "Unload", busy = busy,
                            onNew = { stop(); messages = emptyList(); usage = GenerationUsage() },
                            onSearch = { openSection("search") }, onManage = { openSection("management") })
                        if (messages.isNotEmpty()) OutlinedTextField(query, { query = it.take(256) }, label = { Text(t("Search this chat", "搜索当前对话")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), singleLine = true)
                        if (failure) Text(if (localMode) t("Local model failed. Check available memory, GGUF compatibility and packaged engine/model integrity. Try Unload and Load; partial replies are excluded from future requests.", "本地模型失败。请检查可用内存、GGUF 兼容性及引擎和模型完整性。尝试卸载再加载；未完成回复不会用于后续请求。") else t("Request failed. Check the host, trusted TLS, client token and context size. A partial reply is excluded from the next request.", "请求失败。请检查主机、可信 TLS、客户端令牌和上下文长度。未完成的回复不会发送至下一次请求。"), Modifier.padding(24.dp), color = colors.error)
                        if (messages.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✦", fontSize = 48.sp, color = colors.primary)
                            Spacer(Modifier.height(20.dp)); Text(t("What should we work on?", "我们开始做什么？"), style = MaterialTheme.typography.h4)
                            Spacer(Modifier.height(12.dp)); Text(if (localMode) t("Load the bundled Qwen model to chat offline, or select a GGUF in Settings.", "加载内置 Qwen 模型离线聊天，或在设置中选择 GGUF。") else t("Choose a trusted Meshlit or OpenAI-compatible host in Settings.", "请在设置中选择可信的 Meshlit 或 OpenAI 兼容主机。"), color = colors.onSurface.copy(alpha = .65f))
                            Spacer(Modifier.height(20.dp))
                            HeroMenu(::t, onChat = { stop(); messages = emptyList(); usage = GenerationUsage() }, onModels = { openSection("models") }, onStyle = { openSection("appearance") })
                        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            itemsIndexed(messages.filter { query.isBlank() || it.text.contains(query, true) }) { _, message ->
                                if (message.role == "user") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, modifier = Modifier.widthIn(max = 560.dp)) { SelectionContainer { Text(message.text, Modifier.padding(18.dp)) } }
                                } else Column(Modifier.fillMaxWidth().widthIn(max = 860.dp)) {
                                    if (message.text.isEmpty()) Text(t("Generating…", "正在生成…"), color = colors.onSurface.copy(alpha = .65f))
                                    RichReply(message.text, scale)
                                    val clipboard = LocalClipboardManager.current
                                    Row { TextButton({ clipboard.setText(AnnotatedString(message.text)) }, enabled = message.text.isNotEmpty()) { Text(t("Copy", "复制")) }
                                        if (!message.completed) Text(t("Incomplete", "未完成"), Modifier.padding(12.dp), style = MaterialTheme.typography.caption) }
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = .35f))) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TextField(prompt, { prompt = it.take(131072) }, placeholder = { Text(t("Ask Meshlit", "向 Meshlit 提问")) }, modifier = Modifier.weight(1f), maxLines = 6,
                                        colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                                    Button(if (busy) { { stop() } } else ::requestSend, enabled = busy || (prompt.isNotBlank() && (localMode && localEngine.available() || requestsAllowed() && hostAllowed() && model.isNotBlank())), shape = RoundedCornerShape(22.dp)) { Text(if (busy) t("Stop", "停止") else t("Send ↑", "发送 ↑")) }
                                }
                            }
                            Text(t("Reported output tokens", "报告的输出令牌") + ": ${usage.outputTokens ?: t("unknown", "未知")} · " + t("End-to-end tokens/s", "端到端令牌/秒") + ": ${usage.tokensPerSecond?.let { "%.2f".format(it) } ?: t("unknown", "未知")}", style = MaterialTheme.typography.caption, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
            if (settings) DialogWindow(onCloseRequest = { settings = false }, title = t("Meshlit Studio · settings & tools", "Meshlit Studio · 设置与工具"), state = rememberDialogState(width = 1060.dp, height = 800.dp)) {
                Surface(Modifier.fillMaxSize(), color = colors.background) {
                    Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(260.dp).fillMaxHeight().padding(12.dp)) {
                        Row {
                            TextButton({ advanced = false; if (section !in BASIC_DESKTOP_SECTIONS) section = "models"; runCatching { preferences.saveAdvanced(false) }.onFailure { preferencesError = true } }) { Text((if (!advanced) "✓ " else "") + "Basic") }
                            TextButton({ advanced = true; runCatching { preferences.saveAdvanced(true) }.onFailure { preferencesError = true } }) { Text((if (advanced) "✓ " else "") + "Advanced") }
                        }
                        Text(if (advanced) "Developer tools and Android port status" else "Daily chat, models and connections", style = MaterialTheme.typography.caption)
                        Box(Modifier.weight(1f)) { DesktopMenu(section, { section = it }, advanced = advanced) }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val destination = DesktopDestinations.all.first { it.id == section }
                    Text(destination.title, style = MaterialTheme.typography.h5)
                    if (section in setOf("appearance", "models", "providers", "colibri", "tokens", "permissions")) {
                        if (section == "appearance") {
                        MenuHeading("style", t("Make it yours", "个性化设置"))
                        Row { WorkspaceLanguage.entries.forEach { l -> TextButton({ language = l; save() }) { Text((if (language == l) "✓ " else "") + l.label) } } }
                        Row { WorkspaceLook.entries.forEach { l -> TextButton({ look = l; save() }) { Text((if (look == l) "✓ " else "") + l.label) } } }
                        Text(t("Text size", "字体大小")); Slider(scale, { scale = it }, valueRange = .85f..1.5f, onValueChangeFinished = ::save)
                        if (preferencesError) Text(t("Appearance could not be saved.", "无法保存外观设置。"), color = colors.error)
                        }
                        if (section in setOf("models", "providers", "colibri", "permissions")) {
                        MenuHeading("models", t("Model source", "模型来源"))
                        Row {
                            RadioButton(localMode, { stop(true); localMode = true; messages = emptyList() }); Text(t("Local offline", "本地离线"))
                            RadioButton(!localMode, { stop(true); localMode = false; messages = emptyList() }); Text(t("Remote host", "远程主机"))
                        }
                        if (localMode) {
                            ModelRecommendations(onLoad = { item ->
                                stop(true); customModel = java.nio.file.Path.of(item.path); customModelHash = item.sha256
                                messages = emptyList(); loadLocal(); settings = false
                            })
                            ModelManager(onLoad = { item ->
                                stop(true); customModel = java.nio.file.Path.of(item.path); customModelHash = item.sha256
                                messages = emptyList(); loadLocal(); settings = false
                            }, onBundled = {
                                stop(true); customModel = null; customModelHash = null; messages = emptyList(); loadLocal(); settings = false
                            })
                            Text(customModel?.fileName?.toString() ?: DesktopStarter.label, fontWeight = FontWeight.Bold)
                            Text(t("Bundled Qwen: 1.12 GB · text/chat/code · ${engineOptions.context}-token configured context · CPU. A small model can make mistakes; it has no web or device tools. Imported GGUFs depend on engine support and available RAM.", "内置 Qwen：1.12 GB · 文本/聊天/代码 · 4,096 令牌上下文 · CPU。小模型可能出错；没有网络或设备工具。导入 GGUF 取决于引擎支持和可用内存。"), style = MaterialTheme.typography.caption)
                            Row {
                                Button(::loadLocal, enabled = !busy) { Text(t("Load", "加载")) }
                                TextButton({ stop(true) }) { Text(t("Unload", "卸载")) }
                                TextButton(::selectGguf, enabled = !busy) { Text(t("Select GGUF", "选择 GGUF")) }
                                TextButton({ stop(true); customModel = null; customModelHash = null; messages = emptyList() }, enabled = !busy) { Text(t("Use bundled", "使用内置模型")) }
                            }
                            TextButton({ java.awt.Desktop.getDesktop().browse(java.net.URI("https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF")) }) { Text(t("Download GGUFs · open Hugging Face", "下载 GGUF · 打开 Hugging Face")) }
                            Text(t("Download a trusted .gguf in your browser, choose it here, then Load. Files stay at your chosen location; no duplicate import or automatic download. .safetensors/.bin require conversion outside this app. Unload or closing the app stops its private local process. Stop during generation also unloads it.", "在浏览器中下载可信的 .gguf，在这里选择后加载。文件保留原位置，不复制、不自动下载。.safetensors/.bin 需要在应用外转换。卸载或关闭应用会停止私有本地进程；生成时停止也会卸载模型。"), style = MaterialTheme.typography.caption)
                        } else {
                        MenuHeading("models", t("Explicit host access", "明确授权主机访问"))
                        Row {
                            listOf("LM Studio" to "http://127.0.0.1:1234/v1", "Ollama" to "http://127.0.0.1:11434/v1", "Open WebUI" to "http://127.0.0.1:3000/api").forEach { (label, url) ->
                                TextButton({ stop(true); hostProtocol = if (label == "Open WebUI") HostProtocol.OPEN_WEBUI else HostProtocol.OPENAI; endpoint = url; token = ""; network = false; colibriHost = false; messages = emptyList() }) { Text(label) }
                            }
                        }
                        Text(if (hostProtocol == HostProtocol.OPEN_WEBUI) t("Open WebUI API: /api. Obtain your account API key from that host's Settings → Account. The administrator must enable it. This connects to your existing host; it does not install or rebrand Open WebUI.", "Open WebUI API：/api。请从该主机设置 → 账户获取 API 密钥，管理员需要启用。此连接使用现有主机，不安装或重新品牌化 Open WebUI。") else t("OpenAI-compatible API: /v1. LM Studio/Ollama must already be installed and serving the selected model. No automatic installation or remote discovery.", "OpenAI 兼容 API：/v1。LM Studio/Ollama 需要已安装并提供所选模型。不自动安装或发现远程主机。"), style = MaterialTheme.typography.caption)
                        Text(t("The selected host may forward to a cloud provider or execute its configured server-side tools. Meshlit's local tool grants do not control that host; review its configuration.", "所选主机可能转发到云服务或执行其服务端工具。Meshlit 的本地工具授权不控制该主机，请检查主机配置。"), style = MaterialTheme.typography.caption)
                        if (!localMode && !colibriHost) Row { HostProtocol.entries.forEach { protocol ->
                            TextButton({ stop(true); hostProtocol = protocol; endpoint = ""; token = ""; network = false; connected = false; messages = emptyList() }) { Text((if (hostProtocol == protocol) "✓ " else "") + protocol.label) }
                        } }
                        if (hostProtocol == HostProtocol.MESHLIT) Text("Meshlit nodes use authenticated buffered replies. Use a scoped client key and a reachable trusted HTTPS /v1 service. Worker enrollment and remote command permissions are separate.", style = MaterialTheme.typography.caption)
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(colibriHost, { stop(true); colibriHost = it; connected = false; observedAt = null; hostProtocol = HostProtocol.OPENAI; network = false; token = ""; endpoint = ""; messages = emptyList() }); Text(t("Use Colibri host", "使用 Colibri 主机")) }
                        if (colibriHost) {
                            Row { ColibriMode.entries.forEach { mode -> TextButton({ stop(); colibriMode = mode; if (mode == ColibriMode.OFF) connected = false }) { Text((if (colibriMode == mode) "✓ " else "") + mode.name) } } }
                            Text(t("Auto uses this explicitly authorized, recently observed Colibri host while remote mode is selected. Refresh expires after 5 minutes. No server or model is installed. Agent switching is supported in the Android Experimental node with separate user grants; this preview has no agent controller.", "选择远程模式时，自动模式使用已授权且近期确认的 Colibri 主机。刷新记录在五分钟后失效，不会安装服务器或模型。代理切换需要 Android 实验性节点中的单独授权；本预览没有代理控制器。"), style = MaterialTheme.typography.caption)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(network, { network = it; if (!it) { stop(); connected = false; models = emptyList(); model = "" } }); Text(t("Allow requests to the selected host", "允许向所选主机发送请求")) }
                        Text(t("Prompts and your token go to this host. TLS uses the system trust store; redirects are blocked. HTTP is limited to literal loopback. Host model operation is managed separately.", "提示词和令牌将发送至此主机。TLS 使用系统信任库，并禁止重定向。HTTP 仅限本机回环地址。主机模型需要单独管理。"), style = MaterialTheme.typography.caption)
                        OutlinedTextField(endpoint, { stop(true); endpoint = it.take(2048); network = false; token = ""; connected = false; model = ""; models = emptyList(); messages = emptyList() }, label = { Text("https://host:port${hostProtocol.basePath}") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(token, { stop(); token = it.take(8192); connected = false }, label = { Text(t("Client token · session only", "客户端令牌 · 仅当前会话")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Button(::connect, enabled = network && !busy && endpoint.isNotBlank() && (!colibriHost || colibriMode != ColibriMode.OFF)) { Text(t("Connect / refresh models", "连接 / 刷新模型")) }
                        models.forEach { id -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(model == id, { model = id }); Text(id) } }
                        if (connected && models.isEmpty()) Text(t("The host reports no models.", "主机未报告可用模型。"))
                        }
                        }
                        if (section == "tokens") {
                        Text("Last response: ${usage.outputTokens ?: "unknown"} output tokens · ${usage.tokensPerSecond?.let { "%.2f".format(it) } ?: "unknown"} end-to-end tokens/s")
                        Text("Local context: ${engineOptions.context} tokens; leave room for the prompt and output. Inference engine settings control context and CPU/KV options.")
                        Text(t("Output limit", "输出上限") + ": ${budget.toInt()} tokens"); Slider(budget, { budget = it }, valueRange = 64f..4096f, steps = 62)
                        Text(t("Actual throughput appears only when the host reports token usage; it includes request latency. Language affects UI only.", "仅在主机报告令牌用量时显示吞吐量；此值包含请求延迟。语言设置仅影响界面。"), style = MaterialTheme.typography.caption)
                        }
                    } else when (section) {
                        "engine" -> {
                            if (preferencesError) Text("Engine settings could not be saved; previous values are retained.", color = colors.error)
                            EnginePanel(engineOptions, localSession) { options ->
                            stop(true); messages = emptyList(); usage = GenerationUsage(); sendAfterLoad = false
                            runCatching { options.validate(); enginePreferences.save(options) }.onSuccess { engineOptions = options; budget = minOf(budget, (options.context / 2).toFloat()); preferencesError = false }.onFailure { preferencesError = true }
                            }
                        }
                        "nodes", "network" -> NodesPanel { node ->
                            stop(true); localMode = false; endpoint = node.endpoint; hostProtocol = node.protocol
                            token = ""; network = false; colibriHost = false; messages = emptyList(); section = "providers"
                        }
                        "device-dashboard" -> DeviceDashboardPanel(onNodes = { section = "nodes" }, onMonitor = { section = "monitor" })
                        "securityprivacy" -> {
                            Text("Connection access is opt-in for each Studio session. Saved device addresses are not approved workers or authenticated identities.")
                            Text("Current remote access: ${if (network && connected && !localMode) "connected" else if (network && !localMode) "allowed for this session" else "off"}")
                            Text("Current request: ${if (busy) "running" else "idle"}. Session token: ${if (token.isBlank()) "none entered" else "entered, held in memory"}.")
                            Button({ stop(true); network = false; token = ""; observedAt = null; connected = false }) { Text("Revoke session access and stop") }
                            TextButton({ section = "providers" }) { Text("Review host connection") }
                            TextButton({ section = "guardrails" }) { Text("Review response policy") }
                            TextButton({ section = "nodes" }) { Text("Review saved addresses") }
                            Text("Chat history, host keys and response policy are session-only. Device addresses and appearance persist locally. Remote hosts may retain prompts under their own policy.", style = MaterialTheme.typography.caption)
                        }
                        "health", "memory", "device", "monitor", "power", "acceleration", "tasks" -> ResourceMonitor { stop(true) }
                        "crypto" -> { com.meshlit.desktop.peering.PeerWalletPanel(); Divider(); CryptoPanel() }
                        "hyperl" -> HyperLPanel()
                        "ssh" -> SshPanel()
                        "terminal", "commands" -> TerminalPanel()
                        "files", "ide" -> FilePanel()
                        "discover" -> HuggingFacePanel()
                        "rag" -> KnowledgePanel(knowledge)
                        "securitylab" -> SecuritySkillsPanel()
                        "behavior", "guardrails" -> {
                            Text("Local model instructions · session only")
                            ResponsePolicy.entries.forEach { policy ->
                                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(responsePolicy == policy, { stop(); responsePolicy = policy; messages = emptyList(); usage = GenerationUsage() }); Text(policy.label) }
                            }
                            OutlinedTextField(instructions, { stop(); instructions = it.take(8192); messages = emptyList() }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth(), maxLines = 12, enabled = responsePolicy == ResponsePolicy.CUSTOM)
                            Text("Changing policy starts a fresh conversation. Model native omits Meshlit’s system message; it does not remove learned refusals or provider restrictions. Model-specific alignment comes from the selected weights. Network, SSH, agent and authentication permissions remain independently enforced.")
                        }
                        "operations" -> {
                            Text("Current request: ${if (busy) "running" else "idle"}")
                            Text("Local process: ${if (localSession != null) "loaded" else "unloaded"}")
                            Button({ stop(true) }) { Text("Stop request & unload local model") }
                            Text("This stops the process owned by Meshlit. Remote host lifecycle, agent jobs and other devices remain managed by their own authorised controllers.")
                        }
                        "management" -> ManagementPanel { section = it }
                        "search" -> SearchPanel(messages.map { it.role to it.text }) { destination, chatQuery ->
                            if (destination == "chat") { query = chatQuery; settings = false }
                            else section = destination
                        }
                        "help" -> {
                            Text("Offline: Models → Local offline → Load. Import a trusted GGUF with Select GGUF. The bundled Qwen file is verified in full before loading.")
                            Text("Add a phone/PC/cluster: Nodes → enter its trusted HTTPS inference address and type → Save → Select & authenticate → enter client token → enable access → Connect. Then choose a reported model. The target must already expose a compatible API.")
                            Text("Existing LM Studio/Ollama/Open WebUI: Models → Remote host → choose preset, review the host, add its key where required, allow requests and Connect. Open WebUI uses /api; others use /v1.")
                            Text("Stop cancels generation; local Stop also unloads. Closing the app stops its private model server. Node addresses and appearance persist; chats and keys are session-only.")
                        }
                        "legal" -> {
                            Text("Meshlit application: Apache-2.0. Qwen starter: Apache-2.0. llama.cpp: MIT and its retained dependency notices. Java runtime: GPL-2.0 with Classpath Exception; matching source accompanies the installers.")
                            Text("Optional services keep their own licences. Open WebUI's branding conditions apply to its source/UI; Meshlit implements its API independently. HyperL components retain their separate terms.")
                            Text("Offline chat makes no web requests. Connecting sends prompts and session keys to the selected host, which may use its own cloud providers or tools. No telemetry or agent execution is enabled by default.")
                        }
                        "about" -> {
                            Text("Meshlit Studio · Intel desktop build 40 · experimental")
                            Text("Bundled Qwen2.5 1.5B Instruct Q4_K_M · llama.cpp CPU · portable Java 21")
                            Text("Android menu inventory has been audited. ${DesktopDestinations.all.count { it.available }} desktop sections provide scoped functions or actual host readings. Remaining Android backends are explicitly listed as awaiting a desktop port.")
                            Text("This build is not full Android feature parity or production qualification. GPU, P2P, agents, training, recovery, voice and VM require their respective desktop implementations and tests. SSH currently supports pinned exec and an inbound status node; remote qualification remains.")
                        }
                        else -> FeatureStatus(section)
                    }
                        TextButton({ settings = false }) { Text(t("Done", "完成")) }
                    }
                    }
                }
            }
        }
    }
}
@Composable private fun RichReply(text: String, scale: Float) {
    val document = remember(text) { formatReply(text) }
    val colors = MaterialTheme.colors
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            document.blocks.forEach { block -> when (block) {
                is ReplyBlock.Prose -> {
                    val annotated = buildAnnotatedString { block.spans.forEach { s -> withStyle(SpanStyle(fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (s.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                        fontFamily = if (s.code) FontFamily.Monospace else null, color = if (s.url != null) colors.primary else Color.Unspecified)) { append(s.text) } } }
                    Row(Modifier.padding(start = (block.indent.coerceAtMost(4) * 12).dp)) {
                        block.marker?.let { Text("$it ") }
                        Text(annotated, fontSize = ((when (block.heading) { 1 -> 30; 2 -> 25; 3 -> 21; else -> 16 }) * scale).sp,
                            lineHeight = ((if (block.heading > 0) 34 else 25) * scale).sp, fontWeight = if (block.heading > 0) FontWeight.Bold else null)
                    }
                }
                is ReplyBlock.Code -> Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        val clipboard = LocalClipboardManager.current
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(block.language.ifBlank { "text" }, Modifier.weight(1f), style = MaterialTheme.typography.caption)
                            TextButton({ clipboard.setText(AnnotatedString(block.text)) }) { Text("Copy / 复制") } }
                        Text(block.text, Modifier.horizontalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = (14 * scale).sp)
                    }
                }
                is ReplyBlock.Table -> Column(Modifier.horizontalScroll(rememberScrollState())) {
                    (listOf(block.header) + block.rows).forEachIndexed { index, row ->
                        Row { row.forEach { cell -> Text(cell.joinToString("") { it.text }, Modifier.width(200.dp).padding(12.dp), fontWeight = if (index == 0) FontWeight.Bold else null) } }; Divider()
                    }
                }
                is ReplyBlock.Chart -> Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text(block.title, fontWeight = FontWeight.Bold)
                    val maximum = block.values.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
                    block.labels.zip(block.values).forEach { (label, value) ->
                        Text("$label · $value ${block.unit}", style = MaterialTheme.typography.caption)
                        LinearProgressIndicator((kotlin.math.abs(value) / maximum).toFloat(), Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
                ReplyBlock.Rule -> Divider()
            } }
        }
    }
}
