package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException

/** Forward bounded native SSE chunks without waiting for the entire reply.
 * The caller admits usage before reading and settles it before publishing DONE. */
internal fun readIdeCompletionStream(input: InputStream, allowedToolNames: Set<String> = emptySet(), onChunk: (JsonObject) -> Unit): JsonObject {
    val stream = input.buffered(8192)
    val line = ByteArrayOutputStream()
    val event = StringBuilder()
    var total = 0
    var ended = false
    var completedChoice = false
    var hasFinalContent = false
    val toolParts = mutableMapOf<Int, JsonObject>()
    var usage: JsonObject? = null
    val terminalChunks = mutableListOf<JsonObject>()
    fun dispatch() {
        if (event.isEmpty()) return
        val data = event.toString().trimEnd('\n'); event.setLength(0)
        if (data == "[DONE]") { ended = true; return }
        val chunk = boundedJson(data, 65536)
        require(chunk["error"] == null) { "Native completion failed" }
        chunk["usage"]?.takeIf { it != JsonNull }?.let {
            val counts = it.jsonObject
            for (name in listOf("prompt_tokens", "completion_tokens")) {
                counts[name]?.takeIf { value -> value != JsonNull }?.let { value ->
                    require(value.jsonPrimitive.longOrNull?.let { count -> count in 0..10_000_000 } == true)
                }
            }
            usage = counts
        }
        chunk["choices"]?.jsonArray?.forEach {
            if ((it.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: 0) == 0 && it.jsonObject["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.isNotBlank() == true) hasFinalContent = true
            if ((it.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: 0) == 0) {
                (it.jsonObject["delta"]?.jsonObject?.get("tool_calls") as? JsonArray)?.forEach { fragment ->
                    val part = fragment.jsonObject
                    val index = part["index"]?.jsonPrimitive?.int ?: 0
                    require(index in 0..31)
                    val old = toolParts[index] ?: JsonObject(emptyMap())
                    val function = part["function"] as? JsonObject
                    val oldFunction = old["function"] as? JsonObject ?: JsonObject(emptyMap())
                    toolParts[index] = buildJsonObject {
                        for (field in listOf("id", "type")) (part[field] ?: old[field])?.let { put(field,it) }
                        put("function",buildJsonObject {
                            for(field in listOf("name","arguments")) {
                                val value = (oldFunction[field]?.jsonPrimitive?.contentOrNull.orEmpty() + function?.get(field)?.jsonPrimitive?.contentOrNull.orEmpty())
                                require(value.length <= 65536); put(field,value)
                            }
                        })
                    }
                }
            }
            if (it.jsonObject["finish_reason"]?.let { reason -> reason != JsonNull } == true) completedChoice = true
        }
        // Do not expose completion or usage until the caller commits settlement.
        if (completedChoice || usage != null) terminalChunks += chunk else onChunk(chunk)
    }
    while (!ended) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Completion cancelled")
        val value = stream.read()
        require(value != -1) { "Incomplete native completion stream" }
        require(++total <= 2_000_000) { "Native completion stream exceeds limit" }
        if (value == 10) {
            val text = line.toByteArray().decodeToString(throwOnInvalidSequence = true).trimEnd('\r'); line.reset()
            if (text.isEmpty()) dispatch()
            else if (text.startsWith("data:")) {
                event.append(text.removePrefix("data:").removePrefix(" ")).append('\n')
                require(event.length <= 65536)
            }
        } else {
            require(line.size() < 65536) { "Native completion line exceeds limit" }
            line.write(value)
        }
    }
    require(completedChoice) { "Native completion did not finish a choice" }
    return buildJsonObject {
        put("streamed", true); put("hasFinalContent", hasFinalContent); put("hasValidToolCalls", studioValidToolCalls(JsonArray(toolParts.toSortedMap().values.toList()), allowedToolNames)); usage?.let { put("usage", it) }
        put("terminalChunks", JsonArray(terminalChunks))
    }
}
