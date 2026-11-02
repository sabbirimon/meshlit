package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException

/** Forward bounded native SSE chunks without waiting for the entire reply.
 * The caller admits usage before reading and settles it before publishing DONE. */
internal fun readIdeCompletionStream(input: InputStream, onChunk: (JsonObject) -> Unit): JsonObject {
    val stream = input.buffered(8192)
    val line = ByteArrayOutputStream()
    val event = StringBuilder()
    var total = 0
    var ended = false
    var completedChoice = false
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
        put("streamed", true); usage?.let { put("usage", it) }
        put("terminalChunks", JsonArray(terminalChunks))
    }
}
