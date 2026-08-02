package com.meshlit.observability

import com.meshlit.core.common.MeshlitLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide in-memory log sink. Holds at most [MAX_ENTRIES]
 * entries; older lines are evicted FIFO. Read by `LogScreen` via the
 * [entries] StateFlow; filtered by tag / level on the screen side.
 *
 * Why we keep our own ring buffer and not just hook logback:
 *  - Existing logging goes through SLF4J → logback (Android default)
 *    → logcat. Tap-up `MeshlitLogger` so user-visible logs also land in
 *    the app without dragging in a new dependency.
 *  - `LogScreen` filters on tag/level without the cost of grepping a
 *    multi-MB logcat buffer.
 *  - Export to file ships the same ring buffer (no second copy).
 *
 * Threading:
 *  - Every entry is appended under a single `update` so readers see
 *    a consistent immutable list snapshot. Appends are O(1) amortized
 *    (ArrayList `add` after head-trim).
 *  - The optional sink chain (SLF4J + logcat) is invoked outside the
 *    StateFlow update so a slow sink can't stall emission.
 */
class LogBuffer(
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : MeshlitLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestampMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val context: Map<String, Any?> = emptyMap(),
        val errorMessage: String? = null,
    ) {
        /** Single-line export representation. Context flattened. */
        fun format(): String {
            val sb = StringBuilder()
            sb.append('[').append(timestampMs).append("] ")
            sb.append('[').append(level.name).append("] ")
            sb.append('[').append(tag).append("] ")
            sb.append(message)
            if (context.isNotEmpty()) {
                sb.append(" ctx={")
                context.entries.joinTo(sb, separator = ",") { "${it.key}=${it.value}" }
                sb.append('}')
            }
            if (errorMessage != null) sb.append(" err=").append(errorMessage)
            return sb.toString()
        }
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Chainable sinks. By default everything falls through to
     *  logcat via the SLF4J backend so external tools still see logs.
     *  Tests can swap in a no-op chain. */
    private val sinks = AtomicReference<List<MeshlitLogger>>(listOf())

    fun replaceSinks(newSinks: List<MeshlitLogger>) {
        sinks.set(newSinks)
    }

    override fun info(tag: String, message: String, context: Map<String, Any?>) {
        append(Level.INFO, tag, message, context, errorMessage = null)
        sinks.get().forEach { it.info(tag, message, context) }
    }

    override fun warn(tag: String, message: String, context: Map<String, Any?>) {
        append(Level.WARN, tag, message, context, errorMessage = null)
        sinks.get().forEach { it.warn(tag, message, context) }
    }

    override fun error(
        tag: String,
        message: String,
        error: Throwable?,
        context: Map<String, Any?>,
    ) {
        append(Level.ERROR, tag, message, context, error?.message)
        sinks.get().forEach { it.error(tag, message, error, context) }
    }

    private fun append(
        level: Level,
        tag: String,
        message: String,
        context: Map<String, Any?>,
        errorMessage: String?,
    ) {
        val entry = Entry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            context = context,
            errorMessage = errorMessage,
        )
        _entries.update { current ->
            val next = ArrayList<Entry>(current.size + 1)
            next.addAll(current)
            next.add(entry)
            // Trim from the front so we don't grow unbounded.
            if (next.size > maxEntries) {
                next.subList(next.size - maxEntries, next.size).toList()
            } else next
        }
    }

    /** Wipe the buffer. Used by the LogScreen "Clear" button. */
    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 2_000
    }
}
