package com.meshlit.observability

/**
 * Marker that exists only so `logback.xml` can reference the class
 * name. We don't extend logback's `AppenderBase` directly because
 * that would require a `logback-classic` compile-time dep we don't
 * want to bring in just for a single class.
 *
 * Actual wiring: every `:app` code path that uses
 * [com.meshlit.observability.AppLoggerFactory.appLogger] emits to
 * both SLF4J (logcat) and the in-memory [LogBuffer]. That covers
 * everything the FGS does; `core-inference`'s SLF4J-only logs land
 * in logcat and are captured separately via `adb logcat Meshlit:V *:S`.
 *
 * Kept as an empty object so the class name stays discoverable for
 * future integration. */
object LogBufferAppender {
    fun attach(buffer: LogBuffer) {
        // No-op for now — app-side code routes through AppLoggerFactory.
        @Suppress("UNUSED_PARAMETER")
        buffer.let { /* placeholder */ }
    }
}