package com.meshlit.terminal.vt

/**
 * Terminal colour model. Covers 16-colour (palette 0..15), 256-colour
 * (palette 0..255), and 24-bit truecolor (RGB). Modeled after
 * Ghostty's `Style.Color` (tagged union `{none | palette | rgb}` —
 * <https://github.com/ghostty-org/ghostty>).
 *
 * The 16 standard ANSI palette is fixed at construction (xterm
 * defaults); the 256-colour extended palette is mutated by OSC 4.
 */
sealed class Color {
    data object None : Color()

    /** Index into the palette. 0..15 uses the standard 16-colour
     *  table; 16..255 uses the 256-colour cube / grayscale ramp. */
    data class Palette(val index: Int) : Color()

    /** 24-bit RGB. Packed `0xRRGGBB`. */
    data class Rgb(val rgb: Int) : Color()

    companion object {
        /** xterm default 16-colour palette. */
        val STANDARD_PALETTE: IntArray = intArrayOf(
            0x000000, // 0  Black
            0xCD0000, // 1  Red
            0x00CD00, // 2  Green
            0xCDCD00, // 3  Yellow
            0x0000EE, // 4  Blue
            0xCD00CD, // 5  Magenta
            0x00CDCD, // 6  Cyan
            0xE5E5E5, // 7  White
            0x7F7F7F, // 8  Bright Black
            0xFF0000, // 9  Bright Red
            0x00FF00, // 10 Bright Green
            0xFFFF00, // 11 Bright Yellow
            0x5C5CFF, // 12 Bright Blue
            0xFF00FF, // 13 Bright Magenta
            0x00FFFF, // 14 Bright Cyan
            0xFFFFFF, // 15 Bright White
        )

        /** Resolve a [Color] against a 256-entry palette. */
        fun resolve(color: Color, palette: IntArray): Int = when (color) {
            None -> 0
            is Palette -> palette[color.index.coerceIn(0, 255)]
            is Rgb -> color.rgb
        }

        /** Build the xterm 6×6×6 colour cube + grayscale ramp. */
        fun extendedPalette(): IntArray {
            val out = IntArray(256)
            // 0..15: standard
            STANDARD_PALETTE.copyInto(out, 0, 0, 16)
            // 16..231: 6×6×6 cube
            var i = 16
            for (r in 0..5) {
                for (g in 0..5) {
                    for (b in 0..5) {
                        out[i++] = ((if (r == 0) 0 else 55 + r * 40) shl 16) or
                            ((if (g == 0) 0 else 55 + g * 40) shl 8) or
                            (if (b == 0) 0 else 55 + b * 40)
                    }
                }
            }
            // 232..255: 24-step grayscale ramp
            for (k in 0..23) {
                val v = 8 + k * 10
                out[232 + k] = (v shl 16) or (v shl 8) or v
            }
            return out
        }
    }
}

/**
 * 256-entry mutable palette. OSC 4 reads + writes index into this.
 * The standard 16 colours and the 6×6×6 cube are seeded from the
 * xterm defaults; callers can override per index.
 */
class Palette {
    private val colors: IntArray = Color.extendedPalette()
    var version: Int = 0
        private set

    operator fun get(index: Int): Int = colors[index.coerceIn(0, 255)]

    fun set(index: Int, rgb: Int) {
        colors[index.coerceIn(0, 255)] = rgb
        version++
    }

    fun reset(index: Int) {
        colors[index.coerceIn(0, 255)] = if (index < 16) {
            Color.STANDARD_PALETTE[index]
        } else {
            // Recompute extended entries on demand.
            0
        }
        version++
    }

    fun snapshot(): IntArray = colors.copyOf()
}
