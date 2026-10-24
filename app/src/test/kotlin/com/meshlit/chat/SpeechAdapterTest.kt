package com.meshlit.chat

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.*

class SpeechAdapterTest {
    private val files=linkedMapOf("tiny-encoder.onnx" to byteArrayOf(1,2),"tiny-decoder.onnx" to byteArrayOf(3,4),"tiny-tokens.txt" to byteArrayOf(5))
    private fun pack()=SpeechPack(id="test-whisper",name="Validation fixture",kind=SpeechKind.WHISPER_STT,language="en",license="test-only",source="test fixture; not executable speech weights",files=files.map{(path,b)->SpeechFile(path,b.size.toLong(),MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)})})
    private fun zip(pack:SpeechPack=pack(),extra:Pair<String,ByteArray>?=null,corrupt:Boolean=false):ByteArray {
        val out=ByteArrayOutputStream();ZipOutputStream(out).use{z->
            fun add(name:String,b:ByteArray){z.putNextEntry(ZipEntry(name));z.write(b);z.closeEntry()}
            add("manifest.json",Json.encodeToString(SpeechPack.serializer(),pack).toByteArray())
            files.forEach{(p,b)->add(p,if(corrupt && p.endsWith("tokens.txt")) byteArrayOf(9) else b)}
            extra?.let{add(it.first,it.second)}
        };return out.toByteArray()
    }
    @Test fun exactManifestFilesAreVerifiedAndUnsafeOrCorruptImportsLeaveNoStage()=runBlocking {
        val root=Files.createTempDirectory("voice-packs").toFile()
        try {
            val success=File(root,"valid");assertEquals(pack(),unpackSpeech(ByteArrayInputStream(zip()),success))
            assertArrayEquals(files.getValue("tiny-encoder.onnx"),File(success,"tiny-encoder.onnx").readBytes())
            for(bytes in listOf(zip(extra="../outside" to byteArrayOf(1)),zip(extra="unlisted.bin" to byteArrayOf(1)),zip(corrupt=true),zip(pack().copy(files=pack().files+SpeechFile("missing.txt",1,"0".repeat(64)))))) {
                val stage=File(root,"rejected");var failed=false
                try{unpackSpeech(ByteArrayInputStream(bytes),stage)}catch(_:IllegalArgumentException){failed=true}catch(_:IllegalStateException){failed=true}
                assertTrue(failed);assertFalse(stage.exists());assertFalse(File(root,"outside").exists())
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun oversizedCountsPathsAndMissingRequiredModelsAreRejectedBeforeExtraction() {
        assertFalse(safeSpeechPath("a/../b"));assertFalse(safeSpeechPath("/etc/file"));assertFalse(safeSpeechPath("a\\b"));assertFalse(safeSpeechPath("plugin.so"))
        assertThrows(IllegalArgumentException::class.java){pack().copy(files=pack().files.map{it.copy(bytes=MAX_SPEECH_PACK)}).validate()}
        assertThrows(IllegalArgumentException::class.java){pack().copy(files=pack().files.filterNot{it.path.endsWith("decoder.onnx")}).validate()}
    }
    @Test fun pcmWavRoundTripAndMalformedOrStereoResponsesFailClosed() {
        val pcm=ByteArray(32000);pcm[1]=0x40
        val wav=voiceWav(pcm);val decoded=readVoiceWav(wav);assertEquals(16000,decoded.rate);assertArrayEquals(pcm,decoded.bytes)
        val stereo=wav.copyOf();ByteBuffer.wrap(stereo).order(ByteOrder.LITTLE_ENDIAN).putShort(22,2)
        assertThrows(IllegalArgumentException::class.java){readVoiceWav(stereo)}
        val wrongSize=wav.copyOf();ByteBuffer.wrap(wrongSize).order(ByteOrder.LITTLE_ENDIAN).putInt(40,Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java){readVoiceWav(wrongSize)}
        assertThrows(IllegalArgumentException::class.java){readVoiceWav(wav+byteArrayOf(1))}
        assertThrows(IllegalArgumentException::class.java){PcmAudio(ByteArray(16000*2*181),16000).validate()}
    }
    @Test fun captureEnergyUsesSignedLittleEndianSamplesAndSilenceIsMeasured() {
        assertEquals(0.0,voiceEnergy(ByteArray(100),100),0.0)
        assertEquals(1.0,voiceEnergy(byteArrayOf(0,-128),2),0.0)
        assertEquals(0.5,voiceEnergy(byteArrayOf(0,64),2),0.0)
    }
}
