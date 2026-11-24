package com.meshlit.qualification

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.control.WebBridgeHost
import com.meshlit.core.firewall.*
import com.meshlit.core.mcp.control.*
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.models.ModelLibrary
import com.meshlit.pipeline.PipelineHost
import com.meshlit.resources.ResourceSnapshot
import com.meshlit.resources.ResourceSnapshotReader
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.net.Inet6Address
import java.net.NetworkInterface

/** Real app services and native worker, with an isolated test enrollment directory.
 * The Mac supplies its own fresh test identity; no existing approval is changed. */
@RunWith(AndroidJUnit4::class)
class DistributedWanDeviceTest {
    @Test fun holdAuthenticatedServicesForPhysicalPeer()=runBlocking {
        val k=GlobalContext.get();val context=InstrumentationRegistry.getInstrumentation().targetContext
        val clientId=InstrumentationRegistry.getArguments().getString("clientId")!!
        require(clientId.matches(Regex("qa_[a-f0-9]{32}")))
        val pipeline=k.get<PipelineHost>();val library=k.get<ModelLibrary>();val inference=k.get<InferenceCoordinator>()
        library.ready.await();check(!pipeline.active() && !k.get<WebBridgeHost>().active.value){"Stop existing services before this explicit qualification"}
        check(inference.loadedModel()==null){"Pause startup loading for the isolated worker test"}
        val root=File(context.filesDir,"qualification/distributed-live").apply{mkdirs()}
        for(name in listOf("ready.json","approve","done"))File(root,name).delete()
        val address=NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}
            .filterIsInstance<Inet6Address>().first{!it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isSiteLocalAddress}.hostAddress!!.substringBefore('%')
        var testDirectory=DeviceDirectorySnapshot()
        val bridge=WebBridgeHost(context,k.get(),pipeline,k.get(),{Json.encodeToJsonElement(ResourceSnapshot.serializer(),k.get<ResourceSnapshotReader>().read())},object:DeviceDirectoryStore{
            override fun load()=testDirectory
            override fun save(snapshot:DeviceDirectorySnapshot){testDirectory=snapshot}
        })
        val settings=k.get<SettingsRepository>();val prior=settings.firewallFlow.first();val priorUrl=bridge.publicEndpoint()
        val publicUrl="https://[$address]:${bridge.port}"
        val rules=listOf(PortRule("qa-wan-control",portSpec=PortSpec.Single(bridge.port)),PortRule("qa-native-worker",portSpec=PortSpec.Single(pipeline.publicPort)))
        val permitted=prior.copy(rules=rules+prior.rules.filterNot{r->rules.any{it.id==r.id}}).also{it.validate()}
        val activity=ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        try {
            settings.setFirewallPolicy(permitted)
            withTimeout(5000){while(k.get<MeshlitFirewall>().portLayer!=permitted)delay(20)}
            bridge.setPublicEndpoint(publicUrl);bridge.start();pipeline.startWorker()
            val ready=buildJsonObject{
                put("url",publicUrl);put("pin",bridge.fingerprint());put("invitation",bridge.newInvitation())
                put("rpcPort",pipeline.publicPort);put("rpcPin",pipeline.fingerprint());put("rpcToken",pipeline.pairingToken())
                put("clientId",clientId);put("transport","mobile-ipv6")
            }
            File(root,"ready.json").apply{writeText(ready.toString());android.system.Os.chmod(absolutePath,384)}
            withTimeout(600_000){
                var approved=false
                while(!File(root,"done").exists()){
                    if(!approved && File(root,"approve").exists()){
                        val entry=bridge.directory.read().devices.single{it.id==clientId}
                        assertEquals(EnrollmentState.PENDING,entry.state)
                        bridge.directory.approve(clientId,setOf(DeviceAccess.OBSERVE));approved=true
                    }
                    delay(100)
                }
            }
        }finally{withContext(NonCancellable){
            bridge.stop();pipeline.stopAll()
            if(bridge.publicEndpoint()==publicUrl)bridge.setPublicEndpoint(priorUrl)
            if(settings.firewallFlow.first()==permitted)settings.setFirewallPolicy(prior)
            File(root,"ready.json").delete();File(root,"approve").delete();File(root,"done").delete()
            activity.close()
        }}
    }
}
