package com.meshlit.chat

import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.net.URI

@Serializable enum class ChatSourceKind { SEARCH_RESULT, PAGE }
/** Evidence returned by an invoked tool, never URLs inferred from the model's answer. */
@Serializable data class ChatWebSource(
    val url: String, val title: String, val kind: ChatSourceKind,
    val provider: String? = null, val fetchedAtMs: Long? = null,
    val retrievedAt: String? = null, val httpStatus: Int? = null, val truncated: Boolean? = null,
)

internal fun safeChatSourceUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    value.length in 1..4096 && uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535) &&
        value.none { it.isISOControl() || it.isWhitespace() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }
}.getOrDefault(false)

internal fun chatSourceHost(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
private fun cleanSourceLabel(value: String, limit: Int = 240) = value.take(512)
    .replace(Regex("<[^>]*>"), "").replace(Regex("[\\p{Cntrl}\u202a-\u202e\u2066-\u2069]"), " ").trim().take(limit)

internal fun mergeChatSources(existing: List<ChatWebSource>, incoming: List<ChatWebSource>): List<ChatWebSource> {
    val merged = linkedMapOf<String, ChatWebSource>()
    (existing.take(12) + incoming.take(10)).forEach { source ->
        if (safeChatSourceUrl(source.url)) {
            val normalized = source.copy(title = cleanSourceLabel(source.title).ifBlank { chatSourceHost(source.url) },
                provider = source.provider?.let { cleanSourceLabel(it, 80) }, retrievedAt = source.retrievedAt?.let { cleanSourceLabel(it, 80) })
            val prior = merged[source.url]
            if (prior == null || normalized.kind == ChatSourceKind.PAGE || prior.kind != ChatSourceKind.PAGE) merged[source.url] = normalized
        }
    }
    return merged.values.take(12)
}

internal fun chatSourcesFromTool(tool: String, result: McpToolResult): List<ChatWebSource> {
    val body = ((result as? McpToolResult.Json)?.value as? JsonObject) ?: return emptyList()
    fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    val sources = when (tool) {
        "web_search" -> {
            if (body.string("status") != "ok") return emptyList()
            (body["results"] as? JsonArray).orEmpty().take(10).mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val url = row.string("url") ?: return@mapNotNull null
                ChatWebSource(url, row.string("title").orEmpty(), ChatSourceKind.SEARCH_RESULT,
                    provider = body.string("provider"), fetchedAtMs = body.number("fetchedAtMs")?.takeIf { it > 0 })
            }
        }
        "crawl_url" -> {
            val status = body.number("status")?.takeIf { it in 100..599 }?.toInt()
            val success = body.string("state") == "ok" && status != null && status in 200..299 ||
                body["state"] == null && body.string("status") == "ok"
            if (!success) return emptyList()
            val url = body.string("final_url") ?: body.string("url") ?: return emptyList()
            listOf(ChatWebSource(url, body.string("title").orEmpty(), ChatSourceKind.PAGE,
                retrievedAt = body.string("retrieved_at"), httpStatus = status,
                truncated = (body["truncated"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull))
        }
        else -> emptyList()
    }
    return mergeChatSources(emptyList(), sources)
}

internal fun chatToolActivity(tool: String?): String? = when (tool) {
    "web_search" -> "Searching the web"
    "crawl_url" -> "Reading a web page"
    "app_search" -> "Searching local content"
    else -> null
}
