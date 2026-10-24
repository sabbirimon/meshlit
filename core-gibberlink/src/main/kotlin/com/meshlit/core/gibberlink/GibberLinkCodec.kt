package com.meshlit.core.gibberlink

/** Actual local PCM modem; not an LLM, identity verifier or encryption layer. */
class GibberLinkCodec {
    external fun open(): Int
    external fun close(handle: Int)
    external fun encode(handle: Int, english: ByteArray): ShortArray
    external fun decode(handle: Int, pcm: ShortArray, count: Int): ByteArray?
    companion object {
        init { System.loadLibrary("meshlit_gibberlink") }
        fun validateEnglish(text: String) {
            require(text.isNotBlank() && text.length in 1..96 && text.all { it.code in 32..126 }) {
                "Use 1–96 printable English characters. Translate before sending."
            }
        }
    }
}
