package com.meshlit.qualification

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlit.core.gibberlink.GibberLinkCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Executes the APK's actual ABI library. PCM proof is separate from speaker/microphone delivery. */
@RunWith(AndroidJUnit4::class)
class GibberLinkNativeDeviceTest {
    @Test fun packagedCodecRecoversEnglishFromMicrophoneSizedFrames() {
        val codec = GibberLinkCodec()
        val tx = codec.open()
        val rx = codec.open()
        try {
            val english = "Meshlit agent: ready. English transcript verified."
            val pcm = codec.encode(tx, english.toByteArray(Charsets.US_ASCII))
            assertTrue(pcm.any { it != 0.toShort() })
            var recovered: ByteArray? = null
            val audio = pcm + ShortArray(48000)
            for (offset in audio.indices step 1024) {
                val frame = audio.copyOfRange(offset, minOf(offset + 1024, audio.size))
                codec.decode(rx, frame, frame.size)?.let { recovered = it }
            }
            assertArrayEquals(english.toByteArray(Charsets.US_ASCII), recovered)
        } finally {
            codec.close(tx)
            codec.close(rx)
        }
        assertThrows(IllegalArgumentException::class.java) { codec.decode(rx, ShortArray(1024), 1024) }
    }

    @Test fun silenceCannotCreateAPeerMessage() {
        val codec = GibberLinkCodec()
        val rx = codec.open()
        try {
            repeat(50) { assertNull(codec.decode(rx, ShortArray(1024), 1024)) }
        } finally {
            codec.close(rx)
        }
    }
}
