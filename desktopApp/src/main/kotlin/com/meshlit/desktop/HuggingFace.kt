package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.nio.file.*
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

internal data class HubFile(val name: String, val size: Long, val sha256: String)
internal data class HubModel(val id: String, val revision: String, val licence: String, val gated: String, val files: List<HubFile>)
internal class HubClient(private val token: String = "") : AutoCloseable {
    init { require(token.length <= 8192 && token.none(Char::isISOControl)) }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val timer = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "meshlit-hub-deadline").apply { isDaemon = true } }
    @Volatile private var pending: CompletableFuture<HttpResponse<java.io.InputStream>>? = null
    private val closed = AtomicBoolean(false)
    @Volatile private var body: java.io.InputStream? = null
    private fun request(url: URI): HttpResponse<java.io.InputStream> {
        check(!closed.get()); require(url.scheme == "https" && url.userInfo == null)
        val builder = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30))
        if (url.host == "huggingface.co" && token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        val future = http.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream()); pending = future
        val response = try { future.get(30, TimeUnit.SECONDS) } catch (e: Exception) { future.cancel(true); throw e } finally { pending = null }
        body = response.body(); if (closed.get()) { body?.close(); error("Hub request revoked") }
        return response
    }
    private fun metadata(url: URI): String {
        val result = request(url)
        val expired = AtomicBoolean(false)
        val timeout = timer.schedule({ expired.set(true); runCatching { result.body().close() } }, 30, TimeUnit.SECONDS)
        try {
            return result.body().use {
                require(result.statusCode() == 200) { "Hub HTTP ${result.statusCode()}; body withheld" }
                val bytes = it.readNBytes(262145); require(bytes.size <= 262144 && !expired.get() && !closed.get())
                bytes.decodeToString(throwOnInvalidSequence = true)
            }
        } finally { timeout.cancel(false) }
    }
    fun search(query: String): List<String> {
        require(query.length in 1..200)
        val text = metadata(URI("https://huggingface.co/api/models?search=${encode(query)}&filter=gguf&limit=20"))
        return boundedJson("{\"data\":$text}")["data"]!!.jsonArray.also { require(it.size <= 20) }
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }.onEach(::validateRepository)
    }
    fun details(id: String): HubModel {
        validateRepository(id)
        val root = boundedJson(metadata(URI("https://huggingface.co/api/models/${path(id)}?blobs=true")))
        val revision = root["sha"]!!.jsonPrimitive.content.also { require(it.matches(Regex("[0-9a-f]{40}"))) }
        val files = root["siblings"]!!.jsonArray.also { require(it.size <= 4096) }.mapNotNull { entry ->
            val value = entry.jsonObject; val name = value["rfilename"]!!.jsonPrimitive.content
            if (!name.endsWith(".gguf", true) || name.contains(Regex("-\\d{5}-of-\\d{5}\\.gguf$"))) return@mapNotNull null
            validateFile(name)
            val lfs = value["lfs"] as? JsonObject ?: return@mapNotNull null
            val size = lfs["size"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val sha = lfs["sha256"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (size !in 16..32L * 1024 * 1024 * 1024 || !sha.matches(Regex("[0-9a-f]{64}"))) return@mapNotNull null
            HubFile(name, size, sha)
        }
        val card = root["cardData"] as? JsonObject
        return HubModel(id, revision, card?.get("license")?.toString()?.take(100) ?: "Not reported",
            root["gated"]?.toString()?.take(40) ?: "Unknown", files)
    }
    suspend fun download(model: HubModel, file: HubFile, directory: Path, progress: (Long, Long) -> Unit): SavedModel {
        validateRepository(model.id); validateFile(file.name)
        require(model.revision.matches(Regex("[0-9a-f]{40}")) && file.sha256.matches(Regex("[0-9a-f]{64}")) && file.size in 16..32L * 1024 * 1024 * 1024)
        require(!Files.isSymbolicLink(directory)); Files.createDirectories(directory)
        val destination = directory.resolve(file.sha256 + ".gguf")
        if (Files.exists(destination)) return inspectModel(destination, progress).also { require(it.sha256 == file.sha256 && it.size == file.size) }
        require(Files.getFileStore(directory).usableSpace >= file.size + 1024L * 1024 * 1024) { "Insufficient free disk headroom" }
        val temporary = Files.createTempFile(directory, "download-", ".part")
        val context = currentCoroutineContext()
        try {
            var url = URI("https://huggingface.co/${path(model.id)}/resolve/${model.revision}/${path(file.name)}")
            var response = request(url); var redirects = 0
            while (response.statusCode() in setOf(301,302,303,307,308)) {
                context.ensureActive(); response.body().close(); require(++redirects <= 5)
                url = url.resolve(response.headers().firstValue("Location").orElseThrow())
                require(allowedHubDownload(url)) { "Unapproved download redirect" }
                response = request(url) // Never forwards the HF bearer to its CDN.
            }
            require(response.statusCode() == 200)
            response.headers().firstValueAsLong("Content-Length").ifPresent { require(it == file.size) }
            val digest = MessageDigest.getInstance("SHA-256"); var read = 0L
            val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30)
            val activity = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
            val expired = AtomicBoolean(false)
            val stream = response.body()
            val timeout = timer.scheduleAtFixedRate({
                if (System.nanoTime() >= deadline || System.nanoTime() - activity.get() >= TimeUnit.SECONDS.toNanos(120)) {
                    expired.set(true); runCatching { stream.close() }
                }
            }, 1, 1, TimeUnit.SECONDS)
            try { stream.use { input -> Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    context.ensureActive(); check(!closed.get() && System.nanoTime() < deadline)
                    val count = input.read(buffer); check(!expired.get()); activity.set(System.nanoTime()); if (count == -1) break
                    read += count; require(read <= file.size); output.write(buffer, 0, count); digest.update(buffer, 0, count); progress(read, file.size)
                }
            } } } finally { timeout.cancel(false) }
            require(read == file.size && digest.digest().joinToString("") { "%02x".format(it) } == file.sha256)
            validateModel(temporary, false); context.ensureActive(); check(!closed.get())
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            return SavedModel(destination.toAbsolutePath().toString(), file.size, file.sha256)
        } finally { Files.deleteIfExists(temporary) }
    }
    override fun close() { closed.set(true); pending?.cancel(true); runCatching { body?.close() }; timer.shutdownNow(); http.shutdownNow() }
    companion object {
        fun validateRepository(id: String) { require(id.length <= 200 && id.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) && id.split('/').none { it in setOf(".", "..") }) }
        fun validateFile(name: String) { require(name.length in 1..500 && name.none { it.isISOControl() || it == '\\' } && name.split('/').all { it.isNotBlank() && it !in setOf(".", "..") }) }
        private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
        private fun path(text: String) = text.split('/').joinToString("/") { encode(it) }
        fun allowedHubDownload(url: URI): Boolean = url.scheme == "https" && url.userInfo == null && url.fragment == null && url.port in setOf(-1,443) &&
            url.host in setOf("huggingface.co", "cdn-lfs.huggingface.co", "cdn-lfs.hf.co", "cdn-lfs-us-1.hf.co", "cdn-lfs-eu-1.hf.co", "cas-bridge.xethub.hf.co")
    }
}

