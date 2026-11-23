package com.meshlit.core.gibberlink
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
class GibberLinkCodecTest {
    @Test fun spectrumMatchesIndependentHannWindowedDft() {
        assumeTrue(System.getProperty("gibberlink.host.test") == "true")
        val codec = GibberLinkCodec()
        val pcm = ByteArray(181) { (it * 71 - 19).toByte() }
        for (length in listOf(0, 1, 3, 47, 128, pcm.size)) {
            val frame = pcm.copyOf(length)
            for (count in listOf(0, 1, 9, 32)) {
                val expected = DoubleArray(count) { bin ->
                    var real = 0.0
                    var imag = 0.0
                    for (i in 0 until minOf(64, frame.size / 2)) {
                        val sample = (frame[2*i].toInt() and 255) + 256 * frame[2*i+1]
                        val value = sample * (1 - kotlin.math.cos(2 * Math.PI * i / 63)) / 65536.0
                        val angle = 2 * Math.PI * bin * i / 64
                        real += value * kotlin.math.cos(angle)
                        imag -= value * kotlin.math.sin(angle)
                    }
                    kotlin.math.hypot(real, imag)
                }
                val peak = expected.maxOrNull() ?: 0.0
                val actual = FloatArray(count) { -1f }
                codec.spectrum(frame, actual)
                for (bin in expected.indices) {
                    assertEquals(if (peak == 0.0) 0.0 else expected[bin] / peak, actual[bin].toDouble(), 1e-5)
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { codec.spectrum(pcm, FloatArray(33)) }
    }
    @Test fun actualPcmRoundTripAndInvalidHandles() {
        assumeTrue(System.getProperty("gibberlink.host.test") == "true")
        val codec = GibberLinkCodec(); val tx = codec.open(); val rx = codec.open()
        try {
            val text = "Hello agent. The English transcript is visible."
            val pcm = codec.encode(tx, text.toByteArray(Charsets.US_ASCII))
            assertTrue(pcm.any { it != 0.toShort() })
            var recovered: String? = null
            val audio = pcm + ShortArray(48000)
            for (offset in audio.indices step 1024) {
                val frame = audio.copyOfRange(offset, minOf(offset + 1024, audio.size))
                codec.decode(rx, frame, frame.size)?.let { recovered = it.toString(Charsets.US_ASCII) }
            }
            assertEquals(text, recovered)
            assertThrows(IllegalArgumentException::class.java) { codec.encode(tx, ByteArray(97) { 65 }) }
            assertThrows(IllegalArgumentException::class.java) { codec.decode(rx, ShortArray(1), 4097) }
            assertThrows(IllegalArgumentException::class.java) { codec.encode(-1, byteArrayOf(65)) }
        } finally { codec.close(tx); codec.close(rx) }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(rx, ShortArray(1024), 1024) }
    }
    @Test fun silenceCannotInventTranscript() {
        assumeTrue(System.getProperty("gibberlink.host.test") == "true")
        val c = GibberLinkCodec(); val rx = c.open()
        try { repeat(50) { assertNull(c.decode(rx, ShortArray(1024), 1024)) } } finally { c.close(rx) }
    }
}
