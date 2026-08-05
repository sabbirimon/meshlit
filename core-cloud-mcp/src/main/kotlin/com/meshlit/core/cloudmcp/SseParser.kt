package com.meshlit.core.cloudmcp

/**
 * Hand-rolled SSE (Server-Sent Events) parser. Mirrors the
 * line-by-line reader at `RemoteInferenceClient.kt:136-174` so
 * the project stays consistent — no `okhttp3.sse` dependency.
 *
 * Pure function: takes a `Sequence<String>` of raw response body
 * lines (split on `\n` *or* `\r\n`, with the trailing CR stripped)
 * and emits a `Flow<SseEvent>` of structured events.
 *
 * SSE wire format (RFC: https://html.spec.whatwg.org/multipage/server-sent-events.html):
 *
 *   - Lines starting with `:` are comments; ignore.
 *   - Empty lines dispatch the accumulated event.
 *   - `event: <name>` sets the event name (default: `message`).
 *   - `data: <text>` appends to the data buffer, joined by `\n`.
 *   - `id: <value>` sets the last-event-id (not surfaced here).
 *   - `retry: <ms>` sets the reconnect delay (not surfaced here).
 *
 * Multiple `data:` lines are joined with `\n` per the spec. After
 * dispatch, the data buffer is cleared but the event name is held
 * until the next `event:` line — matches the upstream behavior we
 * observe against the NaraRouter reference server.
 */
data class SseEvent(
    val event: String,
    val data: String,
)

/**
 * Parse a stream of SSE lines into discrete events. The parser
 * keeps internal state across calls so a partially-received
 * `data:` line can be completed by the next line.
 */
class SseParser {
    private var eventName: String = "message"
    private val dataBuffer = StringBuilder()

    /**
     * Feed a single new line (without the trailing `\n` / `\r\n`)
     * and return the list of events that should be dispatched. The
     * parser never returns more than one event per call (data is
     * one-line per SSE frame); returns an empty list when the line
     * is part of an in-flight event.
     */
    fun feed(rawLine: String): List<SseEvent> {
        // Lines must be split on \n BEFORE being passed in, and the
        // trailing \r must already be stripped (the OkHttp ResponseBody
        // .string() + split("\n") pipeline does both).
        val line = rawLine.trimEnd('\r')
        when {
            line.isEmpty() -> {
                // Dispatch boundary.
                if (dataBuffer.isNotEmpty() || eventName != "message") {
                    val ev = SseEvent(event = eventName, data = dataBuffer.toString())
                    dataBuffer.clear()
                    return listOf(ev)
                }
            }
            line.startsWith(":") -> {
                // Comment — ignore.
            }
            line.startsWith("event:") -> {
                eventName = line.substringAfter("event:").trimStart()
            }
            line.startsWith("data:") -> {
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(line.substringAfter("data:").trimStart())
            }
            // `id:` and `retry:` are accepted per spec; we don't
            // expose them yet.
        }
        return emptyList()
    }

    /**
     * Drain any pending event. Call this when the stream ends so a
     * trailing event without a final newline still gets dispatched.
     */
    fun flush(): SseEvent? {
        if (dataBuffer.isEmpty() && eventName == "message") return null
        val ev = SseEvent(event = eventName, data = dataBuffer.toString())
        dataBuffer.clear()
        eventName = "message"
        return ev
    }
}
