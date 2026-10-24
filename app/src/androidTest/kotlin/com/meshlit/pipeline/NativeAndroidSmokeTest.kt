package com.meshlit.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.core.inference.pipeline.*
import com.meshlit.models.ModelLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.meshlit.settings.SettingsRepository
import com.meshlit.core.firewall.*
import java.net.NetworkInterface
import java.net.Inet4Address
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Real installed executable/TLS/context/KV checks. One device is not physical cluster proof. */
@RunWith(AndroidJUnit4::class)
class NativeAndroidSmokeTest {
    @Test fun packagedWorkerAndNativeModelExecute()=runBlocking {
        val koin=GlobalContext.get();val host=koin.get<PipelineHost>();val library=koin.get<ModelLibrary>();val inference=koin.get<InferenceCoordinator>()
        withTimeout(240000){
            library.ready.await();check(!host.active()){"Stop the active native pipeline before this explicit test"}
            val model=library.models.value.single{it.bundled && it.installed}
            val settings=koin.get<SettingsRepository>()
            val previous=settings.firewallFlow.first()
            val firewall=koin.get<MeshlitFirewall>()
            val permitted=previous.copy(rules=listOf(PortRule("native-smoke-test",portSpec=PortSpec.Single(host.publicPort)))+previous.rules.filterNot{it.id=="native-smoke-test"})
            val address=NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}
                .filterIsInstance<Inet4Address>().first{it.isSiteLocalAddress}.hostAddress!!
            assertTrue("Packaged native runtime is missing",host.available())
            val createdCheckpoints=mutableListOf<String>()
            try {
                settings.setFirewallPolicy(permitted)
                withTimeout(5000){while(firewall.portLayer!=permitted) delay(10)}
                host.startWorker()
                val offer=withContext(Dispatchers.IO){Json.decodeFromString<NodeOffer>(RpcTunnel.queryCapabilities(address,host.publicPort,host.fingerprint(),host.pairingToken()))}
                assertTrue(offer.workerAllowed);assertEquals(ClusterNegotiation.REVISION,offer.runtimeRevision);assertTrue(offer.freeMemoryBytes>0)
                val bad=runCatching{withContext(Dispatchers.IO){RpcTunnel.queryCapabilities(address,host.publicPort,host.fingerprint(),"0".repeat(64))}}
                assertTrue("Wrong token accepted",bad.isFailure)
                host.stopWorker();assertFalse(host.status.value.worker)
                host.startLocal(model.path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,512,"q8_0"))
                assertEquals(512,inference.loadedModel()?.contextSize)
                assertEquals("llama-native-local",inference.engineTag)
                val text=StringBuilder()
                val result=inference.infer(InferenceRequest("Say hello.",maxTokens=16,temperature=0f,seed=42,onToken={text.append(it)}))
                assertTrue("Native generation failed: $result",result is MeshlitResult.Success);assertTrue(text.isNotBlank())
                assertEquals("Cache-disabled baseline reused prompt tokens",0,(result as MeshlitResult.Success).value.cachedPromptTokens)
                val saved=host.saveCheckpoint().also{createdCheckpoints+=it.id}
                assertTrue(saved.tokens>0);assertTrue(saved.bytes>0)
                host.stopPipeline()
                host.startLocal(model.path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,256,"q8_0"))
                assertTrue("Mismatched context checkpoint accepted",runCatching{host.restoreCheckpoint(saved.id)}.isFailure)
                host.stopPipeline()
                host.startLocal(model.path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,512,"q8_0"))
                val restored=host.restoreCheckpoint(saved.id)
                assertEquals(saved.tokens,restored.tokens)
                val resumed=inference.infer(InferenceRequest("Say hello.",maxTokens=16,temperature=0f,seed=42,reuseContext=true,onToken={}))
                assertTrue("Restored native generation failed: $resumed",resumed is MeshlitResult.Success)
                val value=(resumed as MeshlitResult.Success).value
                assertTrue("Native did not reuse restored tokens",(value.cachedPromptTokens ?: 0)>0)
                assertTrue("Prompt reuse exceeds prompt size",value.cachedPromptTokens!! <= requireNotNull(value.promptTokens))
                assertEquals(text.toString(),value.finalText)
                val context=koin.get<android.content.Context>()
                val blob=java.io.File(context.filesDir,"native-checkpoints/${saved.id}.kv.aes")
                java.io.RandomAccessFile(blob,"rw").use{file->file.seek(file.length()-1);val old=file.readByte();file.seek(file.length()-1);file.writeByte(old.toInt() xor 1)}
                assertTrue("Corrupted ciphertext was accepted",runCatching{host.restoreCheckpoint(saved.id)}.isFailure)
                assertFalse("Unchecked plaintext staging remained",java.io.File(context.filesDir,"native-slot-staging/checkpoint.bin").exists())
                host.deleteCheckpoint(saved.id)
                host.stopPipeline()
                host.startLocal(model.path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,512,"f16"))
                val fullPrecision=inference.infer(InferenceRequest("Say hello.",maxTokens=16,temperature=0f,seed=42,onToken={}))
                assertTrue(fullPrecision is MeshlitResult.Success)
                val fullCache=host.saveCheckpoint().also{createdCheckpoints+=it.id}
                assertEquals("Cache precision comparison needs the same saved token count",saved.tokens,fullCache.tokens)
                assertTrue("Q8 key cache did not reduce real serialized KV bytes",saved.bytes<fullCache.bytes)
                host.deleteCheckpoint(fullCache.id)
                println("MESHLIT_ANDROID_NATIVE workerTlsVerified=true context=512 keyCache=q8_0 generatedText=$text savedTokens=${saved.tokens} restoredTokens=${restored.tokens} reusedTokens=${value.cachedPromptTokens} q8Bytes=${saved.bytes} f16Bytes=${fullCache.bytes} physicalClusterProof=false")
            } finally {withContext(NonCancellable){
                host.stopAll()
                val retained=host.checkpointList().map{it.id}.toSet()
                createdCheckpoints.filter{it in retained}.forEach{host.deleteCheckpoint(it)}
                settings.setFirewallPolicy(previous)
            }}
            assertFalse(host.active());assertNull(inference.loadedModel())
        }
    }
}
