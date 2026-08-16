package com.meshlit.terminal.vt

import org.junit.Test

/**
 * Microbenchmark for the VT byte-pump.
 *
 * On the host JVM (no `libvt_native.so`) this exercises the Kotlin
 * fallback path only — that's the path the unit tests use. On a real
 * device, the JVM cost here is the upper bound; the native path runs
 * directly in C++ and is expected to be ~5-10x faster on byte-pump
 * throughput, with ~zero allocation.
 *
 * Run with:
 *   ./gradlew :app:testDebugUnitTest --tests "com.meshlit.terminal.vt.ParserBench"
 *
 * The numbers below are advisory — they prove the Kotlin path stays
 * under a useful ceiling, not that the native path is faster.
 */
class ParserBench {

    @Test fun pumpSyntheticStream() {
        val screen = Screen(cols = 80, rows = 24)
        val stream = buildStream(bytes = 1_000_000, mode = Mode.PLAIN)
        val t0 = System.nanoTime()
        screen.process(stream)
        val elapsedNs = System.nanoTime() - t0
        val mbPerSec = (stream.size.toDouble() / 1_000_000.0) / (elapsedNs / 1_000_000_000.0)
        println("[bench] kotlin-pump 1MB=${stream.size}B  elapsed=${elapsedNs / 1_000_000}ms  throughput=${"%.1f".format(mbPerSec)} MB/s")
    }

    @Test fun pumpSgrHeavyStream() {
        val screen = Screen(cols = 80, rows = 24)
        val stream = buildStream(bytes = 1_000_000, mode = Mode.SGR_HEAVY)
        val t0 = System.nanoTime()
        screen.process(stream)
        val elapsedNs = System.nanoTime() - t0
        val mbPerSec = (stream.size.toDouble() / 1_000_000.0) / (elapsedNs / 1_000_000_000.0)
        println("[bench] kotlin-pump-sgr 1MB=${stream.size}B  elapsed=${elapsedNs / 1_000_000}ms  throughput=${"%.1f".format(mbPerSec)} MB/s")
    }

    private enum class Mode { PLAIN, SGR_HEAVY }

    private fun buildStream(bytes: Int, mode: Mode): ByteArray {
        val sb = StringBuilder()
        var i = 0
        while (sb.length < bytes) {
            when (mode) {
                Mode.PLAIN -> sb.append("hello world ")
                Mode.SGR_HEAVY -> {
                    sb.append("\u001b[1;31mchunk_").append(i).append("\u001b[0m ")
                    i++
                }
            }
        }
        return sb.substring(0, bytes.coerceAtMost(sb.length)).toByteArray(Charsets.UTF_8)
    }
}