package com.meshlit.chat

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VoicePreferencesTest {
    @Test fun additiveFieldsSurviveKnownPreferenceEditsIncludingNestedTuning() {
        val original="""{"network":false,"futureSetting":{"mode":"kept"},"tuning":{"threshold":0.02,"futureFilter":true}}"""
        val read=VoicePreferences.decode(original,VoiceOptions())
        assertNull(read.problem);assertEquals(0.02,read.options.tuning.threshold,0.0)
        val encoded=VoicePreferences.encodePreservingUnknown(original,read.options.copy(captions=false))
        val data=Json.parseToJsonElement(encoded).jsonObject
        assertEquals("kept",data.getValue("futureSetting").jsonObject.getValue("mode").jsonPrimitive.content)
        assertTrue(data.getValue("tuning").jsonObject.getValue("futureFilter").jsonPrimitive.boolean)
        assertFalse(VoicePreferences.decode(encoded,VoiceOptions()).options.captions)
    }
    @Test fun unreadableAndUnknownEnumSettingsAreNotReplacedOnOpenOrSave() {
        for(raw in listOf("not-json","""{"input":"FUTURE_ENGINE"}""","""{"tuning":{"threshold":-2}}""")) {
            val read=VoicePreferences.decode(raw,VoiceOptions())
            assertNotNull(read.problem)
            assertThrows(IllegalStateException::class.java){VoicePreferences.encodePreservingUnknown(raw,VoiceOptions())}
        }
        assertNull(VoicePreferences.decode(null,VoiceOptions()).problem)
    }
}
