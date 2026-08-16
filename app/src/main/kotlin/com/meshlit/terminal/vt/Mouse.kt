package com.meshlit.terminal.vt

/**
 * Mouse reporting state. Modeled after Ghostty's `terminal/mouse.zig`.
 * Encapsulates the *protocol* (x10 / sgr / urxvt / sgr-pixels) and
 * the *mode* (off / press / drag / any). Today's Compose touch
 * surface doesn't capture — these flags just sit there for future
 * pty-bridge work.
 */
class MouseState {
    var protocol: Protocol = Protocol.None
    var mode: Mode = Mode.Off
    /** UTF-8 vs default encoding (DECSET 1005). */
    var utf8: Boolean = false

    fun reset() {
        protocol = Protocol.None
        mode = Mode.Off
        utf8 = false
    }

    enum class Protocol {
        None,
        X10,
        Sgr,
        Urxvt,
        SgrPixels,
    }

    enum class Mode {
        Off,
        Press,
        Drag,
        Any,
    }
}