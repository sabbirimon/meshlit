package com.meshlit.terminal.vt

import com.meshlit.terminal.nativ.NativeParser

/**
 * VT100 byte-pump parser. Modeled after Ghostty's
 * `terminal/Parser.zig`. We follow the same state-transition shape
 * but inline the table as a `when (state)` because the Kotlin
 * JIT will fold this into the same jump table a static array
 * would produce on a JVM.
 *
 * The parser doesn't dispatch directly; it accumulates
 * CSI/OSC/DCS/ESC payloads and emits them as actions at the end
 * of each sequence. This keeps the dispatch file small and makes
 * the parser easy to fuzz against ghostty's fixtures.
 *
 * State diagram (subset; full list in ghostty's `parse_table.zig`):
 *
 *   Ground ── C0 ───────────────► Ground (apply C0 control)
 *      │                          ▲
 *      │ ESC ─────────────────────┘
 *      ▼
 *   Escape ── (any) ──► (dispatch ESC; back to Ground)
 *      │
 *      │ '[', ']' ─► CsiEntry / OscString
 *      │
 *      │  '(', ')', '*', '+', '$', 'P', 'X', '^', '_' ─► DcsEntry
 *
 *   CsiEntry ── params ──► CsiParam ── 'final' ─► (dispatch CSI; back)
 *   OscString ── ';' ───► OscString ── ST/BEL ──► (dispatch OSC; back)
 *   DcsEntry  ── 'ESC \' or 'BEL' ─► (dispatch DCS; back)
 */
object Parser {

    enum class State {
        Ground,
        Escape,
        CsiEntry,
        OscString,
        DcsEntry,
    }

    /**
     * CSI dispatch payload. `params` is the list of parameter
     * groups (`[12, 34]` for `CSI 12 ; 34 m`). `intermediate` is
     * the intermediate bytes (e.g. `SP` for `CSI 12 SP q`). `final`
     * is the final byte (`m` for SGR, `q` for DECSCUSR, `H` for
     * CUP, etc.).
     */
    data class CsiAction(
        val params: List<IntArray>,
        val intermediate: String,
        val final: Char,
    )

    /** OSC dispatch payload. `cmd` is the OSC number, `text` is the
     *  payload (decoded as UTF-8). */
    data class OscAction(val cmd: Int, val text: String)

    /** DCS dispatch payload. */
    data class DcsAction(
        val params: List<IntArray>,
        val intermediate: String,
        val final: Char,
        val data: String,
    )

    /** ESC dispatch payload — for sequences like `ESC =`,
     *  `ESC >`, `ESC 7` (DECSC), `ESC 8` (DECRC). */
    data class EscAction(val intermediate: String, val final: Char)

    fun feed(bytes: ByteArray, screen: Screen) {
        // Native fast path. The C++ byte-pump produces an action
        // stream we replay through the existing Dispatch handlers
        // below — the JVM side stays the source of truth for cell
        // mutation, the native side only owns the state machine.
        if (NATIVE_ENABLED) {
            feedNative(bytes, screen)
            return
        }
        feedKotlin(bytes, screen)
    }

    private fun feedNative(bytes: ByteArray, screen: Screen) {
        val actions = NativeParser.feed(bytes) { cp -> screen.putChar(cp) }
        if (actions == null) {
            // .so not loaded — fall back to the Kotlin parser for the
            // rest of the process lifetime. This can happen on host
            // JVM unit tests (no NDK) or on an unsupported ABI.
            NATIVE_ENABLED = false
            feedKotlin(bytes, screen)
            return
        }
        for (action in actions) {
            when (action) {
                is NativeParser.Action.Csi -> Dispatch.dispatchCsi(
                    CsiAction(action.params, action.intermediate, action.finalByte),
                    screen,
                )
                is NativeParser.Action.Osc -> Dispatch.dispatchOsc(
                    OscAction(action.cmd, action.text),
                    screen,
                )
                is NativeParser.Action.Dcs -> Dispatch.dispatchDcs(
                    DcsAction(action.params, action.intermediate, action.finalByte, action.data),
                    screen,
                )
                is NativeParser.Action.Esc -> Dispatch.dispatchEsc(
                    EscAction(action.intermediate, action.finalByte),
                    screen,
                )
            }
        }
    }

    @Volatile private var NATIVE_ENABLED: Boolean = true

