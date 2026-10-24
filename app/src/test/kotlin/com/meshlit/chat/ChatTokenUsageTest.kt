package com.meshlit.chat

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import com.meshlit.ui.modern.tokenSpeedLabel

class ChatTokenUsageTest {
    @Test fun unknownCountsCannotBecomeTokensPerSecond() {
        val u=chatTokenUsage("SDK",1024,null,null,null,1200,100f,2048)
        assertNull(u.tokensPerSecond);assertNull(u.output);assertEquals(1024,u.requestedOutput)
    }
    @Test fun runtimeAndProviderAverageAreLabelledDifferently() {
        val runtime=chatTokenUsage("runtime",1024,40,10,4,2000,8f,2048,"natural")
        assertEquals(8.0,runtime.tokensPerSecond!!,0.0);assertEquals("Runtime-reported rate",runtime.rateBasis)
        val provider=chatTokenUsage("provider",1024,40,10,null,2000,null,null)
        assertEquals(5.0,provider.tokensPerSecond!!,0.0);assertTrue(provider.rateBasis!!.startsWith("End-to-end"))
    }
    @Test fun invalidRatesCountsDurationsAndCachesStayUnknown() {
        val u=chatTokenUsage("bad",16,-1,-1,10,-1,Float.NaN,-1)
        assertNull(u.tokensPerSecond);assertNull(u.input);assertNull(u.output);assertNull(u.cached);assertNull(u.contextCapacity);assertEquals(0,u.elapsedMs)
        assertNull(chatTokenUsage("zero time",16,4,10,5,0,Float.POSITIVE_INFINITY,1).tokensPerSecond)
        assertNull(chatTokenUsage("zero time",16,4,10,5,0,null,1).cached)
        assertEquals("0 output tokens · speed unavailable",tokenSpeedLabel(chatTokenUsage("empty",16,4,0,0,100,null,128)))
        val large=chatTokenUsage("provider",16,4_000_000_000L,2,null,1000,null,null)
        assertEquals(4_000_000_000L,large.input)
        assertNull(chatTokenUsage("SDK unknown context",16,null,null,null,100,null,0).contextCapacity)
    }
    @Test fun oldConversationJsonLoadsWithDefaultIndicatorAndNoUsage() {
        val message=Json.decodeFromString<ChatMessage>("""{"role":"assistant","text":"Existing answer"}""")
        assertNull(message.usage)
        assertTrue(Json.decodeFromString<ChatOptions>("{}").showTokenStats)
        val usage=chatTokenUsage("test",16,4,2,0,1000,null,128)
        val full=message.copy(usage=usage)
        assertEquals(full,Json.decodeFromString<ChatMessage>(Json.encodeToString(ChatMessage.serializer(),full)))
    }
}
