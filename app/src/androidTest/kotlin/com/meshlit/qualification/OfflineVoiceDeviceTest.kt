package com.meshlit.qualification

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.chat.*
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.common.MeshlitResult
import com.meshlit.models.ModelLibrary
import com.meshlit.legal.LegalAgreementStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/** Real checked weights and prerecorded upstream speech, under the app UID.
 * No fake recognizer/reply, Android network service, microphone capture or cloud key. */
@RunWith(AndroidJUnit4::class)
class OfflineVoiceDeviceTest {
    @Test fun importedWhisperTranscribesAndPiperProducesActualPcmWithoutReplacingChatModel()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted()) { "Owner must accept the legal agreement" }
        val koin=GlobalContext.get();val models=koin.get<VoiceModels>();val library=koin.get<ModelLibrary>();val inference=koin.get<InferenceCoordinator>()
        withTimeout(180_000){library.ready.await();library.startupStatus.first{it!="Waiting for model library" && !it.startsWith("Loading ")}}
        val chatBefore=checkNotNull(inference.loadedModel()?.modelPath) { "Load the owner's local chat model before speech qualification" }
        val source=File(context.filesDir,"generated-media/voice-qualification")
        val times=linkedMapOf<String,Double>()
        val adapter=OfflineSpeechAdapter(models)
        var report:JsonObject?=null
        var transcript:String?=null
        var pcmBytes:Int?=null
        var pcmRate:Int?=null
        var completed=false
        try {
        for(name in listOf("whisper-tiny-en","piper-lessac-medium")) {
            if(models.models.value.none{it.id==name}) {
                val start=System.nanoTime();val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",File(source,"$name.voice.zip"))
                withTimeout(180_000){models.install(uri)};times["$name-importMs"]=(System.nanoTime()-start)/1e6
            }
        }
            withTimeout(240_000) {
                var start=System.nanoTime();adapter.load("whisper-tiny-en",SpeechKind.WHISPER_STT);times["recognitionLoadMs"]=(System.nanoTime()-start)/1e6
                val wav=readVoiceWav(File(source,"reference.wav").readBytes());assertEquals(16000,wav.rate)
                start=System.nanoTime();val text=adapter.transcribe(wav.bytes);times["transcriptionMs"]=(System.nanoTime()-start)/1e6
                assertTrue("Real reference speech was not recognized",text.lowercase().contains("early nightfall"))
                transcript=text
                start=System.nanoTime();adapter.load("piper-lessac-medium",SpeechKind.PIPER_TTS);times["synthesisLoadMs"]=(System.nanoTime()-start)/1e6
                start=System.nanoTime();val audio=adapter.synthesize("Hello. Meshlit can use an offline voice adapter.",VoiceStyle.NATURAL);times["synthesisMs"]=(System.nanoTime()-start)/1e6
                assertEquals(22050,audio.rate);assertTrue(audio.bytes.size>22050);assertTrue(voiceEnergy(audio.bytes,audio.bytes.size)>0.001)
                pcmBytes=audio.bytes.size;pcmRate=audio.rate
                assertEquals("Speech model must not replace the owner's chat model",chatBefore,inference.loadedModel()?.modelPath)
                start=System.nanoTime()
                val name=library.models.value.firstOrNull{it.path==chatBefore}?.name.orEmpty()
                val prompt=if(usesChatMlPrompt(inference.engineTag,name)) {
                    if(name.contains("Qwen3",true)) qwen3ChatPrompt("You are Meshlit.",emptyList(),"Say hello in one short sentence.")
                    else smolLmChatPrompt("You are Meshlit.",emptyList(),"Say hello in one short sentence.")
                } else "Say hello in one short sentence."
                val answer=inference.infer(InferenceRequest(prompt,maxTokens=32,temperature=0f,seed=42,
                    expectedModelPath=chatBefore,onDeviceOnly=true,publishEvents=false,onToken={}))
                times["localLlmWithSpeechLoadedMs"]=(System.nanoTime()-start)/1e6
                assertTrue("Local LLM failed with both speech models loaded",answer is MeshlitResult.Success)
                assertTrue("No real local reply",(answer as MeshlitResult.Success).value.finalText.isNotBlank())
                completed=true
                report=buildJsonObject{put("format","meshlit-offline-voice/1");put("timestampEpochMs",System.currentTimeMillis());put("status","passed-listed-cases")
                    put("recognition","Whisper Tiny English INT8 / ONNX");put("synthesis","Piper Lessac medium / ONNX");put("transcript",text)
                    put("pcmBytes",audio.bytes.size);put("pcmSampleRate",audio.rate);put("microphoneTested",false);put("liveConversationTested",false);put("providerTested",false)
                    put("chatModelPreserved",true);put("localLlmExecutedWithSpeechLoaded",true)
                    put("timingsMs",buildJsonObject{times.forEach{(name,value)->put(name,value)}})}
            }
        }finally{try{adapter.close()}finally{
            source.deleteRecursively()
            val evidence=report ?: buildJsonObject {
                put("format","meshlit-offline-voice/1");put("timestampEpochMs",System.currentTimeMillis());put("status","incomplete-or-failed")
                put("recognitionPassed",transcript!=null);transcript?.let{put("transcript",it)}
                put("synthesisPassed",pcmBytes!=null);pcmBytes?.let{put("pcmBytes",it)};pcmRate?.let{put("pcmSampleRate",it)}
                put("localLlmExecutedWithSpeechLoaded",completed);put("microphoneTested",false);put("liveConversationTested",false);put("providerTested",false)
                put("timingsMs",buildJsonObject{times.forEach{(name,value)->put(name,value)}})
            }
            File(context.filesDir,"qualification/offline-voice.json").apply{parentFile!!.mkdirs();writeText(evidence.toString())}
        }}
        assertEquals("Speech cleanup must preserve the chat model",chatBefore,inference.loadedModel()?.modelPath)
        val file=File(context.filesDir,"qualification/offline-voice.json");file.parentFile!!.mkdirs();file.writeText(checkNotNull(report).toString())
    }
}