@Composable internal fun HuggingFacePanel() {
    var allowed by remember { mutableStateOf(false) }; var token by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }; var ids by remember { mutableStateOf(emptyList<String>()) }
    var selected by remember { mutableStateOf<HubModel?>(null) }; var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }; var downloaded by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }; var verified by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }; var client by remember { mutableStateOf<HubClient?>(null) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    fun stop() { revision++; client?.close(); job?.cancel(); client = null; busy = false }
    DisposableEffect(Unit) { onDispose { client?.close(); job?.cancel() } }
    fun run(action: suspend (HubClient) -> Unit) {
        stop(); busy = true; failure = false
        val active = HubClient(token); client = active; val currentRevision = revision
        job = scope.launch {
            try { action(active) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == currentRevision) failure = true }
            finally { active.close(); if (revision == currentRevision) { client = null; busy = false } }
        }
    }
    Text("Discover & download · Hugging Face", style = MaterialTheme.typography.h6)
    Text("GGUF repository search, revision-pinned single-file downloads and full SHA-256 verification. Publisher licences and account access apply. Public weights are not a guarantee of free hosted inference.")
    Row { Checkbox(allowed, { allowed = it; if (!it) stop() }); Text("Allow Hugging Face metadata and reviewed model/CDN downloads") }
    OutlinedTextField(token, { stop(); token = it.take(8192); allowed = false }, label = { Text("Optional HF read token · this service only · session only") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search GGUF models") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row {
        Button({ run { active -> ids = withContext(Dispatchers.IO) { active.search(query.trim()) }; selected = null } }, enabled = allowed && !busy && query.isNotBlank()) { Text("Search Hugging Face") }
        TextButton(::stop, enabled = busy) { Text("Cancel") }
    }
    ids.forEach { id -> TextButton({ run { active -> selected = withContext(Dispatchers.IO) { active.details(id) } } }, enabled = allowed && !busy) { Text(id) } }
    selected?.let { model ->
        Text("${model.id} · revision ${model.revision.take(12)}")
        Text("Licence: ${model.licence} · gated: ${model.gated}")
        TextButton({ java.awt.Desktop.getDesktop().browse(URI("https://huggingface.co/${model.id}")) }) { Text("Read publisher's model card & access terms") }
        if (model.files.isEmpty()) Text("No single-file GGUF with verified size/SHA metadata was reported. Split models and missing metadata are not auto-imported.")
        model.files.forEach { file ->
            Text("${file.name} · ${formatMemory(file.size)}")
            TextButton({ run { active ->
                val directory = Path.of(System.getProperty("meshlit.desktop.dataDir", Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString())).resolve("weights")
                val item = withContext(Dispatchers.IO) { active.download(model, file, directory) { read, size ->
                    runBlocking { withContext(Dispatchers.Main) { progress = (read.toDouble() / size).toFloat(); verified = read } }
                } }
                val store = ModelStore(); val models = store.load(); store.save(models.filterNot { it.path == item.path } + item)
                downloaded = "Verified ${file.name}; now listed in Models. Load remains your choice."
            } }, enabled = allowed && !busy) { Text("Download & verify this file") }
        }
    }
    if (busy) { LinearProgressIndicator(progress, Modifier.fillMaxWidth()); Text("Received $verified bytes; file becomes available only after full verification.") }
    if (failure) Text("Hub operation failed: check account access, metadata, trusted redirect, free disk or checksum. No failed file was activated.", color = MaterialTheme.colors.error)
    if (downloaded.isNotBlank()) Text(downloaded)
    Text("Gated/private files require your legitimate read token and publisher approval. No automatic payment or gated-access acceptance. Download cancellation removes incomplete staging; resume is not implemented in this build.", style = MaterialTheme.typography.caption)
}
