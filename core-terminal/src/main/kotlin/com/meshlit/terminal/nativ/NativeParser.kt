package com.meshlit.terminal.nativ

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * Thin Kotlin wrapper around the C++ byte-pump in libvt_native.so.
 *
 * The native side emits a packed `IntArray` action buffer with this
 * layout (one int per word, little-endian on Android):
 *
 *   word 0 : kind (1=CSI, 2=OSC, 3=DCS, 4=ESC)
 *   word 1 : aux0 (final byte for CSI/DCS/ESC; cmd number for OSC)
 *   word 2 : aux1 (intermediate[0] for CSI/DCS/ESC)
 *   word 3 : aux2 (reserved; 0)
 *   word 4 : aux3 (number of payload words following the header)
 *   word 5..5+aux3-1 : payload (numeric CSI params or UTF-16LE text code units)
 *   word N : END_MARKER (0x7FFFFFFF) — terminator between actions
 *
 * The Kotlin dispatcher walks the buffer in a tight loop and emits
 * the same `Parser.CsiAction` / `OscAction` / `DcsAction` / `EscAction`
 * payloads the original pure-Kotlin parser produced, so the
 * downstream [Dispatch] handlers don't change.
 */
object NativeParser {

    private const val ACTION_KIND_CSI = 1
    private const val ACTION_KIND_OSC = 2
    private const val ACTION_KIND_DCS = 3
    private const val ACTION_KIND_ESC = 4

    private const val PRINT_MARKER = 0xFF

    @Volatile private var loaded: Boolean = false
    @Volatile private var loadFailed: Boolean = false

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadFailed) return false
        return try {
            System.loadLibrary("vt_native")
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
            false
        }
    }

    @JvmStatic external fun nativeVersion(): String

    /**
     * Pump a byte stream through the native parser. Returns a list of
     * [Action] records the JVM side can replay into [Dispatch].
     * Returns null if the native library is not available — callers
     * should fall back to the pure-Kotlin implementation.
     */
    fun feed(bytes: ByteArray, onPrint: (Int) -> Unit): List<Action>? {
        if (!ensureLoaded()) return null
        if (bytes.isEmpty()) return emptyList()
        val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        input.put(bytes)
        input.position(0)

        // Conservative output capacity: 8 ints per input byte. Real-world
        // mixes rarely exceed 2 ints/byte, but OSC text payloads scale
        // with input length so we leave headroom.
        val outCap = (bytes.size * 8).coerceAtLeast(64)
        val outBytes = outCap * 4
        val output = ByteBuffer.allocateDirect(outBytes).order(ByteOrder.nativeOrder())
        val written = nativeParse(input, bytes.size, output)
        if (written <= 0) return emptyList()

        output.position(0)
        val intBuf: IntBuffer = output.order(ByteOrder.nativeOrder()).asIntBuffer()
        val actions = ArrayList<Action>()
        while (intBuf.position() < written) {
            val kind = intBuf.get()
            if (kind == 0x7FFFFFFF) continue
            val aux0 = intBuf.get()
            val aux1 = intBuf.get()
            val aux2 = intBuf.get()
            val payloadLen = intBuf.get()
            val payload = IntArray(payloadLen) { intBuf.get() }
            when (kind) {
                ACTION_KIND_CSI -> {
                    if (aux0 == PRINT_MARKER) {
                        onPrint(aux1)
                    } else {
                        // payload layout: [groupSize, p0, p1, ..., groupSize, p0, ...]
                        val groups = ArrayList<IntArray>()
                        var i = 0
                        while (i < payload.size) {
                            val size = payload[i]
                            i++
                            val arr = IntArray(size)
                            for (k in 0 until size) arr[k] = payload[i + k]
                            groups += arr
                            i += size
                        }
                        actions += Action.Csi(
                            finalByte = aux0.toChar(),
                            intermediate = if (aux1 == 0) "" else aux1.toChar().toString(),
                            params = groups,
                        )
                    }
                }
                ACTION_KIND_OSC -> actions += Action.Osc(cmd = aux0, text = intArrayToUtf8(payload))
                ACTION_KIND_DCS -> actions += Action.Dcs(finalByte = aux0.toChar(), intermediate = if (aux1 == 0) "" else aux1.toChar().toString(), params = listOf(payload), data = intArrayToUtf8(payload))
                ACTION_KIND_ESC -> actions += Action.Esc(finalByte = aux0.toChar(), intermediate = if (aux1 == 0) "" else aux1.toChar().toString())
            }
        }
        return actions
    }

    private external fun nativeParse(input: ByteBuffer, inputLength: Int, output: ByteBuffer): Int

    private fun intArrayToUtf8(payload: IntArray): String {
        if (payload.isEmpty()) return ""
        val bytes = ByteArray(payload.size)
        for (i in payload.indices) bytes[i] = payload[i].toByte()
        return String(bytes, Charsets.UTF_8)
    }

    /** Native action shape. Mirrors the Kotlin Parser.*Action data classes. */
    sealed class Action {
        data class Csi(val finalByte: Char, val intermediate: String, val params: List<IntArray>) : Action()
        data class Osc(val cmd: Int, val text: String) : Action()
        data class Dcs(val finalByte: Char, val intermediate: String, val params: List<IntArray>, val data: String) : Action()
        data class Esc(val finalByte: Char, val intermediate: String) : Action()
    }
}