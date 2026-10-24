package com.meshlit.core.inference.models

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Explicit external text engine. No setup, model download, redirects or retries. */
class ColibriClient {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun models(endpoint: String, token: String): List<String> {
        val data = fetch(endpoint, token, "models", null)
        val rows = data["data"] as? JsonArray ?: error("Colibri model-list schema is unavailable")
        require(rows.size <= 512) { "Colibri model list is too large" }
        return rows.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
            .onEach(::validModel).distinct()
    }

    suspend fun generate(endpoint: String, token: String, model: String,
                         messages: List<OnlineMessage>, system: String, maxTokens: Int, temperature: Float = 0.7f): OnlineReply {
        validModel(model)
        require(messages.isNotEmpty() && messages.size <= 41 && messages.last().role == "user" &&
            messages.all { it.role in setOf("user", "assistant") } &&
            messages.sumOf { it.text.length.toLong() } + system.length <= 96_000 && maxTokens in 1..2048 && temperature.isFinite() && temperature in 0f..2f)
        val payload = buildJsonObject {
            put("model", model); put("stream", false); put("max_tokens", maxTokens); put("temperature", temperature)
            put("messages", buildJsonArray {
                if (system.isNotBlank()) add(buildJsonObject { put("role", "system"); put("content", system) })
                messages.forEach { add(buildJsonObject { put("role", it.role); put("content", it.text) }) }
            })
        }
        val data = fetch(endpoint, token, "chat/completions", payload)
        val choice = (data["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val text = (message?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
        require(text.isNotBlank() && text.length <= 131_072) { "Colibri returned no usable bounded text" }
        val usage = data["usage"] as? JsonObject
        fun count(key: String) = (usage?.get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it in 0..10_000_000 }
        return OnlineReply(text, count("prompt_tokens"), count("completion_tokens"))
    }

    private suspend fun fetch(endpoint: String, token: String, path: String, payload: JsonObject?): JsonObject =
        withContext(Dispatchers.IO) { coroutineScope {
            val base = validateEndpoint(endpoint)
            require(token.length in 32..4096 && token.none { it.code !in 33..126 }) { "Save a private Colibri bearer token first" }
            val request = Request.Builder().url(base.newBuilder().addPathSegments(path).build())
                .header("Authorization", "Bearer $token").header("Accept", "application/json")
            if (payload != null) request.post(payload.toString().toRequestBody("application/json".toMediaType()))
            val call = client.newCall(request.build())
            val cancellation = launch(Dispatchers.IO) { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    check(response.isSuccessful) { "Colibri HTTP ${response.code}; verify host authorization and model availability" }
                    require(response.header("Content-Type").orEmpty().startsWith("application/json", true)) { "Colibri returned an unsupported content type" }
                    val source = requireNotNull(response.body).source()
                    require(!source.request(262_145)) { "Colibri response exceeds 256 KiB" }
                    val value = source.readUtf8(); boundedColibriJson(value)
                    try { Json.parseToJsonElement(value) as? JsonObject ?: error("schema") }
                    catch (_: Exception) { error("Colibri returned invalid JSON") }
                }
            } finally { cancellation.cancel() }
        } }

    companion object {
        fun validateEndpoint(value: String): HttpUrl {
            require(value.length in 1..2048 && value.none { it.isWhitespace() || it.code < 32 || it.code == 127 })
            val url = value.toHttpUrl()
            require(url.isHttps || url.scheme == "http" && url.host in setOf("127.0.0.1", "::1")) { "Use trusted HTTPS, or literal loopback HTTP" }
            require(url.encodedPath in setOf("/v1", "/v1/") && url.query == null && url.fragment == null &&
                url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty()) { "Use an exact /v1 base without URL credentials or parameters" }
            return url.newBuilder().encodedPath("/v1/").build()
        }
        fun validModel(model: String) { require(model.length in 1..200 && model.none { it.isWhitespace() || it.code < 32 || it.code == 127 }) { "Invalid host model identifier" } }
    }
}

internal fun boundedColibriJson(value: String) {
    var depth = 0; var quoted = false; var escaped = false
    value.forEach { c ->
        if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
        else when (c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 32) { "Colibri JSON nesting exceeds 32" } }; '}', ']' -> { depth--; require(depth >= 0) } }
    }
    require(depth == 0 && !quoted) { "Colibri JSON is incomplete" }
}
