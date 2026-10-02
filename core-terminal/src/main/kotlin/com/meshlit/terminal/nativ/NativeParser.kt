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

    /**
     * Maximum number of payload words we'll accept from the native
     * parser per action. The C++ side uses an 8 ints-per-input-byte
     * upper bound, so the worst-case legitimate payload is roughly
     * 8 × (max input length). We pick a generous ceiling that still
     * rejects corrupted action headers (e.g. an uninitialized word
     * that reads as ~2^31 after sign extension). If the native
     * parser ever produces more, we drop the action rather than
     * OOM-ing the process.
     */
    private const val MAX_PAYLOAD_WORDS = 65_536

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
        // Defensive: never trust a `written` value larger than the buffer we
        // handed the native side. The C++ side could in principle misreport
        // (e.g. on a corrupted input) and the IntBuffer loop below would
        // happily read past the buffer.
        if (written > outBytes) {
            android.util.Log.w(
                "NativeParser",
                "nativeParse reported written=$written > outBytes=$outBytes — truncating",
            )
            return emptyList()
        }

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
            // Defensive bounds check: the native parser should never emit a
            // payload longer than `outCap` (which is bytes.size * 8). If it
            // does — e.g. because of a memory corruption or a release build
            // with a busted word boundary — the JVM would otherwise attempt
            // to allocate gigabytes of ints and the process would die with
            // an OOM long before any UI feedback. Skip and surface as a
            // truncated action; the next END_MARKER will re-sync the loop.
            val safeLen = if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_WORDS) {
                android.util.Log.w(
                    "NativeParser",
                    "dropping action with implausible payloadLen=$payloadLen (kind=$kind)",
                )
                continue
            } else {
                payloadLen
            }
            val payload = IntArray(safeLen) { intBuf.get() }
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
                            // Same defensive bound as the outer payload — a CSI
                            // param group is normally a handful of ints; a
                            // corrupted word must not OOM the process.
                            val safeGroupSize = if (size < 0 || size > MAX_PAYLOAD_WORDS) {
                                android.util.Log.w(
                                    "NativeParser",
                                    "dropping CSI group with implausible size=$size",
                                )
                                break
                            } else {
                                size
                            }
                            val arr = IntArray(safeGroupSize)
                            for (k in 0 until safeGroupSize) {
                                val idx = i + k
                                if (idx >= payload.size) {
                                    // Defensive: bail if the group claims more
                                    // words than the payload actually holds.
                                    // This avoids an AIOOBE if a CSI action's
                                    // payload is shorter than the first group
                                    // header claims.
                                    break
                                }
                                arr[k] = payload[idx]
                            }
                            groups += arr
                            i += safeGroupSize
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