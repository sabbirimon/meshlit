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
    @Test fun androidSilenceRetriesButPermissionAndNetworkErrorsRemainFatal() {
        assertTrue(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) is NoVoiceDetected)
        assertTrue(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_NO_MATCH) is NoVoiceDetected)
        assertFalse(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) is NoVoiceDetected)
        assertFalse(androidRecognitionFailure(android.speech.SpeechRecognizer.ERROR_NETWORK) is NoVoiceDetected)
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
