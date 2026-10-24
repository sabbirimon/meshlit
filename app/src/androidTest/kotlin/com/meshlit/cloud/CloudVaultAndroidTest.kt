package com.meshlit.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.cloudmcp.management.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.UUID

/** Actual Android encrypted storage; disposable credentials only. Not authenticated vendor proof. */
@RunWith(AndroidJUnit4::class)
class CloudVaultAndroidTest {
    @Test fun vaultPersistsWithoutPublishingSecretValuesAndRespectsServiceBinding()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=GlobalContext.get().get<CloudManagement>()
        val id=UUID.randomUUID().toString();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val description=EnvironmentDescription(id,"Disposable instrumentation credential",emptyList(),false,null,EnvironmentPurpose.SSH,"approved.example")
        try{
            repository.saveEnvironment(description,"SSH_PASSWORD","test-only-never-deploy")
            assertFalse(repository.state.value.toString().contains("test-only-never-deploy"))
            val restarted=CloudManagement(context,scope);restarted.ready.await()
            assertEquals("test-only-never-deploy",restarted.serviceCredentials(id,EnvironmentPurpose.SSH,"approved.example",CloudActor.HUMAN)["SSH_PASSWORD"])
            assertTrue(runCatching{restarted.serviceCredentials(id,EnvironmentPurpose.SSH,"other.example",CloudActor.HUMAN)}.isFailure)
            assertTrue(runCatching{restarted.serviceCredentials(id,EnvironmentPurpose.SSH,"approved.example",CloudActor.AGENT)}.isFailure)
            assertTrue(restarted.descriptions(CloudActor.AGENT).environments.none{it.id==id})
            println("MESHLIT_VAULT encryptedRestart=true publicSecretValues=false serviceBindingVerified=true agentDefaultDenied=true liveVendorProof=false")
        }finally{withContext(NonCancellable){repository.removeEnvironment(id);scope.cancel()}}
    }
    @Test fun realPublicOpenRouterCatalogUsesNoCredential()=runBlocking {
        val client=CloudApiClient();val environment=EnvironmentProfile("public-catalog","Public catalog")
        val profile=CloudProfile("public-openrouter","Public OpenRouter",CloudVendor.OPENROUTER,environment.id,humanEnabled=true,humanActions=setOf("models"))
        val result=client.execute(profile,environment,CloudActor.HUMAN,"models")
        val data=(result.data as kotlinx.serialization.json.JsonObject)["data"] as kotlinx.serialization.json.JsonArray
        assertTrue("Live public catalog was empty",data.isNotEmpty())
        assertEquals("https://openrouter.ai/api/v1/models",result.source)
        println("MESHLIT_CLOUD_PUBLIC source=${result.source} fetchedAtMs=${result.fetchedAtMs} models=${data.size} authenticatedVendorProof=false")
    }
}
