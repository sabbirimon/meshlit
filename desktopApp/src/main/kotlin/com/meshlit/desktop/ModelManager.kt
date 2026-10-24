package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import javax.swing.JFileChooser

data class SavedModel(val path: String, val size: Long, val sha256: String) {
    fun validate() {
        require(path.length in 1..4096 && Path.of(path).isAbsolute && path.none(Char::isISOControl))
        require(size in 16..32L * 1024 * 1024 * 1024 && sha256.matches(Regex("[0-9a-f]{64}")))
    }
}
class ModelStore(private val directory: Path = Path.of(System.getProperty("meshlit.desktop.dataDir",
    Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString()))) {
    private val file get() = directory.resolve("models.json")
    fun load(): List<SavedModel> {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        require(!Files.isSymbolicLink(directory) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 262144)
        val root = boundedJson(Files.readString(file)); require(root["version"]!!.jsonPrimitive.int == 1)
        val models = root["models"]!!.jsonArray; require(models.size <= 64)
        return models.map { it.jsonObject.let { value -> SavedModel(value["path"]!!.jsonPrimitive.content, value["size"]!!.jsonPrimitive.long, value["sha256"]!!.jsonPrimitive.content).also(SavedModel::validate) } }
            .also { require(it.map(SavedModel::path).distinct().size == it.size) }
    }
    fun save(models: List<SavedModel>) {
        require(models.size <= 64 && models.map(SavedModel::path).distinct().size == models.size); models.forEach(SavedModel::validate)
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file)); Files.createDirectories(directory)
        if (Files.getFileAttributeView(directory, PosixFileAttributeView::class.java) != null) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        val temporary = Files.createTempFile(directory, "models-", ".tmp")
        try {
            if (Files.getFileAttributeView(temporary, PosixFileAttributeView::class.java) != null) Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"))
            Files.writeString(temporary, buildJsonObject {
                put("version", 1); putJsonArray("models") { models.forEach { model -> add(buildJsonObject { put("path", model.path); put("size", model.size); put("sha256", model.sha256) }) } }
            }.toString())
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
internal suspend fun inspectModel(path: Path, progress: (Long, Long) -> Unit): SavedModel {
    val context = currentCoroutineContext(); context.ensureActive()
    val normalized = path.toRealPath(); validateModel(normalized, false)
    val size = Files.size(normalized); val modified = Files.getLastModifiedTime(normalized)
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(normalized).use { stream ->
        val buffer = ByteArray(1024 * 1024); var checked = 0L
        while (true) {
            context.ensureActive(); val count = stream.read(buffer); if (count == -1) break
            digest.update(buffer, 0, count); checked += count; progress(checked, size)
        }
        require(checked == size && Files.size(normalized) == size && Files.getLastModifiedTime(normalized) == modified) { "Model changed during verification" }
    }
    context.ensureActive()
    return SavedModel(normalized.toString(), size, digest.digest().joinToString("") { "%02x".format(it) })
}

@Composable internal fun ModelManager(onLoad: (SavedModel) -> Unit, onBundled: () -> Unit) {
    val store = remember { ModelStore() }; val initial = remember { runCatching { store.load() } }
    var models by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
    var failure by remember { mutableStateOf(initial.isFailure) }; var scanning by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(0L) }; var total by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }; val scope = rememberCoroutineScope()
    Text("My models · verified file references", style = MaterialTheme.typography.h6)
    Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text(DesktopStarter.label); Text("Bundled · 1.12 GB · Apache-2.0 · text/chat/code · CPU · 4,096 context", style = MaterialTheme.typography.caption)
        TextButton(onBundled) { Text("Load bundled model") }
    } }
    models.forEach { item -> Surface(elevation = 1.dp, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
        Text(Path.of(item.path).fileName.toString()); Text(item.path, style = MaterialTheme.typography.caption)
        Text("%.2f GiB · SHA-256 %s".format(item.size / 1073741824.0, item.sha256.take(16)), style = MaterialTheme.typography.caption)
        Row {
            TextButton({ onLoad(item) }, enabled = !scanning) { Text("Verify & load") }
            TextButton({ runCatching { store.save(models.filterNot { it.path == item.path }) }.onSuccess { models = models.filterNot { it.path == item.path }; failure = false }.onFailure { failure = true } }, enabled = initial.isSuccess && !scanning) { Text("Forget reference") }
        }
    } } }
    Button({
        val chooser = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("GGUF model", "gguf"); isAcceptAllFileFilterUsed = false }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val path = chooser.selectedFile.toPath(); scanning = true; failure = false; checked = 0; total = 0
            job = scope.launch {
                try {
                    val item = withContext(Dispatchers.IO) { inspectModel(path) { read, size ->
                        runBlocking { withContext(Dispatchers.Main) { checked = read; total = size } }
                    } }
                    val updated = models.filterNot { it.path == item.path } + item
                    store.save(updated); models = updated
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failure = true }
                finally { scanning = false }
            }
        }
    }, enabled = initial.isSuccess && !scanning && models.size < 64) { Text("Add GGUF from disk · no duplicate copy") }
    if (scanning) {
        LinearProgressIndicator(if (total > 0) (checked.toDouble() / total).toFloat() else 0f, Modifier.fillMaxWidth())
        Text("Verified $checked / $total bytes")
        TextButton({ job?.cancel() }) { Text("Cancel verification") }
    }
    if (failure) Text("Model reference could not be read or saved. Existing records and weights are retained.", color = MaterialTheme.colors.error)
    Text("Recorded hashes detect later file changes; they do not establish publisher trust, licences or engine compatibility. Review the source before import. Forget removes only the reference, never your weights. Moving a file requires adding its new location.", style = MaterialTheme.typography.caption)
}
