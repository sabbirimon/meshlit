package com.meshlit.chat

import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.json.*

/** Return only bounded public search evidence, never tool diagnostics or local records. */
internal fun hostedSearchEvidence(result:McpToolResult):JsonObject {
    val body=(result as? McpToolResult.Json)?.value as? JsonObject ?: error("Web search failed or was blocked; no evidence was sent to the model")
    fun JsonObject.string(key:String)=(get(key) as? JsonPrimitive)?.takeIf{it.isString}?.content
    require(body.string("status")=="ok"){"Web search did not return usable evidence"}
    val rows=mutableListOf<JsonObject>()
    fun evidence()=buildJsonObject {
        put("status","ok");put("provider",body.string("provider")?.take(80) ?: "Web search")
        (body["fetchedAtMs"] as? JsonPrimitive)?.longOrNull?.takeIf{it>0}?.let{put("fetchedAtMs",it)}
        put("results",JsonArray(rows.toList()))
    }
    for(raw in (body["results"] as? JsonArray).orEmpty().take(10)) {
        val row=raw as? JsonObject ?: continue
        val url=row.string("url")?.takeIf(::safeChatSourceUrl) ?: continue
        rows+=buildJsonObject{put("url",url);put("title",row.string("title").orEmpty().take(200));put("snippet",row.string("snippet").orEmpty().take(500))}
        if(evidence().toString().toByteArray(Charsets.UTF_8).size>16384){rows.removeAt(rows.lastIndex);break}
        if(rows.size==6)break
    }
    return evidence()
}
