package com.meshlit.core.inference

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OfflineSpeechAudioTest {
    private fun floats(vararg values:Float)=ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply{values.forEach{putFloat(it)}}.array()
    private fun rejected(work:()->Unit){try{work();fail("Invalid audio accepted")}catch(_:IllegalArgumentException){}}
    @Test fun signedSamplesAndClippedPeaksKeepLittleEndianMeaning() {
        val bytes=speechFloatPcm16(floats(-1f,-0.5f,0f,0.5f,1f,2f,-2f),22050)
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertArrayEquals(shortArrayOf(-32768,-16384,0,16384,32767,32767,-32768),ShortArray(bytes.size/2){buffer.short})
        assertEquals(0,bytes[6].toInt());assertEquals(64,bytes[7].toInt())
    }
    @Test fun nonFiniteNativeOutputIsRejected() {
        for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) rejected{speechFloatPcm16(floats(0f,value),22050)}
    }
    @Test fun PartialAndEmptySamplesAreRejected() {
        for(length in listOf(0,1,2,3,5,7)) rejected{speechFloatPcm16(ByteArray(length),22050)}
    }
    @Test fun InvalidRatesAndExcessDurationAreRejectedBeforePlayback() {
        for(rate in listOf(0,7999,48001)) rejected{speechFloatPcm16(floats(0f),rate)}
        rejected{speechFloatPcm16(ByteArray(8000*4*180+4),8000)}
    }
}
