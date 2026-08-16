package com.meshlit.terminal.vt

/**
 * SGR (Select Graphic Rendition) attribute model. Modeled after
 * Ghostty's `style.Style` + `Attribute` tagged union. Bold/Italic/
 * Faint/Blink/Inverse/Invisible/Strike are bit-packed into a single
 * Int so cell equality is cheap and a Compose render can detect
 * identical adjacent attrs in O(1).
 *
 * `fg`/`bg`/`underlineColor` carry the colour separately because
 * `Color` is a sealed type with payload. Equality is structural,
 * which is what we want.
 *
 * `underline` is the SGR 4 / 21 + 4:3 (curly) + 4:4 (dotted) family
 * — we model straight / double / curly / dotted to match ghostty.
 * SGR 21 folds into "no underline" (it's the de-facto "double
 * underline" legacy interpretation).
 */
class Attr(
    val fg: Color = Color.None,
    val bg: Color = Color.None,
    val underlineColor: Color = Color.None,
    val underline: Underline = Underline.None,
    val flags: Int = 0,
) {
    fun withFg(c: Color): Attr = Attr(c, bg, underlineColor, underline, flags)
    fun withBg(c: Color): Attr = Attr(fg, c, underlineColor, underline, flags)
    fun withUnderline(u: Underline): Attr = Attr(fg, bg, underlineColor, u, flags)
    fun withUnderlineColor(c: Color): Attr = Attr(fg, bg, c, underline, flags)
    fun withFlag(flag: Int, on: Boolean): Attr =
        Attr(fg, bg, underlineColor, underline,
            if (on) flags or flag else flags and flag.inv())

    override fun equals(other: Any?): Boolean =
        this === other || (other is Attr &&
            fg == other.fg && bg == other.bg && underlineColor == other.underlineColor &&
            underline == other.underline && flags == other.flags)

    override fun hashCode(): Int {
        var h = fg.hashCode()
        h = 31 * h + bg.hashCode()
        h = 31 * h + underlineColor.hashCode()
        h = 31 * h + underline.ordinal
        h = 31 * h + flags
        return h
    }

    companion object {
        const val BOLD = 1 shl 0
        const val ITALIC = 1 shl 1
        const val FAINT = 1 shl 2
        const val BLINK = 1 shl 3
        const val INVERSE = 1 shl 4
        const val INVISIBLE = 1 shl 5
        const val STRIKE = 1 shl 6
        const val OVERLINE = 1 shl 7

        val DEFAULT = Attr(fg = Color.Palette(7), bg = Color.Palette(0))
    }
}

enum class Underline {
    None,
    Single,
    Double,
    Curly,
    Dotted,
}