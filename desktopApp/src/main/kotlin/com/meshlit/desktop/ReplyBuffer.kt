package com.meshlit.desktop

/** Lossless bounded text accumulation; UI snapshots are coalesced, native token
 * metrics remain supplied by the engine. Single writer, no per-token UI blocking. */
internal class ReplyBuffer(private val clock: () -> Long = System::nanoTime) {
    private val text = StringBuilder()
    private var lastPublish: Long? = null
    fun append(chunk: String): String? {
        require(chunk.length <= 131072-text.length) { "Reply exceeds display limit" }
        text.append(chunk)
        val now = clock()
        // Formatting and laying out the whole Markdown reply becomes more costly
        // as it grows. Keep short replies fluid, then bound long-reply redraws.
        val interval = when {
            text.length >= 32_768 -> 200_000_000L
            text.length >= 8_192 -> 100_000_000L
            else -> 33_000_000L
        }
        if (lastPublish == null || now-lastPublish!! >= interval) {
            lastPublish = now; return text.toString()
        }
        return null
    }
    fun snapshot() = text.toString()
}
