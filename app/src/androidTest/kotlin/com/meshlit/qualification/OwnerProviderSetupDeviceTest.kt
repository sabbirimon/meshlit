package com.meshlit.qualification

import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatOptions
import com.meshlit.core.inference.models.OnlineProfile
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** Explicit owner setup only; no secrets in source, files, test evidence or exports. */
class OwnerProviderSetupDeviceTest {
    @Test fun saveOwnerRequestedFreeProvider() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicit owner setup",args.getString("ownerProviderSetup")=="true")
        val key=args.getString("providerKey").orEmpty()
        require(key.startsWith("sk-or-") && key.length in 40..4096) { "Missing owner-provided key" }
        val model=args.getString("providerModel").orEmpty()
        require(model.endsWith(":free") || model=="openrouter/free") { "Only a free model is allowed in this setup" }
        val providers=GlobalContext.get().get<OnlineProviders>()
        val profile=OnlineProfile(id="owner-openrouter-free",name="OpenRouter · free model",endpoint="https://openrouter.ai/api/v1",
            model=model,enabled=true,agentAllowed=false)
        providers.save(profile,key)
        assertTrue("Credential must be saved in encrypted provider storage",providers.tokenFor(profile).isNotBlank())
        val chat=GlobalContext.get().get<ChatController>()
        chat.ready.await()
        check(!chat.state.value.running && chat.state.value.conversations.size<40) { "Finish the active task or make room for a dedicated provider chat" }
        withContext(Dispatchers.Main) {
            chat.newChat()
            chat.setOptions(ChatOptions(onlineProfileId=profile.id,maxTokens=256))
            assertNotNull("Test chat must start",chat.send("Reply exactly MESHLIT_OK"))
        }
        val state=withTimeout(120_000) { chat.state.first { !it.running && (it.error!=null || it.current?.messages?.any{m->m.role=="assistant" && m.text.isNotBlank()}==true) } }
        assertNull("Provider chat failed",state.error)
        assertEquals(profile.id,state.current?.options?.onlineProfileId)
        assertTrue("Provider returned no visible answer",state.current!!.messages.any{it.role=="assistant" && it.text.isNotBlank()})
    }
}
