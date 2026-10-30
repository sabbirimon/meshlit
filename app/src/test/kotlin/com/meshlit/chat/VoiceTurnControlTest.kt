package com.meshlit.chat

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTurnControlTest {
    @Test fun shortRepliesEndOnSilenceAndContinuousSpeechKeepsItsBoundedAudio() {
        val short=VoiceEndpointDetector()
        short.accept(3200,0.02);assertTrue(short.hasSpeech);assertFalse(short.complete)
        short.accept(22400,0.0);assertTrue(short.complete)
        val continuous=VoiceEndpointDetector()
        continuous.accept(960000,0.02);assertTrue(continuous.complete);assertTrue(continuous.hasSpeech)
        assertEquals(0,continuous.remaining)
        val silence=VoiceEndpointDetector()
        silence.accept(960000,0.0);assertTrue(silence.complete);assertFalse(silence.hasSpeech)
    }
    @Test fun tunedThresholdAndSilenceChangeRealEndpointDetection() {
        val detector=VoiceEndpointDetector(VoiceTuning(threshold=0.03,silenceMs=1200))
        detector.accept(3200,0.02);assertFalse(detector.hasSpeech)
        detector.accept(3200,0.04);assertTrue(detector.hasSpeech)
        detector.accept(22400,0.0);assertFalse(detector.complete)
        detector.accept(16000,0.0);assertTrue(detector.complete)
        val bounded=VoiceEndpointDetector(VoiceTuning(silenceMs=2000))
        bounded.accept(960000,0.04);assertTrue(bounded.complete)
    }
    @Test fun tuningRejectsUnsafeImportedValuesAndKeepsLegacyDefaults() {
        assertThrows(IllegalArgumentException::class.java){VoiceTuning(threshold=Double.NaN)}
        assertThrows(IllegalArgumentException::class.java){VoiceTuning(silenceMs=Int.MAX_VALUE)}
        assertThrows(IllegalArgumentException::class.java){VoiceTuning(speechRate=Float.POSITIVE_INFINITY)}
        assertThrows(IllegalArgumentException::class.java){VoiceTuning(pitch=0f)}
        assertEquals(VoiceTuning(),kotlinx.serialization.json.Json.decodeFromString<VoiceTuning>("{}"))
        val tuning=VoiceTuning(threshold=0.02,silenceMs=1500,speechRate=1.2f,pitch=0.9f)
        val encoded=kotlinx.serialization.json.Json.encodeToString(VoiceTuning.serializer(),tuning)
        assertEquals(tuning,kotlinx.serialization.json.Json.decodeFromString<VoiceTuning>(encoded))
        assertFalse(VoiceTuning().automaticThreshold)
    }
    @Test fun optionalQuietInputCalibrationRejectsSteadyNoiseAndRecognizesQuietSpeech() {
        val quiet=VoiceEndpointDetector(VoiceTuning(automaticThreshold=true))
        quiet.accept(16000,0.0002)
        assertEquals(0.001,quiet.threshold,0.00001)
        assertFalse(quiet.hasSpeech)
        quiet.accept(3200,0.004)
        assertTrue(quiet.hasSpeech)
        val noise=VoiceEndpointDetector(VoiceTuning(automaticThreshold=true))
        repeat(12){noise.accept(32000,0.004)}
        assertFalse(noise.hasSpeech)
        assertTrue(noise.complete)
        assertEquals(0.008,noise.threshold,0.00001)
        val fixed=VoiceEndpointDetector()
        fixed.accept(16000,0.0002);fixed.accept(3200,0.004)
        assertFalse(fixed.hasSpeech)
    }
    @Test fun manualBoundaryRequiresActualNonSilentSamplesAndSilenceIsBounded() {
        val silence=VoiceEndpointDetector()
        silence.accept(384000,0.0)
        assertTrue(silence.complete);assertFalse(silence.hasSpeech);assertFalse(silence.canFinishManually)
        val quiet=VoiceEndpointDetector()
        quiet.accept(16000,0.002)
        assertFalse(quiet.hasSpeech);assertTrue(quiet.canFinishManually)
        assertTrue(voiceNoSpeechMessage(silence.peakLevel).contains("silence"))
        assertTrue(voiceNoSpeechMessage(quiet.peakLevel).contains("threshold"))
        assertEquals(0f,voiceMeterLevel(0.0))
        assertTrue(voiceMeterLevel(0.002)>0.2f)
        assertTrue(voiceMeterLevel(0.02)>voiceMeterLevel(0.002))
        assertEquals(1f,voiceMeterLevel(1.0))
    }
    @Test fun androidSilenceRetriesButPermissionAndNetworkErrorsRemainFatal() {
        assertTrue(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) is NoVoiceDetected)
        assertTrue(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_NO_MATCH) is NoVoiceDetected)
        assertFalse(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) is NoVoiceDetected)
        assertFalse(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_NETWORK) is NoVoiceDetected)
    }
    @Test fun failedNativeCleanupBlocksFurtherSessionsEvenWhenTurnWasCancelled()=runTest {
        val guard=VoiceCleanupGuard();guard.requireReady()
        guard.cleanup{yield()};guard.requireReady()
        assertThrows(IllegalStateException::class.java){runBlocking{guard.cleanup{throw IllegalStateException("unload failed")}}}
        assertThrows(IllegalStateException::class.java){guard.requireReady()}
        val stopped=VoiceCleanupGuard()
        val session=launch{try{awaitCancellation()}finally{withContext(NonCancellable){stopped.cleanup{withContext(kotlinx.coroutines.Dispatchers.Default){}}}}}
        runCurrent();session.cancelAndJoin();stopped.requireReady()
        val cancelled=VoiceCleanupGuard()
        try{cancelled.cleanup{throw CancellationException("cleanup interrupted")};fail()}catch(_:CancellationException){}
        assertThrows(IllegalStateException::class.java){cancelled.requireReady()}
    }
    @Test fun speechAdmissionRejectsPressureAndInvalidSizes() {
        requireSpeechMemory(100L*1024*1024,600L*1024*1024,false)
        assertThrows(IllegalStateException::class.java){requireSpeechMemory(100L*1024*1024,300L*1024*1024,false)}
        assertThrows(IllegalStateException::class.java){requireSpeechMemory(1,1024L*1024*1024,true)}
        assertThrows(IllegalArgumentException::class.java){requireSpeechMemory(Long.MAX_VALUE,Long.MAX_VALUE,false)}
    }
    @Test fun interruptionWaitsForCleanupAndAllowsNextTurn()=runTest {
        val control=VoiceTurnControl();var released=false;var interrupted=false
        val job=launch {
            try {control.run {try {awaitCancellation()} finally {withContext(NonCancellable){delay(100);released=true}}}}
            catch(_:VoiceTurnInterrupted){interrupted=true}
            assertTrue(released)
            assertEquals("next",control.run{"next"})
        }
        runCurrent();control.interrupt();advanceUntilIdle();job.join()
        assertTrue(interrupted);assertTrue(released)
    }
    @Test fun muteStopsCaptureAndResumeRequiresExplicitUnmute()=runTest {
        val control=VoiceTurnControl();var resumed=false
        val job=launch {
            try{control.run{awaitCancellation()}}catch(_:VoiceTurnInterrupted){}
            control.awaitUnmuted();resumed=true
        }
        runCurrent();control.setMuted(true,interruptCapture=true);runCurrent()
        assertFalse(resumed);control.setMuted(false);advanceUntilIdle();job.join();assertTrue(resumed)
    }
    @Test fun parentCancellationIsNotConvertedToRestart()=runTest {
        val control=VoiceTurnControl();var released=false
        val job=launch{control.run{try{awaitCancellation()}finally{released=true}}}
        runCurrent();job.cancelAndJoin();assertTrue(released);assertTrue(job.isCancelled)
    }
    @Test fun captionsBoundPhrasesAndPlaybackDoesNotReadMarkdownFences() {
        val source="Hello there. A second sentence! "+"word ".repeat(180)
        val pieces=speechSegments(source)
        assertTrue(pieces.all{it.length in 1..240})
        assertEquals(source.trim().replace(Regex("\\s+")," "),pieces.joinToString(" "))
        val spoken=spokenReply("# Hello\n**Friend** [guide](https://example.com)\n```sh\nsecret command\n```")
        assertFalse(spoken.contains("secret command"));assertFalse(spoken.contains("https://"))
        assertTrue(spoken.contains("Code is available in the chat."))
    }
}
