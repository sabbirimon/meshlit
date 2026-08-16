package com.meshlit.terminal.vt

/**
 * DECSET / DECRST mode flags. Modeled after Ghostty's
 * `terminal/modes.zig` Mode enum. We expose only the subset the
 * commands we actually feed (see [TerminalSession]) will toggle;
 * the parser dispatches unknown mode numbers as no-ops rather than
 * errors so we stay forward-compatible with future shells.
 */
enum class Mode {
    /** DECCKM — application cursor keys vs ANSI. */
    CursorKeys,
    /** LNM — line feed / newline mode. */
    Lnm,
    /** IRM — insert/replace mode. */
    Irm,
    /** DECBPM — bracketed paste (2004). */
    BracketedPaste,
    /** DECSET 1000 — X11 mouse reporting. */
    MouseX10,
    /** DECSET 1006 — SGR mouse encoding. */
    MouseSgr,
    /** DECSET 1015 — urxvt mouse encoding. */
    MouseUrxvt,
    /** DECSET 1016 — SGR-pixel mouse encoding. */
    MouseSgrPx,
    /** DECSET 1004 — focus event reporting. */
    FocusEvent,
    /** DECSET 1049 — alternate screen. */
    AltScreen,
    /** DECKPAM / DECKPNM — application keypad. */
    AppKeypad,
    /** DECSET 2048 — in-band resize signalling. */
    InBandResize,
    /** DECAWM — auto-wrap mode (line wrap at end of row). */
    AutoWrap,
    /** DECOM — origin mode (cursor clamped to scroll region). */
    OriginMode,
}

/**
 * Packed mode flag store. O(1) toggle / contains / reset. We
 * serialise state on save by writing one bit per mode to a
 * LongArray — 14 modes fit in a single Long.
 */
class ModeSet {
    private val bits = java.util.EnumSet.noneOf(Mode::class.java)

    operator fun contains(mode: Mode): Boolean = bits.contains(mode)
    fun set(mode: Mode, on: Boolean) {
        if (on) bits.add(mode) else bits.remove(mode)
    }
    fun toggle(mode: Mode): Boolean {
        val on = !bits.contains(mode)
        if (on) bits.add(mode) else bits.remove(mode)
        return on
    }
    fun clear() = bits.clear()
    fun iterator(): Iterator<Mode> = bits.iterator()

    /** Snapshot as a stable ordered list (enumeration declaration order). */
    fun snapshot(): List<Mode> = Mode.entries.filter { it in bits }
}