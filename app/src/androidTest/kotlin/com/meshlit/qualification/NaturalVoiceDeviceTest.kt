package com.meshlit.qualification

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.chat.PcmAudio
import com.meshlit.chat.playVoice
import com.meshlit.core.inference.models.*
import com.meshlit.legal.LegalAgreementStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real local host through an explicit adb reverse tunnel. No hosted provider, mic or credential changes. */
@RunWith(AndroidJUnit4::class)
class NaturalVoiceDeviceTest {
    @Test fun localHostKokoroAndPocketSynthesizeAndPlayOnAndroid()=runBlocking<Unit>{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted()){"Owner legal acceptance required"}
        val profile=OnlineProfile("natural-test","Local speech test","http://127.0.0.1:47834/api/voice",model="speech",enabled=true,requiresApiKey=false)
        val client=NaturalSpeechClient();val reports=mutableListOf<JsonObject>()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            withTimeout(240000){
                val engines=client.engines(profile,"")
                for(id in listOf("kokoro-en-v0_19-int8","pocket-int8")){
                    val engine=engines.single{it.id==id};assertTrue("Install $id on the host first",engine.ready)
                    val voice=engine.voices.first();val started=System.nanoTime()
                    val audio=client.synthesize(profile,"",false,id,voice.id,"Hello. This natural voice is available in Meshlit.")
                    val elapsed=(System.nanoTime()-started)/1e6
                    val pcm=PcmAudio(audio.bytes,audio.rate);pcm.validate();assertTrue(audio.bytes.size>audio.rate)
                    playVoice(pcm)
                    val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                    h.put("RIFF".toByteArray()).putInt(36+audio.bytes.size).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                    h.putInt(audio.rate).putInt(audio.rate*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(audio.bytes.size)
                    File(context.filesDir,"qualification/$id.wav").apply{parentFile!!.mkdirs();outputStream().use{it.write(h.array());it.write(audio.bytes)}}
                    reports+=buildJsonObject{put("engine",id);put("voice",voice.id);put("sampleRate",audio.rate);put("pcmBytes",audio.bytes.size);put("synthesisMs",elapsed);put("androidPlaybackCompleted",true);put("execution","OpenLive host CPU over owner USB tunnel");put("license",engine.license)}
                }
            }
            File(context.filesDir,"qualification/natural-voices.json").writeText(buildJsonObject{put("status","passed");put("models",JsonArray(reports));put("microphoneTested",false);put("cloudProviderTested",false);put("androidNativeSynthesis",false)}.toString())
        }finally{scenario.close()}
    }
}
