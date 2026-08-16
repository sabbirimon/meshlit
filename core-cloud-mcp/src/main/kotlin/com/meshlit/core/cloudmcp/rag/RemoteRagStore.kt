package com.meshlit.core.cloudmcp.rag

import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import java.io.IOException

/**
 * Remote RAG store. Talks to the user's configured Pinecone /
 * Qdrant / Milvus via each provider's MCP server — no native
 * SDK required for v1.
 *
 * Wire shape:
 *   POST {providerBaseUrl}/rag/upsert  body = {namespace, docs[]}
 *   POST {providerBaseUrl}/rag/query   body = {namespace, embedding, k}
 *
 * Each doc is `{id, text, embedding: number[]}`. The provider's
 * MCP server handles the actual vector-DB round-trip.
 *
 * The Android client doesn't need to know which vector DB is on
 * the other side — that's the provider's job. We just speak
 * MCP.
 */
class RemoteRagStore(
    private val httpClient: OkHttpClient,
    private val credentialProvider: (providerId: String, credentialRef: String?) -> String? = { _, _ -> null },
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    private fun authHeaders(credential: String?): Map<String, String> = buildMap {
        credential?.let { put("Authorization", "Bearer $it") }
    }

    suspend fun upsert(
        providerId: String,
        namespace: String,
        docs: List<RemoteDoc>,
    ): Boolean {
        val credential = credentialProvider(providerId, "$providerId/token")
        val envelope = buildJsonObject {
            put("namespace", namespace)
            put("docs", json.encodeToJsonElement(
                ListSerializer(RemoteDoc.serializer()),
                docs,
            ))
        }
        val request = Request.Builder()
            .url("https://mcp.$providerId.example.com/rag/upsert")
            .apply { authHeaders(credential).forEach { (k, v) -> header(k, v) } }
            .post(json.encodeToString(JsonObject.serializer(), envelope).toRequestBody(mediaType))
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    suspend fun query(
        providerId: String,
        namespace: String,
        embedding: FloatArray,
        k: Int = 5,
    ): List<Pair<String, Float>> {
        val credential = credentialProvider(providerId, "$providerId/token")
        val envelope = buildJsonObject {
            put("namespace", namespace)
            put("embedding", json.encodeToJsonElement(FloatArraySerializer(), embedding))
            put("k", k)
        }
        val request = Request.Builder()
            .url("https://mcp.$providerId.example.com/rag/query")
            .apply { authHeaders(credential).forEach { (k, v) -> header(k, v) } }
            .post(json.encodeToString(JsonObject.serializer(), envelope).toRequestBody(mediaType))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val matches = root["matches"]?.jsonArray ?: return emptyList()
                matches.mapNotNull { node ->
                    val obj = node.jsonObject
                    val text = obj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                    text to score
                }
            }
        } catch (e: IOException) {
            emptyList()
        }
    }
}

@kotlinx.serialization.Serializable
data class RemoteDoc(
    val id: String,
    val text: String,
    val embedding: FloatArray,
)