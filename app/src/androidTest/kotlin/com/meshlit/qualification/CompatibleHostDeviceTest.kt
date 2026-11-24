package com.meshlit.qualification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.inference.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in physical API check. Uses no saved profile, key or owner model library. */
@RunWith(AndroidJUnit4::class)
class CompatibleHostDeviceTest {
    @Test fun catalogAndGenerationFromExplicitLoopbackHost()=runBlocking {
        val arguments=InstrumentationRegistry.getArguments()
        val base=requireNotNull(arguments.getString("baseUrl"))
        val model=requireNotNull(arguments.getString("modelId"))
        require(base.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}/v1"))) {
            "Qualification requires an explicit local tunnel"
        }
        val profile=OnlineProfile("qa-compatible-host","Temporary host qualification",base,
            model=model,enabled=true,requiresApiKey=false,sendTemperature=true,
            sourceKind=ModelSourceKind.REMOTE_DEVICE)
        val client=OnlineModelClient()
        assertTrue("The actual host must list the selected model",model in client.models(profile,""))
        val reply=client.generate(profile,"",listOf(OnlineMessage("user",
            "What is two plus two? Answer with the number only.")),maxTokens=16,temperature=0f)
        assertEquals("4",reply.text.trim())
        assertTrue("Host input usage must be real",(reply.inputTokens ?: 0)>0)
        assertTrue("Host output usage must be real",(reply.outputTokens ?: 0)>0)
    }
}
