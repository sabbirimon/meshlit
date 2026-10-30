package com.meshlit.core.inference

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VoicePlaybackLeaseTest {
    @Test fun invalidSynthesisInputFailsBeforeOpeningAndroidAudio()=kotlinx.coroutines.runBlocking<Unit> {
        val engine=RunAnywhereVoiceEngine()
        assertFalse(engine.synthesize(""))
        assertFalse(engine.synthesize("a".repeat(4001)))
        assertFalse(engine.synthesize("hello",speakingRate=Float.NaN))
        assertFalse(engine.synthesize("hello",speakingRate=0f))
    }
    @Test fun stoppedPlaybackRetainsOwnershipUntilCleanupAndDoesNotStopReplacement() {
        val lease=VoicePlaybackLease();assertNull(lease.stop())
        val first=checkNotNull(lease.begin());var audioStops=0
        first.attachStop{audioStops++};assertNull(lease.begin());val stop=checkNotNull(lease.stop());assertEquals(1,audioStops)
        assertThrows(CancellationException::class.java){first.requireActive()};assertNull(lease.begin())
        lease.finish(first);assertNull(lease.begin());lease.completeStop(stop);val replacement=checkNotNull(lease.begin());replacement.requireActive()
        lease.finish(first);assertNull(lease.begin());replacement.requireActive()
        lease.finish(replacement);assertNull(lease.stop())
    }
    @Test fun stopBeforeTrackAttachmentIsAppliedWhenAudioBecomesAvailable() {
        val lease=VoicePlaybackLease();val session=checkNotNull(lease.begin());val stop=checkNotNull(lease.stop())
        var audioStopped=false;session.attachStop{audioStopped=true};assertTrue(audioStopped)
        assertThrows(CancellationException::class.java){session.requireActive()}
        lease.finish(session);lease.completeStop(stop)
    }
}
