package com.meshlit.core.inference

import ai.runanywhere.proto.v1.LogEntry
import com.runanywhere.sdk.infrastructure.logging.LogDestination
import com.runanywhere.sdk.infrastructure.logging.Logging

/** The pinned SDK exposes llama.cpp's final context and decoder counters through its local log destination. */
internal class LlamaRuntimeMeasurements : LogDestination {
    override val identifier = "meshlit-llama-" + java.util.UUID.randomUUID()
    override val isAvailable = true
    @Volatile var contextSize: Int? = null
        private set
    @Volatile var inputTokens: Int? = null
        private set
    @Volatile var outputTokens: Int? = null
        private set
    @Volatile var decodeRate: Float? = null
        private set
    private var worker: Int? = null
    private var ambiguous = false
    private var decodeStart: Long? = null

    suspend fun <T> measure(operation: suspend () -> T): T {
        Logging.addDestinationSync(this)
        return try { operation() } finally { Logging.removeDestinationSync(this) }
    }

    @Synchronized override fun write(entry: LogEntry) {
        if (entry.category != "LLM.LlamaCpp" ||
            entry.metadata["source_file"]?.substringBeforeLast(':') != "llamacpp_backend.cpp") return
        // Only recognize complete backend diagnostics. Never retain prompts or arbitrary log text.
        val message = entry.message.removePrefix("[Native] ")
        when (entry.metadata["source_function"]) {
            "load_model" -> CONTEXT.matchEntire(message)?.let { contextSize = it.groupValues[1].toIntOrNull() }
            "generate_stream" -> {
                val start = PROMPT.matchEntire(message)
                if (start != null) {
                    if (worker != null) {
                        ambiguous = true
                        inputTokens = null
                        outputTokens = null
                        decodeRate = null
                    } else {
                        worker = android.os.Process.myTid()
                        inputTokens = start.groupValues[1].toIntOrNull()
                    }
                    return
                }
                if (ambiguous || worker != android.os.Process.myTid()) return
                if (message == "generate_stream: prompt decoded successfully") decodeStart = System.nanoTime()
                COMPLETE.matchEntire(message)?.let {
                    val count = it.groupValues[1].toIntOrNull() ?: return
                    outputTokens = count
                    val elapsed = decodeStart?.let { t -> System.nanoTime() - t }
                    if (count > 0 && elapsed != null && elapsed > 0) decodeRate = (count * 1e9 / elapsed).toFloat()
                }
            }
        }
    }

    override fun flush() = Unit

    private companion object {
        val CONTEXT = Regex("""Final context size: (\d+) \(fitted=\d+, train=\d+, cap=\d+\)""")
        val PROMPT = Regex("""generate_stream: processing (\d+) prompt tokens in chunks of \d+""")
        val COMPLETE = Regex("""Generation complete: (\d+) tokens""")
    }
}