    @Suppress("FunctionName")
    private fun feedKotlin(bytes: ByteArray, screen: Screen) {
        val state = State.Ground
        var current = state
        var params = mutableListOf<IntArray>()
        var param = IntArray(0)
        var intermediate = StringBuilder()
        var oscCmd = 0
        var oscCmdDone = false
        var oscText = StringBuilder()
        var dcsParams = mutableListOf<IntArray>()
        var dcsParam = IntArray(0)
        var dcsIntermediate = StringBuilder()
        var dcsFinal = ' '
        var dcsData = StringBuilder()

        for (b in bytes) {
            val c = b.toInt() and 0xFF
            when (current) {
                State.Ground -> {
                    when {
                        c == 0x1B -> current = State.Escape
                        c in 0x00..0x17 || c == 0x19 -> applyC0(c, screen)
                        c >= 0x20 -> screen.putChar(c)
                        // 0x7F is DEL — drop
                    }
                }
                State.Escape -> {
                    when {
                        c == 0x1B -> {
                            // ESC ESC — start over
                            current = State.Ground
                        }
                        c == '['.code -> {
                            current = State.CsiEntry
                            params = mutableListOf()
                            param = IntArray(0)
                            intermediate = StringBuilder()
                        }
                        c == ']'.code -> {
                            current = State.OscString
                            oscCmd = 0
                            oscCmdDone = false
                            oscText = StringBuilder()
                        }
                        c == 'P'.code || c == 'X'.code || c == '^'.code || c == '_'.code -> {
                            current = State.DcsEntry
                            dcsParams = mutableListOf()
                            dcsParam = IntArray(0)
                            dcsIntermediate = StringBuilder()
                            dcsFinal = ' '
                            dcsData = StringBuilder()
                        }
                        c == '('.code || c == ')'.code || c == '*'.code || c == '+'.code ||
                            c == '$'.code || c == '/'.code -> {
                            // Designate G0–G3 charset — we skip these.
                            // Consume one final byte then ground.
                            current = State.Ground
                        }
                        c >= 0x20 -> {
                            // ESC <final> like ESC =, ESC >, ESC 7, ESC 8
                            Dispatch.dispatchEsc(
                                EscAction(intermediate.toString(), c.toChar()),
                                screen,
                            )
                            current = State.Ground
                            intermediate = StringBuilder()
                        }
                        else -> current = State.Ground
                    }
                }
                State.CsiEntry -> {
                    when {
                        c == 0x1B -> {
                            // Abandon; treat as if terminator never came
                            current = State.Escape
                            params = mutableListOf()
                            param = IntArray(0)
                            intermediate = StringBuilder()
                        }
                        c in 0x30..0x39 -> {
                            // Digit — accumulate into current param
                            if (param.isEmpty()) {
                                param = IntArray(1)
                                param[0] = 0
                            }
                            val cur = param
                            param = IntArray(cur.size)
                            for (i in cur.indices) param[i] = cur[i]
                            param[param.size - 1] = param[param.size - 1] * 10 + (c - '0'.code)
                        }
                        c == ';'.code -> {
                            params.add(param)
                            param = IntArray(0)
                        }
                        c == ':'.code -> {
                            // Sub-parameter separator — flat for now
                            // (treated like ';').
                            params.add(param)
                            param = IntArray(0)
                        }
                        c in 0x20..0x2F -> {
                            // Intermediate
                            intermediate.append(c.toChar())
                        }
                        c in 0x3C..0x3F -> {
                            // Private-marker chars (`<`, `=`, `>`, `?`).
                            // We fold them into `intermediate` so the
                            // dispatcher can branch on `?…` for DECSET.
                            intermediate.append(c.toChar())
                        }
                        c in 0x40..0x7E -> {
                            // Final
                            if (param.isNotEmpty()) params.add(param)
                            Dispatch.dispatchCsi(
                                CsiAction(params.toList(), intermediate.toString(), c.toChar()),
                                screen,
                            )
                            current = State.Ground
                            params = mutableListOf()
                            param = IntArray(0)
                            intermediate = StringBuilder()
                        }
                        c in 0x00..0x17 || c == 0x19 -> applyC0(c, screen)
                        c == 0x7F -> { /* ignore */ }
                        c >= 0x80 -> {
                            // Treat as printable; many real-world
                            // streams put UTF-8 into the parameter
                            // field. We don't decode it here —
                            // spec doesn't require it.
                        }
                    }
                }
                State.OscString -> {
                    when {
                        c == 0x1B -> {
                            // ESC begins the ST terminator
                            current = State.Escape
                            if (oscText.isNotEmpty()) {
                                Dispatch.dispatchOsc(
                                    OscAction(oscCmd, oscText.toString()),
                                    screen,
                                )
                            }
                            oscCmd = 0
                            oscCmdDone = false
                            oscText = StringBuilder()
                        }
                        c == 0x07 -> {
                            // BEL terminator
                            Dispatch.dispatchOsc(
                                OscAction(oscCmd, oscText.toString()),
                                screen,
                            )
                            oscCmd = 0
                            oscCmdDone = false
                            oscText = StringBuilder()
                            current = State.Ground
                        }
                        c in 0x30..0x39 && !oscCmdDone -> {
                            oscCmd = oscCmd * 10 + (c - '0'.code)
                        }
                        c == ';'.code && !oscCmdDone -> {
                            oscCmdDone = true
                        }
                        c >= 0x20 -> {
                            if (!oscCmdDone) oscCmdDone = true
                            oscText.append(c.toChar())
                        }
                        c in 0x00..0x17 || c == 0x19 -> { /* ignore */ }
                        c == 0x7F -> { /* ignore */ }
                    }
                }
                State.DcsEntry -> {
                    when {
                        c == 0x1B -> {
                            current = State.Escape
                            if (dcsData.isNotEmpty()) {
                                Dispatch.dispatchDcs(
                                    DcsAction(
                                        dcsParams.toList(),
                                        dcsIntermediate.toString(),
                                        dcsFinal,
                                        dcsData.toString(),
                                    ),
                                    screen,
                                )
                            }
                            dcsParams = mutableListOf()
                            dcsParam = IntArray(0)
                            dcsIntermediate = StringBuilder()
                            dcsFinal = ' '
                            dcsData = StringBuilder()
                        }
                        c == 0x07 -> {
                            Dispatch.dispatchDcs(
                                DcsAction(
                                    dcsParams.toList(),
                                    dcsIntermediate.toString(),
                                    dcsFinal,
                                    dcsData.toString(),
                                ),
                                screen,
                            )
                            dcsParams = mutableListOf()
                            dcsParam = IntArray(0)
                            dcsIntermediate = StringBuilder()
                            dcsFinal = ' '
                            dcsData = StringBuilder()
                            current = State.Ground
                        }
                        c >= 0x20 -> dcsData.append(c.toChar())
                        c in 0x00..0x17 || c == 0x19 -> { /* ignore */ }
                        c == 0x7F -> { /* ignore */ }
                    }
                }
            }
        }

        // EOF: if we land in OscString, dispatch it.
        if (current == State.OscString && oscText.isNotEmpty()) {
            Dispatch.dispatchOsc(OscAction(oscCmd, oscText.toString()), screen)
        }
    }

