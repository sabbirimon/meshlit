package com.meshlit.core.cloudmcp.web

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
 * Dispatches [WebSearchProvider.search] requests to the right
 * vendor-specific HTTP shape and normalises the response into
 * [List]<[WebSearchResult]>.
 *
 * Each vendor's `dispatch*` function owns:
 *   1. Building the OkHttp [Request] (URL, headers, body).
 *   2. Executing it (with a 10-second connect / 15-second read
 *      timeout — web-search responses are bounded by `k`).
 *   3. Parsing the JSON response into a flat `List<WebSearchResult>`.
 *
 * The dispatcher holds no per-vendor state — it's a stateless
 * bridge. The [WebSearchProvider] sealed class carries the
 * vendor-specific config.
 */
class WebSearchDispatcher(
    private val httpClient: OkHttpClient,
) {
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Resolve [provider] to its vendor-specific transport. Pure
     * delegation — the agent-loop UI never calls this directly,
     * it goes through [WebSearchProvider.search].
     */
    suspend fun search(provider: WebSearchProvider, query: String, k: Int): List<WebSearchResult> =
        try {
            when (provider) {
                is WebSearchProvider.Bing -> dispatchBing(provider, query, k)
                is WebSearchProvider.Brave -> dispatchBrave(provider, query, k)
                is WebSearchProvider.Serper -> dispatchSerper(provider, query, k)
                is WebSearchProvider.Tavily -> dispatchTavily(provider, query, k)
                is WebSearchProvider.GoogleCse -> dispatchGoogleCse(provider, query, k)
            }
        } catch (e: IOException) {
            emptyList()
        }

    private fun dispatchBing(
        provider: WebSearchProvider.Bing,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val url = "${provider.endpoint}/v7.0/search?q=${query.urlEncode()}&count=$k"
        val request = Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", provider.apiKey)
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            val root = jsonParser(body).jsonObject
            root["webPages"]?.jsonObject
                ?.get("value")?.jsonArray
                ?.mapNotNull(::bingRow)
                .orEmpty()
        }
    }

    private fun bingRow(node: kotlinx.serialization.json.JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val url = obj["url"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty()
        return WebSearchResult(title = name, url = url, snippet = snippet)
    }

    private fun dispatchBrave(
        provider: WebSearchProvider.Brave,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val url = "${provider.endpoint}/res/v1/web/search?q=${query.urlEncode()}&count=$k"
        val request = Request.Builder()
            .url(url)
            .header("X-Subscription-Token", provider.apiKey)
            .header("Accept", "application/json")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            val root = jsonParser(body).jsonObject
            root["web"]?.jsonObject
                ?.get("results")?.jsonArray
                ?.mapNotNull(::braveRow)
                .orEmpty()
        }
    }

    private fun braveRow(node: kotlinx.serialization.json.JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val url = obj["url"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["description"]?.jsonPrimitive?.content.orEmpty()
        return WebSearchResult(title = title, url = url, snippet = snippet)
    }

    private fun dispatchSerper(
        provider: WebSearchProvider.Serper,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val body = buildJsonObject {
            put("q", query)
            put("n", k)
        }
        val request = Request.Builder()
            .url("${provider.endpoint}/search")
            .header("X-API-KEY", provider.apiKey)
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            val root = jsonParser(text).jsonObject
            root["organic"]?.jsonArray
                ?.mapNotNull(::serperRow)
                .orEmpty()
        }
    }

    private fun serperRow(node: kotlinx.serialization.json.JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val url = obj["link"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty()
        return WebSearchResult(title = title, url = url, snippet = snippet)
    }

    private fun dispatchTavily(
        provider: WebSearchProvider.Tavily,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val body = buildJsonObject {
            put("query", query)
            put("max_results", k)
        }
        val request = Request.Builder()
            .url("${provider.endpoint}/search")
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            val root = jsonParser(text).jsonObject
            root["results"]?.jsonArray
                ?.mapNotNull(::tavilyRow)
                .orEmpty()
        }
    }

    private fun tavilyRow(node: kotlinx.serialization.json.JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val url = obj["url"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["content"]?.jsonPrimitive?.content.orEmpty()
        val score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull()
        return WebSearchResult(title = title, url = url, snippet = snippet, score = score)
    }

    private fun dispatchGoogleCse(
        provider: WebSearchProvider.GoogleCse,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val url = buildString {
            append("${provider.endpoint}/customsearch/v1?q=")
            append(query.urlEncode())
            append("&key=").append(provider.apiKey.urlEncode())
            append("&cx=").append(provider.cx.urlEncode())
            append("&num=").append(k.coerceIn(1, 10))
        }
        val request = Request.Builder().url(url).get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            val root = jsonParser(text).jsonObject
            root["items"]?.jsonArray
                ?.mapNotNull(::googleCseRow)
                .orEmpty()
        }
    }

    private fun googleCseRow(node: kotlinx.serialization.json.JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val url = obj["link"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty()
        return WebSearchResult(title = title, url = url, snippet = snippet)
    }

    private fun jsonParser(text: String): JsonObject =
        if (text.isBlank()) buildJsonObject {} else json.parseToJsonElement(text).jsonObject

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

private fun String.urlEncode(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
        .replace("+", "%20")
