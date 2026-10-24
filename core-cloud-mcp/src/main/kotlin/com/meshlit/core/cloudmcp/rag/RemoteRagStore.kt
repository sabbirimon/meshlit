package com.meshlit.core.cloudmcp.rag

import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.*

/** Custom REST bridge. This is not a vendor MCP protocol implementation.
 * No endpoint is inferred from a provider name. A compatible integration must
 * supply a reviewed HTTPS endpoint and endpoint-bound credential reference.
 */
data class RagBridgeEndpoint(val baseUrl: String, val credentialRef: String? = null)
class RemoteRagStore(
    httpClient: OkHttpClient,
    private val credentialProvider: (providerId: String, credentialRef: String?) -> String? = { _, _ -> null },
    private val endpointProvider: (providerId: String) -> RagBridgeEndpoint? = { null },
) {
    private val httpClient = httpClient.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    private fun endpoint(providerId: String): RagBridgeEndpoint {
        require(providerId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        val value = checkNotNull(endpointProvider(providerId)) { "Remote RAG bridge is not configured; no request was sent" }
        val url = value.baseUrl.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "RAG bridge requires a reviewed HTTPS base URL" }
        return value
    }
    private fun validate(namespace: String, embedding: FloatArray) {
        require(namespace.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        require(embedding.size in 1..4096 && embedding.all { it.isFinite() }) { "Invalid embedding" }
    }
    private suspend fun exchange(request: Request): String = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(request)
        coroutineScope {
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    check(response.isSuccessful) { "RAG bridge HTTP ${response.code}; response withheld" }
                    val source = checkNotNull(response.body).source()
                    require(!source.request(262145)) { "RAG response exceeds 256 KiB" }
                    currentCoroutineContext().ensureActive()
                    source.readUtf8()
                }
            } finally { watcher.cancel() }
        }
    }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    private fun body(envelope: JsonObject): okhttp3.RequestBody {
        val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 2 * 1024 * 1024) { "RAG request exceeds 2 MiB" }
        return bytes.toRequestBody(mediaType)
    }
    private fun authHeaders(credential: String?): Map<String, String> = buildMap {
        credential?.let { require(it.length in 1..8192 && it.none(Char::isISOControl)); put("Authorization", "Bearer $it") }
    }

    suspend fun upsert(
        providerId: String,
        namespace: String,
        docs: List<RemoteDoc>,
    ): Boolean {
        require(docs.size in 1..32)
        docs.forEach { validate(namespace, it.embedding); require(it.text.length in 1..32768 && it.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
        val bridge = endpoint(providerId)
        val credential = bridge.credentialRef?.let { checkNotNull(credentialProvider(providerId, it)) { "RAG credential unavailable" } }
        val envelope = buildJsonObject {
            put("namespace", namespace)
            put("docs", json.encodeToJsonElement(
                ListSerializer(RemoteDoc.serializer()),
                docs,
            ))
        }
        val request = Request.Builder()
            .url(bridge.baseUrl.toHttpUrl().newBuilder().addPathSegment("rag").addPathSegment("upsert").build())
            .apply { authHeaders(credential).forEach { (k, v) -> header(k, v) } }
            .post(body(envelope))
            .build()
        exchange(request)
        return true
    }

    suspend fun query(
        providerId: String,
        namespace: String,
        embedding: FloatArray,
        k: Int = 5,
    ): List<Pair<String, Float>> {
        validate(namespace, embedding); require(k in 1..10)
        val bridge = endpoint(providerId)
        val credential = bridge.credentialRef?.let { checkNotNull(credentialProvider(providerId, it)) { "RAG credential unavailable" } }
        val envelope = buildJsonObject {
            put("namespace", namespace)
            put("embedding", json.encodeToJsonElement(FloatArraySerializer(), embedding))
            put("k", k)
        }
        val request = Request.Builder()
            .url(bridge.baseUrl.toHttpUrl().newBuilder().addPathSegment("rag").addPathSegment("query").build())
            .apply { authHeaders(credential).forEach { (k, v) -> header(k, v) } }
            .post(body(envelope))
            .build()
        val body = exchange(request)
        val matches = json.parseToJsonElement(body).jsonObject.getValue("matches").jsonArray
        require(matches.size <= k) { "RAG bridge exceeded requested count" }
        return matches.map { node ->
            val obj = node.jsonObject
            val text = obj.getValue("text").jsonPrimitive.content
            val score = obj.getValue("score").jsonPrimitive.content.toFloat()
            require(text.length <= 32768 && score.isFinite()) { "Invalid RAG result" }
            text to score
        }
    }
}

@kotlinx.serialization.Serializable
data class RemoteDoc(
    val id: String,
    val text: String,
    val embedding: FloatArray,
)