    /**
     * Some shells emit the OSC cmd as a sequence of digits *without*
     * a semicolon — e.g. `ESC ]0;title` works because we read
     * digits in Ground mode until ';'. To support that, we hook
     * the digit accumulator into the OSC path. This helper is a
     * no-op placeholder that returns the leading param when the
     * dispatcher needs it.
     */
    private fun paramsOrDigitToInt(params: List<IntArray>, param: IntArray): Int {
        if (param.isNotEmpty()) return param.last()
        if (params.isNotEmpty()) return params.last().last()
        return 0
    }

    /**
     * Apply C0 control bytes (0x00..0x1F, 0x19). LF / CR / BS / HT
     * need to move the cursor or scroll; the rest are no-ops.
     */
    private fun applyC0(c: Int, screen: Screen) {
        when (c) {
            0x05 -> { /* ENQ — we'd answer with "Meshlit" if attached */ }
            0x07 -> { /* BEL — visual bell via title blink */ }
            0x08 -> {
                // BS — backspace
                screen.cursorBack(1)
            }
            0x09 -> {
                // HT — tab to next 8-col stop
                val next = ((screen.cursor.col / 8) + 1) * 8
                if (next < screen.cols) screen.cursorGoto(screen.cursor.row + 1, next + 1)
                else screen.cursorForward(screen.cols - screen.cursor.col)
            }
            0x0A -> {
                // LF — newline
                screen.cursorDown(1)
                if (screen.cursor.row == screen.rows - 1) {
                    screen.scrollUp(1)
                }
                if (Mode.Lnm in screen.modes) screen.cursorForward(1)
            }
            0x0B, 0x0C -> {
                // VT / FF — vertical tab / form feed
                screen.cursorDown(1)
            }
            0x0D -> {
                // CR — carriage return
                screen.cursorGoto(screen.cursor.row + 1, 1)
                screen.cursor.pendingWrap = false
            }
            0x0E, 0x0F -> { /* SO / SI — G1/G0 shift; ignored */ }
            0x11, 0x13 -> { /* XON / XOFF — flow control; ignored */ }
            else -> { /* ignore */ }
        }
    }
}

/** Helper to read the first (or only) param, defaulting to 0. */
internal fun Parser.CsiAction.firstParam(default: Int = 0): Int =
    params.firstOrNull()?.firstOrNull() ?: default

internal fun Parser.CsiAction.intParam(idx: Int, default: Int = 0): Int =
    params.getOrNull(idx)?.firstOrNull() ?: default

internal fun Parser.CsiAction.allParams(): IntArray {
    if (params.isEmpty()) return IntArray(0)
    val flat = IntArray(params.sumOf { it.size })
    var off = 0
    for (g in params) {
        for (v in g) {
            flat[off++] = v
        }
    }
    return flat
}