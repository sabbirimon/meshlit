package com.meshlit.chat

import org.junit.Assert.*
import org.junit.Test

class HeadsetSpeechGateTest {
    @Test fun suddenNoiseAndSpeechOutsidePlaybackDoNotInterrupt() {
        val gate = HeadsetSpeechGate(0.008)
        repeat(10) { assertFalse(gate.accept(320, 0.002, watching = true)) }
        assertFalse(gate.accept(320, 0.7, watching = true))
        repeat(30) { assertFalse(gate.accept(320, 0.1, watching = false)) }
        repeat(9) { assertFalse(gate.accept(320, 0.03, watching = true)) }
        assertTrue(gate.accept(320, 0.03, watching = true))
    }

    @Test fun steadyBackgroundNoiseDoesNotCountAsSpeech() {
        val gate = HeadsetSpeechGate(0.008)
        repeat(40) { assertFalse(gate.accept(320, 0.015, watching = true)) }
    }
}
