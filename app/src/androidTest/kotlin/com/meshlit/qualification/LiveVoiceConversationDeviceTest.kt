package com.meshlit.qualification

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.chat.*
import com.meshlit.legal.LegalAgreementStore
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Owner speaks a real utterance after the Listening phase. No prerecorded/injected audio.
 * Runs one offline turn in the current chat and retains its actual transcript/reply there. */
class LiveVoiceConversationDeviceTest {
    @Test fun realMicrophoneSpeechProducesAChatReplyAndSpokenPlayback()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted()){"Owner legal acceptance required"}
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val koin=GlobalContext.get();val chat=koin.get<ChatController>();val models=koin.get<VoiceModels>()
        val report=File(context.filesDir,"qualification/live-voice-conversation.json").also{it.parentFile!!.mkdirs()}
        var voice:HandsFreeVoice?=null
        var observer:Job?=null
        try {
            withTimeout(180_000){chat.ready.await()}
            val chatOptions=chat.state.value.current?.options ?: ChatOptions()
            check(chatOptions.onlineProfileId==null && chatOptions.routeId==null && chatOptions.colibriMode=="OFF" && !chatOptions.usesLocalTools) {"Select a local chat without tools before this offline qualification"}
            val stt=models.models.value.firstOrNull{it.kind==SpeechKind.WHISPER_STT} ?: error("Install an offline recognition pack first")
            val tts=models.models.value.firstOrNull{it.kind==SpeechKind.PIPER_TTS} ?: error("Install an offline voice pack first")
            val options=VoiceOptions(input=SpeechInput.OFFLINE_MODEL,output=SpeechOutput.OFFLINE_MODEL,sttPack=stt.id,ttsPack=tts.id,mode=VoiceMode.SINGLE_TURN)
            withContext(Dispatchers.Main.immediate) {
                voice=HandsFreeVoice(context,chat,models,koin.get<OnlineProviders>()).also{it.configure(options)}
            }
            val active=checkNotNull(voice)
            observer=launch {
                active.state.map{it.phase}.distinctUntilChanged().collect{phase->
                    println("MESHLIT_LIVE_VOICE_PHASE $phase")
                    report.writeText(buildJsonObject{put("status","running");put("phase",phase);put("liveConversationTested",false)}.toString())
                }
            }
            withContext(Dispatchers.Main.immediate){active.start()}
            val result=withTimeout(300_000){active.state.first{!it.busy}}
            observer.cancelAndJoin();observer=null
            val success=result.error==null && result.completedTurns==1 && !result.lastHeard.isNullOrBlank() && !result.lastReply.isNullOrBlank()
            report.writeText(buildJsonObject {
                put("status",if(success) "passed-one-real-voice-turn" else "failed")
                put("liveConversationTested",true);put("liveConversationPassed",success)
                put("completedTurns",result.completedTurns);put("recognizedCharacters",result.lastHeard?.length ?: 0)
                put("replyCharacters",result.lastReply?.length ?: 0);put("error",result.error)
                put("microphoneAudioSaved",false);put("providerTested",false)
            }.toString())
            assertTrue(result.error ?: "The real microphone turn did not complete",success)
        } finally {
            observer?.cancelAndJoin()
            withContext(NonCancellable+Dispatchers.Main.immediate){voice?.close()}
            scenario.close()
        }
    }
}
