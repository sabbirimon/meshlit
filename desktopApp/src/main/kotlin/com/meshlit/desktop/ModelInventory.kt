package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * Honest model inventory. Reads the bundled model from the running JAR's
 * `models/bundled-model.json`, SHA-256 verifies the on-disk file, lists
 * imported models from the local store, and probes a loopback Ollama if
 * one is reachable. **Never invents a model**: every row is either a
 * real probe result or a structured `null` with a reason.
 *
 * The output of [collect] is a single `JsonObject` that the
 * `--models-check` launcher flag writes to disk as the
 * "before / after" evidence for the D0 acceptance gate.
 */

internal data class BundleModelStatus(
    val present: Boolean,
    val sizeOk: Boolean,
    val hashOk: Boolean,
    val sha256: String,
    val sizeBytes: Long,
    val expectedSizeBytes: Long,
    val expectedSha256: String,
    val id: String?,
    val publisher: String?,
    val quantization: String?,
    val parameters: String?,
    val license: String?,
    val filename: String?,
)

internal data class ImportedModelStatus(
    val name: String,
    val path: String,
    val present: Boolean,
    val sizeOk: Boolean,
    val hashOk: Boolean,
    val sha256: String?,
    val sizeBytes: Long?,
    val expectedSha256: String?,
    val expectedSizeBytes: Long?,
)

internal data class OllamaModel(
    val name: String,
    val sizeBytes: Long?,
    val digest: String?,
    val parameterSize: String?,
    val quantizationLevel: String?,
    val contextLength: Long?,
    val loaded: Boolean,
)

internal data class OllamaStatus(
    val reachable: Boolean,
    val endpoint: String,
    val modelCount: Int,
    val models: List<OllamaModel>,
    val probeAt: Instant,
    val reason: String?,
)

