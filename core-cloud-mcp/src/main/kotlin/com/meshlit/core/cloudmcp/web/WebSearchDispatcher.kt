package com.meshlit.core.cloudmcp.web

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

/**
 * Dispatches [WebSearchProvider.search] requests to the right
 * vendor-specific HTTP shape and normalises the response into
 * [List]<[WebSearchResult]>.
 *
 * Each vendor's `dispatch*` function owns:
 *   1. Building the OkHttp [Request] (URL, headers, body).
 *   2. Executing it (with a 10-second connect / 15-second read
 *      timeout — web-search responses are bounded by `k`).
 *   3. Parsing the response into a flat `List<WebSearchResult>`.
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
                is WebSearchProvider.DuckDuckGo -> dispatchDuckDuckGo(provider, query, k)
                is WebSearchProvider.Bing -> dispatchBing(provider, query, k)
                is WebSearchProvider.Brave -> dispatchBrave(provider, query, k)
                is WebSearchProvider.Serper -> dispatchSerper(provider, query, k)
                is WebSearchProvider.Tavily -> dispatchTavily(provider, query, k)
                is WebSearchProvider.GoogleCse -> dispatchGoogleCse(provider, query, k)
                is WebSearchProvider.Perplexity -> dispatchPerplexity(provider, query, k)
                is WebSearchProvider.SearXNG -> dispatchSearXNG(provider, query, k)
                is WebSearchProvider.Custom -> dispatchCustom(provider, query, k)
            }
        } catch (e: IOException) {
            emptyList()
        }

    /**
     * DuckDuckGo search: zero setup, free, instant titles, links and text snippets.
     */
    private fun dispatchDuckDuckGo(
        provider: WebSearchProvider.DuckDuckGo,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val formBody = FormBody.Builder()
            .add("q", query)
            .add("b", "")
            .build()
        val request = Request.Builder()
            .url(provider.endpoint)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .post(formBody)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            parseDuckDuckGoHtml(html, k)
        }
    }

    private fun parseDuckDuckGoHtml(html: String, k: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        // Match snippets and result urls
        val snippetRegex = Regex(
            """<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val titleRegex = Regex(
            """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            RegexOption.IGNORE_CASE
        )

        val snippetMatches = snippetRegex.findAll(html).toList()
        val titleMatches = titleRegex.findAll(html).toList()

        for (i in 0 until minOf(k, maxOf(snippetMatches.size, titleMatches.size))) {
            val titleMatch = titleMatches.getOrNull(i)
            val snippetMatch = snippetMatches.getOrNull(i)

            var rawUrl = titleMatch?.groupValues?.getOrNull(1)
                ?: snippetMatch?.groupValues?.getOrNull(1)
                ?: continue

            if (rawUrl.contains("uddg=")) {
                val extracted = rawUrl.substringAfter("uddg=").substringBefore("&")
                rawUrl = java.net.URLDecoder.decode(extracted, Charsets.UTF_8.name())
            }

            if (!isValidUrl(rawUrl)) continue

            val rawTitle = titleMatch?.groupValues?.getOrNull(2)
                ?: snippetMatch?.groupValues?.getOrNull(2)
                ?: rawUrl
            val rawSnippet = snippetMatch?.groupValues?.getOrNull(2).orEmpty()

            val cleanTitle = cleanHtml(rawTitle).take(300)
            val cleanSnippet = cleanHtml(rawSnippet).take(1500)

            results.add(
                WebSearchResult(
                    title = cleanTitle.ifBlank { rawUrl },
                    url = rawUrl,
                    snippet = cleanSnippet,
                )
            )
        }
        return results.distinctBy { it.url }.take(k)
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

    private fun bingRow(node: JsonElement): WebSearchResult? {
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

    private fun braveRow(node: JsonElement): WebSearchResult? {
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

    private fun serperRow(node: JsonElement): WebSearchResult? {
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

    private fun tavilyRow(node: JsonElement): WebSearchResult? {
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

    private fun googleCseRow(node: JsonElement): WebSearchResult? {
        val obj = node.jsonObject
        val title = obj["title"]?.jsonPrimitive?.content ?: return null
        val url = obj["link"]?.jsonPrimitive?.content ?: return null
        val snippet = obj["snippet"]?.jsonPrimitive?.content.orEmpty()
        return WebSearchResult(title = title, url = url, snippet = snippet)
    }

    private fun dispatchPerplexity(
        provider: WebSearchProvider.Perplexity,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val body = buildJsonObject {
            put("model", "sonar")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", query)
                })
            })
        }
        val request = Request.Builder()
            .url("${provider.endpoint}/chat/completions")
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            val root = jsonParser(text).jsonObject
            val citations = root["citations"]?.jsonArray
            val content = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()

            if (citations != null && citations.isNotEmpty()) {
                citations.mapIndexedNotNull { index, elem ->
                    val url = elem.jsonPrimitive.content
                    if (isValidUrl(url)) {
                        WebSearchResult(
                            title = "Source ${index + 1}",
                            url = url,
                            snippet = content.take(300),
                        )
                    } else null
                }.take(k)
            } else if (content.isNotBlank()) {
                listOf(WebSearchResult(title = query, url = provider.endpoint, snippet = content.take(1500)))
            } else {
                emptyList()
            }
        }
    }

    private fun dispatchSearXNG(
        provider: WebSearchProvider.SearXNG,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val url = "${provider.endpoint}/search?q=${query.urlEncode()}&format=json"
        val builder = Request.Builder().url(url).get().header("Accept", "application/json")
        if (!provider.apiKey.isNullOrBlank()) {
            builder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        return httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            val root = jsonParser(text).jsonObject
            root["results"]?.jsonArray?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val rUrl = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val snippet = obj["content"]?.jsonPrimitive?.content.orEmpty()
                WebSearchResult(title = title, url = rUrl, snippet = snippet)
            }.orEmpty().take(k)
        }
    }

    /**
     * Dispatch universal custom search provider with user-specified endpoint,
     * arbitrary API key, custom headers, and flexible response parsing.
     */
    private fun dispatchCustom(
        provider: WebSearchProvider.Custom,
        query: String,
        k: Int,
    ): List<WebSearchResult> {
        val requestBuilder = Request.Builder()
        if (!provider.apiKey.isNullOrBlank()) {
            val authHeader = "${provider.authPrefix}${provider.apiKey}".trim()
            requestBuilder.header(provider.headerName, authHeader)
        }
        requestBuilder.header("Accept", "application/json")

        val request = if (provider.isPost) {
            val body = buildJsonObject {
                put(provider.queryParam, query)
                put("k", k)
            }
            requestBuilder
                .url(provider.endpoint)
                .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
                .build()
        } else {
            val sep = if (provider.endpoint.contains("?")) "&" else "?"
            val fullUrl = "${provider.endpoint}${sep}${provider.queryParam}=${query.urlEncode()}&k=$k"
            requestBuilder.url(fullUrl).get().build()
        }

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val text = response.body?.string().orEmpty()
            parseGenericSearchResults(text, k)
        }
    }

    private fun parseGenericSearchResults(text: String, k: Int): List<WebSearchResult> {
        val root = jsonParser(text)
        val array = root["results"]?.jsonArray
            ?: root["organic"]?.jsonArray
            ?: root["items"]?.jsonArray
            ?: root["webPages"]?.jsonObject?.get("value")?.jsonArray
            ?: root["web"]?.jsonObject?.get("results")?.jsonArray
            ?: return emptyList()

        return array.mapNotNull { item ->
            val obj = item.jsonObject
            val title = (obj["title"] ?: obj["name"])?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = (obj["url"] ?: obj["link"] ?: obj["href"])?.jsonPrimitive?.content ?: return@mapNotNull null
            val snippet = (obj["snippet"] ?: obj["description"] ?: obj["content"] ?: obj["body"])?.jsonPrimitive?.content.orEmpty()
            val score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull()
            if (isValidUrl(url)) {
                WebSearchResult(title = cleanHtml(title), url = url, snippet = cleanHtml(snippet), score = score)
            } else null
        }.take(k)
    }

    private fun cleanHtml(value: String): String =
        value.replace(Regex("<[^>]+>"), "").replace(Regex("[\\p{Cntrl}]"), " ").trim()

    private fun isValidUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

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
