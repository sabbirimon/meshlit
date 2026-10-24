package com.meshlit.desktop

import com.meshlit.workspace.ChatTurn
import com.meshlit.workspace.GenerationBudget
import com.meshlit.workspace.GenerationUsage
import kotlinx.serialization.json.*
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** System TLS, no redirects, no background discovery, no persisted bearer secrets. */
enum class HostProtocol(val basePath: String, val label: String, val streaming: Boolean = true) {
    OPENAI("/v1", "OpenAI compatible"), OPEN_WEBUI("/api", "Open WebUI"), MESHLIT("/v1", "Meshlit buffered node", false)
}
class HostEndpoint private constructor(val base: URI, val protocol: HostProtocol) {
    val loopback: Boolean get() = base.host in setOf("127.0.0.1", "::1", "[::1]")
    fun route(path: String): URI = URI(base.toString().trimEnd('/') + "/" + path)
    companion object {
        fun parse(text: String, protocol: HostProtocol = HostProtocol.OPENAI): HostEndpoint {
            require(text.length in 1..2048 && text.none { it.isWhitespace() || it.isISOControl() }) { "Enter an HTTPS /v1 endpoint, or HTTP on literal loopback." }
            val uri = URI(text)
            require(uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("127.0.0.1", "::1", "[::1]"))) { "Remote hosts require HTTPS with a trusted certificate." }
            require(uri.port == -1 || uri.port in 1..65535)
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/" || uri.rawPath == protocol.basePath || uri.rawPath == protocol.basePath + "/") { "Wrong API base path." }
            return HostEndpoint(URI(uri.scheme, null, uri.host, uri.port, protocol.basePath, null, null), protocol)
        }
    }
}
class HostClient(private val endpoint: HostEndpoint, private val token: String) : AutoCloseable {
    init {
        require(token.length <= 8192 && token.none { it.isISOControl() })
        require(endpoint.loopback || token.isNotBlank()) { "A remote host requires a client token." }
        require(endpoint.protocol == HostProtocol.OPENAI || token.isNotBlank()) { "This host protocol requires its client/account API token." }
    }

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build()
    private val deadline = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "meshlit-client-deadline").apply { isDaemon = true } }
    private val closed = AtomicBoolean(false)
    private val lifecycle = Any()
    @Volatile private var active: CompletableFuture<HttpResponse<InputStream>>? = null
    @Volatile private var body: InputStream? = null
    private fun request(path: String): HttpRequest.Builder {
        check(!closed.get()) { "Client closed" }
        return HttpRequest.newBuilder(endpoint.route(path)).timeout(Duration.ofSeconds(120))
            .header("Accept", "application/json").apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
    }
    private fun <T> response(request: HttpRequest, timeoutSeconds: Long = 120, consume: (InputStream, String) -> T): T {
        val responseBody = java.util.concurrent.atomic.AtomicReference<InputStream?>()
        val timedOut = AtomicBoolean(false)
        val (pending, timeout) = synchronized(lifecycle) {
            check(!closed.get()) { "Client closed" }
            check(active == null) { "One request per client at a time." }
            val future = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            active = future
            val timer = deadline.schedule({ timedOut.set(true); future.cancel(true); runCatching { responseBody.get()?.close() } }, timeoutSeconds, TimeUnit.SECONDS)
            future to timer
        }
        try {
            val result = pending.get(timeoutSeconds, TimeUnit.SECONDS)
            body = result.body(); responseBody.set(body)
            check(!closed.get() && !timedOut.get()) { "Request cancelled or timed out" }
            if (result.statusCode() != 200) throw HostHttpFailure(result.statusCode())
            return body!!.use { consume(it, result.headers().firstValue("Content-Type").orElse("")) }
        } finally {
            runCatching { responseBody.getAndSet(null)?.close() }
            synchronized(lifecycle) { body = null; active = null; timeout.cancel(false) }
        }
    }
    /** Explicit read-only probe. Headers and body share a five-second deadline. */
    fun health(): HostConnectionResult = try {
        val request = request("models").timeout(Duration.ofSeconds(5)).GET().build()
        val models = response(request, 5) { stream, _ ->
            val bytes = stream.readNBytes(262145); require(bytes.size <= 262144)
            val root = boundedJson(bytes.decodeToString(throwOnInvalidSequence = true))
            val data = root["data"]?.jsonArray ?: error("Missing model list")
            require(data.size <= 512)
            data.map { entry -> entry.jsonObject["id"]!!.jsonPrimitive.content.also { require(it.length in 1..512 && it.none(Char::isISOControl)) } }.distinct()
        }
        HostConnectionResult.Ready(models, System.currentTimeMillis())
    } catch (failure: Exception) {
        var cause: Throwable = failure
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        when (cause) {
            is HostHttpFailure -> HostConnectionResult.Failed(if (cause.status in setOf(401,403)) "auth" else "http", "HTTP ${cause.status} — ${if (cause.status in setOf(401,403)) "check this host's scoped client token" else "host refused the model inventory request"}")
            is java.net.http.HttpTimeoutException, is TimeoutException -> HostConnectionResult.Failed("timeout", "No complete model inventory within 5 seconds")
            is javax.net.ssl.SSLException -> HostConnectionResult.Failed("tls", "TLS verification failed; use a certificate trusted by this computer")
            is java.io.IOException -> HostConnectionResult.Failed("network", "Host unreachable; check its address and listener")
            else -> HostConnectionResult.Failed("response", "Invalid or incomplete host model inventory")
        }
    }
    internal fun localProperties(): JsonObject {
        require(endpoint.loopback && endpoint.protocol == HostProtocol.OPENAI)
        val request = HttpRequest.newBuilder(endpoint.base.resolve("/props")).timeout(Duration.ofSeconds(15))
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }.GET().build()
        return response(request) { stream, _ ->
            val bytes = stream.readNBytes(262145); require(bytes.size <= 262144)
            boundedJson(bytes.decodeToString(throwOnInvalidSequence = true))
        }
    }
    fun models(): List<String> = response(request("models").GET().build()) { stream, _ ->
        val bytes = stream.readNBytes(262145)
        require(bytes.size <= 262144) { "Model list exceeds 256 KiB." }
        val root = boundedJson(bytes.decodeToString(throwOnInvalidSequence = true))
        val list = root["data"]?.jsonArray ?: error("Missing model list")
        require(list.size <= 512)
        list.map { it.jsonObject["id"]?.jsonPrimitive?.content ?: error("Missing model id") }
            .onEach { require(it.length in 1..512 && it.none(Char::isISOControl)) }.distinct()
    }
    fun generate(model: String, turns: List<ChatTurn>, budget: GenerationBudget, onText: (String) -> Unit): GenerationUsage =
        generateConfigured(model, turns, budget, null, onText)
    fun generateConfigured(model: String, turns: List<ChatTurn>, budget: GenerationBudget, systemPrompt: String?, onText: (String) -> Unit): GenerationUsage =
        generateRequest(model, turns, budget, systemPrompt, false, onText)
    /** Opt-in local developer benchmark only; ordinary chat retains provider sampling defaults. */
    internal fun generateDeterministic(model: String, turns: List<ChatTurn>, budget: GenerationBudget, onText: (String) -> Unit): GenerationUsage {
        require(endpoint.loopback && endpoint.protocol == HostProtocol.OPENAI)
        return generateRequest(model, turns, budget, null, true, onText)
    }
    private fun generateRequest(model: String, turns: List<ChatTurn>, budget: GenerationBudget, systemPrompt: String?, deterministic: Boolean, onText: (String) -> Unit): GenerationUsage {
        require(systemPrompt == null || systemPrompt.isNotBlank() && systemPrompt.length <= 8192)
        require(model.length in 1..512 && model.none(Char::isISOControl))
        require((turns.size + if (systemPrompt == null) 0 else 1) in 1..64 && turns.last().role == "user" && turns.sumOf { it.content.length.toLong() } + (systemPrompt?.length ?: 0) <= 131072)
        val payload = buildJsonObject {
            put("model", model); put("stream", endpoint.protocol.streaming); put("max_tokens", budget.maxOutputTokens)
            if (deterministic) { put("temperature", 0); put("seed", 42) }
            if (endpoint.protocol.streaming) putJsonObject("stream_options") { put("include_usage", true) }
            putJsonArray("messages") {
                systemPrompt?.let { add(buildJsonObject { put("role", "system"); put("content", it) }) }
                turns.forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.content) }) } }
        }.toString()
        val start = System.nanoTime()
        return response(request("chat/completions").header("Content-Type", "application/json")
            .header("Accept", if (endpoint.protocol.streaming) "text/event-stream" else "application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build()) { stream, type ->
            if (!endpoint.protocol.streaming) {
                require(type.substringBefore(';').trim().lowercase() == "application/json")
                val bytes = stream.readNBytes(262145); require(bytes.size <= 262144)
                val root = boundedJson(bytes.decodeToString(throwOnInvalidSequence = true))
                require(root["error"] == null)
                val choices = root["choices"]!!.jsonArray; require(choices.size == 1)
                val text = choices.single().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
                require(text.isNotBlank() && text.length <= 131072)
                val tokens = (root["usage"] as? JsonObject)?.get("completion_tokens")?.jsonPrimitive?.longOrNull
                require(tokens == null || tokens in 0..10_000_000)
                onText(text)
                return@response GenerationUsage(tokens, System.nanoTime() - start)
            }
            require(type.substringBefore(';').trim().lowercase() == "text/event-stream") { "Host did not return a token stream." }
            var total = 0; var outputSize = 0; var tokens: Long? = null; var finished = false
            val event = StringBuilder()
            fun dispatch(): Boolean {
                if (event.isEmpty()) return false
                val data = event.toString().trimEnd('\n'); event.setLength(0)
                if (data == "[DONE]") return true
                val json = boundedJson(data)
                if (json["error"] != null) error("Host reported a generation error; details withheld.")
                json["usage"]?.takeIf { it is JsonObject }?.jsonObject?.get("completion_tokens")?.jsonPrimitive?.longOrNull?.let {
                    require(it in 0..10_000_000); tokens = it
                }
                json["choices"]?.jsonArray?.forEach { choice ->
                    val delta = choice.jsonObject["delta"]?.jsonObject
                    val content = delta?.get("content")?.takeIf { it is JsonPrimitive && it != JsonNull }?.jsonPrimitive?.content
                    if (content != null) { outputSize += content.length; require(outputSize <= 131072); onText(content) }
                }
                return false
            }
            val line = StringBuilder()
            val buffered = stream.buffered(8192)
            while (!finished) {
                if (closed.get() || Thread.currentThread().isInterrupted) throw CancellationException("Request cancelled")
                val c = buffered.read()
                if (c == -1) { if (line.isNotEmpty() || event.isNotEmpty()) error("Incomplete token stream"); break }
                require(++total <= 2_097_152) { "Token stream exceeds 2 MiB." }
                // Preserve UTF-8 bytes until the complete line; no per-byte Unicode decoding.
                if (c == 10) {
                    val value = line.toString().map { it.code.toByte() }.toByteArray().decodeToString(throwOnInvalidSequence = true).trimEnd('\r'); line.setLength(0)
                    if (value.isEmpty()) finished = dispatch()
                    else if (value.startsWith("data:")) { event.append(value.removePrefix("data:").removePrefix(" ")).append('\n'); require(event.length <= 65536) }
                } else { line.append(c.toChar()); require(line.length <= 65536) { "SSE line exceeds 64 KiB." } }
            }
            require(finished) { "Host closed the stream before completion." }
            require(outputSize > 0) { "Host returned no text." }
            GenerationUsage(tokens, System.nanoTime() - start)
        }
    }
    override fun close() {
        synchronized(lifecycle) { closed.set(true); active?.cancel(true); deadline.shutdownNow() }
        runCatching { body?.close() }; http.shutdownNow()
    }
}

/** Bound nesting before the provider-controlled JSON reaches the recursive parser. */
internal fun boundedJson(text: String): JsonObject {
    require(text.length <= 262144)
    var depth = 0; var quoted = false; var escaped = false
    text.forEach { character ->
        if (quoted) {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == '"') quoted = false
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) { "Host JSON nesting exceeds 32 levels." } }
            '}', ']' -> { depth--; require(depth >= 0) }
        }
    }
    require(depth == 0 && !quoted)
    return Json.parseToJsonElement(text).jsonObject
}

private class HostHttpFailure(val status: Int) : Exception("Host returned HTTP $status; response body withheld")
sealed interface HostConnectionResult {
    data class Ready(val models: List<String>, val observedAtMs: Long) : HostConnectionResult
    data class Failed(val code: String, val message: String) : HostConnectionResult
}