internal class ModelInventory(
    private val resourceBundleManifest: String = "/models/bundled-model.json",
    private val importedFile: Path = Path.of(
        System.getProperty("meshlit.desktop.dataDir",
            Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString()),
        "models.json"
    ),
    private val loopbackProbe: URI = URI("http://127.0.0.1:11434"),
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun collect(): JsonObject = buildJsonObject {
        put("scope", "desktop model inventory: bundled, imported, loopback Ollama")
        put("collectedAt", Instant.now().toString())
        put("bundle", bundle().toJson())
        put("imported", buildJsonArray { imported().forEach { add(it.toJson()) } })
        put("ollama", ollama().toJson())
    }

    private fun bundle(): BundleModelStatus {
        val manifestText = readResourceText(resourceBundleManifest)
            ?: return BundleModelStatus(
                present = false, sizeOk = false, hashOk = false, sha256 = "", sizeBytes = 0L,
                expectedSizeBytes = 0L, expectedSha256 = "", id = null, publisher = null,
                quantization = null, parameters = null, license = null, filename = null,
            )
        val manifest = runCatching { boundedJson(manifestText) }.getOrNull()
            ?: return BundleModelStatus(
                present = false, sizeOk = false, hashOk = false, sha256 = "", sizeBytes = 0L,
                expectedSizeBytes = 0L, expectedSha256 = "", id = null, publisher = null,
                quantization = null, parameters = null, license = null, filename = null,
            )
        val filename = manifest["filename"]?.jsonPrimitive?.contentOrNull
        val expectedSize = manifest["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L
        val expectedSha = manifest["sha256"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = manifest["id"]?.jsonPrimitive?.contentOrNull
        val publisher = manifest["publisher"]?.jsonPrimitive?.contentOrNull
        val quantization = manifest["quantization"]?.jsonPrimitive?.contentOrNull
        val parameters = manifest["parameters"]?.jsonPrimitive?.contentOrNull
        val license = manifest["license"]?.jsonPrimitive?.contentOrNull
        val file = filename?.let { locateBundledFile(it) }
        if (file == null || !Files.exists(file)) {
            return BundleModelStatus(
                present = false, sizeOk = false, hashOk = false, sha256 = "", sizeBytes = 0L,
                expectedSizeBytes = expectedSize, expectedSha256 = expectedSha, id = id, publisher = publisher,
                quantization = quantization, parameters = parameters, license = license, filename = filename,
            )
        }
        val actualSize = Files.size(file)
        val actualSha = sha256Of(file)
        return BundleModelStatus(
            present = true,
            sizeOk = expectedSize == 0L || actualSize == expectedSize,
            hashOk = expectedSha.isEmpty() || actualSha.equals(expectedSha, ignoreCase = true),
            sha256 = actualSha,
            sizeBytes = actualSize,
            expectedSizeBytes = expectedSize,
            expectedSha256 = expectedSha,
            id = id, publisher = publisher, quantization = quantization,
            parameters = parameters, license = license, filename = filename,
        )
    }

    /**
     * Look in the running JAR's `models/` directory first, then fall back
     * to `~/.meshlit/desktop/models/` so a dev-mode run still resolves the
     * model when `gradle run` hasn't bundled it.
     */
    private fun locateBundledFile(filename: String): Path? {
        val resourcePath = javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { uri ->
            val asPath = Path.of(uri)
            // If we're inside a JAR (file:*.jar!/...) unwrap the JAR root.
            val inside = asPath.toString()
            when {
                inside.endsWith(".jar") -> Path.of(inside.substringBeforeLast('/')).resolve("models").resolve(filename)
                Files.isDirectory(asPath) -> asPath.resolve("models").resolve(filename)
                else -> null
            }
        }
        if (resourcePath != null && Files.exists(resourcePath)) return resourcePath
        val devPath = Path.of(System.getProperty("user.dir"), "desktopApp", "build", "compose",
            "resources", "main", "models", filename)
        if (Files.exists(devPath)) return devPath
        val homePath = Path.of(System.getProperty("user.home"), ".meshlit", "desktop", "models", filename)
        if (Files.exists(homePath)) return homePath
        return null
    }

    private fun imported(): List<ImportedModelStatus> {
        if (!Files.exists(importedFile)) return emptyList()
        val text = runCatching { Files.readString(importedFile) }.getOrNull() ?: return emptyList()
        val root = runCatching { boundedJson(text) }.getOrNull() ?: return emptyList()
        val list = root["models"]?.jsonArray ?: root["imported"]?.jsonArray ?: return emptyList()
        return list.mapNotNull { entry ->
            val obj = entry.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val expectedSha = obj["sha256"]?.jsonPrimitive?.contentOrNull
            val expectedSize = obj["sizeBytes"]?.jsonPrimitive?.longOrNull
            val p = Path.of(path)
            if (!Files.exists(p)) {
                ImportedModelStatus(name, path, present = false, sizeOk = false, hashOk = false,
                    sha256 = null, sizeBytes = null, expectedSha256 = expectedSha, expectedSizeBytes = expectedSize)
            } else {
                val actualSize = Files.size(p)
                val actualSha = sha256Of(p)
                ImportedModelStatus(
                    name = name, path = path, present = true,
                    sizeOk = expectedSize == null || actualSize == expectedSize,
                    hashOk = expectedSha == null || actualSha.equals(expectedSha, ignoreCase = true),
                    sha256 = actualSha, sizeBytes = actualSize,
                    expectedSha256 = expectedSha, expectedSizeBytes = expectedSize,
                )
            }
        }
    }

    private fun ollama(): OllamaStatus {
        val now = Instant.now()
        val tagsUri = loopbackProbe.resolve("/api/tags")
        val request = HttpRequest.newBuilder(tagsUri).timeout(Duration.ofSeconds(3))
            .header("Accept", "application/json").GET().build()
        val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.getOrElse {
            return OllamaStatus(reachable = false, endpoint = loopbackProbe.toString(),
                modelCount = 0, models = emptyList(), probeAt = now,
                reason = it.javaClass.simpleName + ": " + (it.message ?: ""))
        }
        if (response.statusCode() != 200) {
            return OllamaStatus(reachable = false, endpoint = loopbackProbe.toString(),
                modelCount = 0, models = emptyList(), probeAt = now,
                reason = "HTTP " + response.statusCode())
        }
        val root = runCatching { boundedJson(response.body()) }.getOrNull()
        val list = root?.get("models")?.jsonArray
        if (list == null) {
            return OllamaStatus(reachable = true, endpoint = loopbackProbe.toString(),
                modelCount = 0, models = emptyList(), probeAt = now,
                reason = "no /api/tags models array")
        }
        val models = list.map { entry ->
            val obj = entry.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "<unknown>"
            val size = obj["size"]?.jsonPrimitive?.longOrNull
            val digest = obj["digest"]?.jsonPrimitive?.contentOrNull
            val details = obj["details"]?.jsonObject
            val parameterSize = details?.get("parameter_size")?.jsonPrimitive?.contentOrNull
            val quantizationLevel = details?.get("quantization_level")?.jsonPrimitive?.contentOrNull
            val contextLength = details?.get("context_length")?.jsonPrimitive?.longOrNull
            val loaded = probeLoaded(name)
            OllamaModel(name = name, sizeBytes = size, digest = digest,
                parameterSize = parameterSize, quantizationLevel = quantizationLevel,
                contextLength = contextLength, loaded = loaded)
        }
        return OllamaStatus(reachable = true, endpoint = loopbackProbe.toString(),
            modelCount = models.size, models = models, probeAt = now, reason = null)
    }

    private fun probeLoaded(model: String): Boolean {
        val showUri = loopbackProbe.resolve("/api/show")
        val payload = """{"name":${jsonString(model)}}"""
        val request = HttpRequest.newBuilder(showUri).timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload)).build()
        val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.getOrNull() ?: return false
        if (response.statusCode() != 200) return false
        val root = runCatching { boundedJson(response.body()) }.getOrNull() ?: return false
        // /api/show returns the full model card even when not loaded. The
        // Ollama-native `size_vram` field is only present after load. Anything
        // else would be a guess, so the row reports "available, not loaded".
        val details = root["details"]?.jsonObject
        val sizeVram = details?.get("size_vram")?.jsonPrimitive?.longOrNull ?: 0L
        return sizeVram > 0L
    }

    fun close() { runCatching { http.shutdownNow() } }

    private fun BundleModelStatus.toJson(): JsonObject = buildJsonObject {
        put("present", present); put("sizeOk", sizeOk); put("hashOk", hashOk)
        put("sha256", sha256); put("sizeBytes", sizeBytes)
        put("expectedSizeBytes", expectedSizeBytes); put("expectedSha256", expectedSha256)
        id?.let { put("id", it) }; publisher?.let { put("publisher", it) }
        quantization?.let { put("quantization", it) }; parameters?.let { put("parameters", it) }
        license?.let { put("license", it) }; filename?.let { put("filename", it) }
    }

    private fun ImportedModelStatus.toJson(): JsonObject = buildJsonObject {
        put("name", name); put("path", path); put("present", present)
        put("sizeOk", sizeOk); put("hashOk", hashOk)
        sha256?.let { put("sha256", it) }
        sizeBytes?.let { put("sizeBytes", it) }
        expectedSha256?.let { put("expectedSha256", it) }
        expectedSizeBytes?.let { put("expectedSizeBytes", it) }
    }

    private fun OllamaStatus.toJson(): JsonObject = buildJsonObject {
        put("reachable", reachable); put("endpoint", endpoint)
        put("modelCount", modelCount); put("probeAt", probeAt.toString())
        reason?.let { put("reason", it) }
        putJsonArray("models") {
            models.forEach { model ->
                add(buildJsonObject {
                    put("name", model.name)
                    model.sizeBytes?.let { put("sizeBytes", it) }
                    model.digest?.let { put("digest", it) }
                    model.parameterSize?.let { put("parameterSize", it) }
                    model.quantizationLevel?.let { put("quantizationLevel", it) }
                    model.contextLength?.let { put("contextLength", it) }
                    put("loaded", model.loaded)
                })
            }
        }
    }

    private fun readResourceText(path: String): String? = runCatching {
        ModelInventory::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    private fun sha256Of(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buf = ByteArray(1 shl 20)
            while (true) { val n = stream.read(buf); if (n <= 0) break; digest.update(buf, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal fun modelsCheck(output: String) {
    val inventory = ModelInventory()
    try {
        val json = inventory.collect()
        val text = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json) + "\n"
        Files.writeString(Path.of(output), text)
        val bundle = json["bundle"]!!.jsonObject
        val ollama = json["ollama"]!!.jsonObject
        val bundleOk = bundle["present"]?.jsonPrimitive?.boolean == true
            && bundle["sizeOk"]?.jsonPrimitive?.boolean == true
            && bundle["hashOk"]?.jsonPrimitive?.boolean == true
        val ollamaOk = ollama["reachable"]?.jsonPrimitive?.boolean == true
        val summary = "models-check: bundle=$bundleOk ollama=$ollamaOk -> $output"
        println(summary)
    } finally { inventory.close() }
}

private fun JsonPrimitive.contentOrNull(): String? = if (this is JsonNull) null else content
private fun jsonString(s: String): String = buildString { append('"'); s.forEach { c ->
    when (c) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c) } }; append('"') }
