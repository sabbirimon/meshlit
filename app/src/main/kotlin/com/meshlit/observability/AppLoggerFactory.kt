package com.meshlit.observability

import com.meshlit.core.common.MeshlitLogger
import com.meshlit.core.common.Slf4jLogger
import com.meshlit.core.common.logger

/**
 * App-wide logger factory. The first call wires the [LogBuffer] as a
 * sink on top of SLF4J, so every existing
 *
 *   `logger("Something")`
 *
 * returns a [MeshlitLogger] that emits to BOTH logcat (via SLF4J →
 * logback) AND the process-wide in-memory ring buffer read by
 * `LogScreen`.
 *
 * Idempotent — re-installing the sink chain is harmless.
 */
object AppLoggerFactory {

    private var installed = false
    lateinit var buffer: LogBuffer
        private set

    @Synchronized
    fun install(buffer: LogBuffer = LogBuffer()) {
        if (installed) return
        this.buffer = buffer
        // We do not replace the global SLF4J binding (that would
        // require a logback.xml rewrite and isn't worth it for v1).
        // Instead, every `logger(name)` the app creates goes through
        // [appLogger] which fans out.
        installed = true
    }

    /**
     * The factory every `:app` caller uses. Returns a wrapper that
     * mirrors to the [LogBuffer] and the underlying SLF4J logger.
     */
    fun appLogger(name: String): MeshlitLogger {
        install()
        return TeeLogger(slf4j = logger(name), buffer = buffer)
    }

    fun appLogger(forClass: Class<*>): MeshlitLogger {
        install()
        return TeeLogger(slf4j = logger(forClass), buffer = buffer)
    }
}

/**
 * Fan-out logger: delegates to a SLF4J backend while also appending
 * into the in-memory ring buffer. Cheap — single virtual dispatch
 * per line.
 */
class TeeLogger(
    private val slf4j: MeshlitLogger,
    private val buffer: MeshlitLogger,
) : MeshlitLogger {
    override fun info(tag: String, message: String, context: Map<String, Any?>) {
        buffer.info(tag, message, context)
        slf4j.info(tag, message, context)
    }
    override fun warn(tag: String, message: String, context: Map<String, Any?>) {
        buffer.warn(tag, message, context)
        slf4j.warn(tag, message, context)
    }
    override fun error(tag: String, message: String, error: Throwable?, context: Map<String, Any?>) {
        buffer.error(tag, message, error, context)
        slf4j.error(tag, message, error, context)
    }
}
