package com.meshlit.core.mcp

import kotlinx.serialization.json.*

/** Accept presentation wrappers, never search prose for something executable. */
internal fun parseLocalToolPlan(raw: String): JsonObject {
    require(raw.length <= 16384) { "Local model response exceeds tool protocol limit" }
    var body = raw.trim()
    if (body.startsWith("<think>", ignoreCase = true)) {
        val end = body.indexOf("</think>", ignoreCase = true)
        require(end >= 0) { "Incomplete model thinking block" }
        body = body.substring(end + 8).trim()
    }
    if (body.startsWith("```")) {
        val fence = Regex("\\A```(?:json)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z", RegexOption.IGNORE_CASE)
        body = fence.matchEntire(body)?.groupValues?.get(1)?.trim()
            ?: error("Incomplete or unsupported JSON fence")
    }
    val decision = Json.parseToJsonElement(body) as? JsonObject ?: error("Expected exactly one tool-plan object")
    // kotlinx.serialization keeps the last duplicate key. Ambiguous actions or
    // argument values must instead fail closed, including escaped key names.
    val objects = mutableListOf<MutableSet<String>>()
    var i = 0
    while (i < body.length) {
        when (body[i]) {
            '{' -> objects.add(mutableSetOf())
            '}' -> objects.removeAt(objects.lastIndex)
            '"' -> {
                val start = i++
                while (body[i] != '"') { if (body[i] == '\\') i++; i++ }
                var next = i + 1
                while (next < body.length && body[next].isWhitespace()) next++
                if (next < body.length && body[next] == ':') {
                    val key = Json.parseToJsonElement(body.substring(start, i + 1)).jsonPrimitive.content
                    require(objects.last().add(key)) { "Duplicate JSON field: $key" }
                }
            }
        }
        i++
    }
    return decision
}
