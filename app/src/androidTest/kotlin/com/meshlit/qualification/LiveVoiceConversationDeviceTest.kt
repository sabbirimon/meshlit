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
    @Test fun offlinePreparationReachesListeningAndStopReleasesPendingOutput()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted())
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val koin=GlobalContext.get();val chat=koin.get<ChatController>();val models=koin.get<VoiceModels>()
        withTimeout(180_000){chat.ready.await()}
        val originalOptions=chat.state.value.current?.options ?: ChatOptions()
        var voice:HandsFreeVoice?=null
        val samples=mutableListOf<JsonObject>()
        try {
            withContext(Dispatchers.Main.immediate) {
                chat.setOptions(originalOptions.copy(onlineProfileId=null,routeId=null,colibriMode="OFF",webTools=false,
                    phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,
                    gibberlinkTools=false,remoteTools=false,peerTools=false))
                voice=HandsFreeVoice(context,chat,models,koin.get<OnlineProviders>()).also {
                    it.configure(VoiceOptions(input=SpeechInput.OFFLINE_MODEL,output=SpeechOutput.OFFLINE_MODEL,
                        sttPack=models.models.value.first{pack->pack.kind==SpeechKind.WHISPER_STT}.id,
                        ttsPack=models.models.value.first{pack->pack.kind==SpeechKind.PIPER_TTS}.id,mode=VoiceMode.SINGLE_TURN))
                }
            }
            val active=checkNotNull(voice)
            repeat(3) { trial ->
                val start=android.os.SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main.immediate){active.start()}
                val listening=withTimeout(180_000){active.state.first{it.phase=="Listening" || !it.busy}}
                assertEquals(listening.error ?: "Listening was not reached","Listening",listening.phase)
                val ready=android.os.SystemClock.elapsedRealtime()
                withContext(Dispatchers.Main.immediate){active.stop()}
                val stopped=withTimeout(180_000){active.state.first{!it.busy}}
                assertNull(stopped.error);assertEquals(0,stopped.completedTurns)
                samples+=buildJsonObject {put("trial",trial+1);put("startToListeningMs",ready-start)
                    put("stopToIdleMs",android.os.SystemClock.elapsedRealtime()-ready)}
            }
            File(context.filesDir,"qualification/voice-startup-benchmark.json").apply {parentFile!!.mkdirs();writeText(buildJsonObject {
                put("status","passed");put("samples",JsonArray(samples));put("microphoneAudioSaved",false)
                put("humanConversationTested",false);put("completedTurns",0);put("scope","Cold speech-adapter preparation and immediate Stop, not answer latency")
            }.toString())}
        } finally {
            withContext(NonCancellable+Dispatchers.Main.immediate){voice?.close();chat.setOptions(originalOptions)}
            scenario.close()
        }
    }

    @Test fun realMicrophoneSpeechProducesAChatReplyAndSpokenPlayback()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted()){"Owner legal acceptance required"}
        assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val koin=GlobalContext.get();val chat=koin.get<ChatController>();val models=koin.get<VoiceModels>()
        val report=File(context.filesDir,"qualification/live-voice-conversation.json").also{it.parentFile!!.mkdirs()}
        var originalOptions:ChatOptions?=null
        var voice:HandsFreeVoice?=null
        var cueDialog:android.app.AlertDialog?=null
        var observer:Job?=null
        val phaseTimes=linkedMapOf<String,Long>()
        var previousPhase="Stopped"
        var phaseStarted=android.os.SystemClock.elapsedRealtime()
        fun recordPhase(phase:String) {
            val now=android.os.SystemClock.elapsedRealtime()
            phaseTimes[previousPhase]=(phaseTimes[previousPhase] ?: 0L)+(now-phaseStarted)
            previousPhase=phase;phaseStarted=now
        }
        try {
            withTimeout(180_000){chat.ready.await()}
            val chatOptions=chat.state.value.current?.options ?: ChatOptions()
            originalOptions=chatOptions
            withContext(Dispatchers.Main.immediate) {
                chat.setOptions(chatOptions.copy(onlineProfileId=null,routeId=null,colibriMode="OFF",webTools=false,
                    phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,
                    gibberlinkTools=false,remoteTools=false,peerTools=false))
            }
            val stt=models.models.value.firstOrNull{it.kind==SpeechKind.WHISPER_STT} ?: error("Install an offline recognition pack first")
            val tts=models.models.value.firstOrNull{it.kind==SpeechKind.PIPER_TTS} ?: error("Install an offline voice pack first")
            val options=VoiceOptions(input=SpeechInput.OFFLINE_MODEL,output=SpeechOutput.OFFLINE_MODEL,sttPack=stt.id,ttsPack=tts.id,mode=VoiceMode.SINGLE_TURN)
            withContext(Dispatchers.Main.immediate) {
                voice=HandsFreeVoice(context,chat,models,koin.get<OnlineProviders>()).also{it.configure(options)}
            }
            val active=checkNotNull(voice)
            scenario.onActivity { activity ->
                cueDialog=android.app.AlertDialog.Builder(activity)
                    .setTitle("Meshlit voice performance test")
                    .setMessage("Preparing speech adapters. Please wait for Listening.")
                    .setNegativeButton("Stop") { _,_ -> active.stop() }
                    .setCancelable(false).show()
            }
            observer=launch {
                active.state.map{it.phase}.distinctUntilChanged().collect{phase->
                    recordPhase(phase)
                    withContext(Dispatchers.Main.immediate) {
                        cueDialog?.setMessage(if(phase=="Listening") "Listening — say: What is two plus two?" else phase)
                    }
                    println("MESHLIT_LIVE_VOICE_PHASE $phase")
                    report.writeText(buildJsonObject{put("status","running");put("phase",phase);put("liveConversationTested",false)}.toString())
                }
            }
            withContext(Dispatchers.Main.immediate){active.start()}
            val result=withTimeout(300_000){active.state.first{!it.busy}}
            observer.cancelAndJoin();observer=null
            recordPhase("Stopped")
            val success=result.error==null && result.completedTurns==1 && !result.lastHeard.isNullOrBlank() && !result.lastReply.isNullOrBlank()
            report.writeText(buildJsonObject {
                put("status",if(success) "passed-one-real-voice-turn" else "failed")
                put("liveConversationTested",true);put("liveConversationPassed",success)
                put("completedTurns",result.completedTurns);put("recognizedCharacters",result.lastHeard?.length ?: 0)
                put("replyCharacters",result.lastReply?.length ?: 0);put("error",result.error)
                put("microphoneAudioSaved",false);put("providerTested",false)
                put("phaseDurationsMs",buildJsonObject{phaseTimes.forEach{(phase,duration)->put(phase,duration)}})
            }.toString())
            assertTrue(result.error ?: "The real microphone turn did not complete",success)
        } finally {
            observer?.cancelAndJoin()
            withContext(NonCancellable+Dispatchers.Main.immediate){cueDialog?.dismiss();voice?.close();originalOptions?.let{chat.setOptions(it)}}
            scenario.close()
        }
    }
}
