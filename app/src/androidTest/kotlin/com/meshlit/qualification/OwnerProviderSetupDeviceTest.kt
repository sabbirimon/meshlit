package com.meshlit.qualification

import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.inference.models.OnlineMessage
import com.meshlit.core.inference.models.OnlineProfile
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.runBlocking
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
        assertTrue("Credential must be saved in encrypted provider storage",providers.hasStoredKey(profile))
        val reply=providers.generate(profile.id,listOf(OnlineMessage("user","Reply exactly MESHLIT_OK")),maxTokens=256).second
        assertTrue("Provider returned no visible answer",reply.text.isNotBlank())
    }
}
