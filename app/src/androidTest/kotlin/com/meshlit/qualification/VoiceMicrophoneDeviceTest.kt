package com.meshlit.qualification

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.chat.captureVoiceTurn
import com.meshlit.legal.LegalAgreementStore
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real foreground mic, existing owner permission, two short capture/cancel cycles.
 * PCM is discarded in memory; no transcript, recording, cloud request or chat turn. */
@RunWith(AndroidJUnit4::class)
class VoiceMicrophoneDeviceTest {
    @Test fun foregroundMicrophoneReopensAfterCaptureCancellation()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(LegalAgreementStore(context).accepted()) { "Owner legal acceptance required" }
        assertEquals("Grant microphone permission in Meshlit first",PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))
        val packets=AtomicInteger();val speech=AtomicInteger();var cancelledCycles=0
        val peak=AtomicReference(0.0);val lowest=AtomicReference(1.0)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            repeat(2) {
                val before=packets.get()
                val capture=async(Dispatchers.IO){captureVoiceTurn(onLevel={level,detected->packets.incrementAndGet();peak.updateAndGet{maxOf(it,level)};lowest.updateAndGet{minOf(it,level)};if(detected)speech.incrementAndGet()})}
                try {withTimeout(8000){while(packets.get()==before){capture.ensureActive();delay(50)}};delay(1000)}
                finally {if(capture.isActive)cancelledCycles++;capture.cancelAndJoin()}
                assertTrue("Microphone delivered no PCM frames",packets.get()>before)
            }
            assertTrue("No active capture was cancelled",cancelledCycles>0)
            File(context.filesDir,"qualification/voice-microphone.json").apply{parentFile!!.mkdirs();writeText(buildJsonObject {
                put("status","passed-capture-and-cancel");put("timestampEpochMs",System.currentTimeMillis());put("captureCycles",2)
                put("cancelledCycles",cancelledCycles);put("pcmCallbacks",packets.get());put("callbacksWithSpeechDetected",speech.get());put("microphoneTested",true)
                put("peakRms",peak.get());put("minimumRms",lowest.get());put("speechRecognitionProven",false)
                put("recordingSaved",false);put("transcriptProduced",false);put("liveConversationTested",false);put("providerTested",false)
            }.toString())}
        }finally{scenario.close()}
    }
}
