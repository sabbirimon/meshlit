package com.meshlit.operations
import android.content.Context
import com.meshlit.core.common.control.ManagedFeature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.context.GlobalContext
/** Stop admission first, then tear down local sessions. Transport cancellation is not an
 * acknowledgment that a remote process or paid provider task was killed. */
class StopCoordinator(private val context:Context) {
    private val control=OperationsControl.get(context)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow<Map<String,String>>(emptyMap());val state=mutable.asStateFlow()
    private val pending=java.util.concurrent.atomic.AtomicInteger(0)
    private val busy=MutableStateFlow(false);val stopping=busy.asStateFlow()
    @Synchronized fun resumeHuman(){check(pending.get()==0){"Local stop cleanup is still running"};control.gate.resumeHuman()}
    @Synchronized fun stopAll() { try {control.gate.emergencyStop()} finally {cleanup(null)} }
    @Synchronized fun setFeature(feature:ManagedFeature,enabled:Boolean,agentOnly:Boolean=false) {
        if(enabled) check(pending.get()==0){"Wait for local stop cleanup before enabling operations"}
        try {control.gate.setFeature(feature,enabled,agentOnly)} finally {if(!enabled && !agentOnly) cleanup(feature)}
    }
    private fun cleanup(feature:ManagedFeature?) {
        pending.incrementAndGet();busy.value=true
        scope.launch {try {
        val koin=GlobalContext.get()
        val actions=linkedMapOf<String,suspend()->Unit>()
        if(feature==null || feature==ManagedFeature.INFERENCE || feature==ManagedFeature.CLUSTER) {
            actions["native inference / worker"]={koin.get<com.meshlit.core.inference.InferenceCoordinator>().cancel();if(!com.meshlit.BuildProfile.coreCandidate) koin.get<com.meshlit.pipeline.PipelineHost>().stopAll()}
        }
        if(feature==null || feature==ManagedFeature.MODEL_TRANSFERS) actions["model transfers"]={koin.get<com.meshlit.models.ModelLibrary>().pauseAll()}
        if(!com.meshlit.BuildProfile.coreCandidate) {
        if(feature==null || feature==ManagedFeature.GATEWAY) actions["gateway"]={
            koin.get<com.meshlit.gateway.GatewayHost>().stop()
            context.stopService(android.content.Intent(context,com.meshlit.core.cloudmcp.CloudMcpForegroundService::class.java))
        }
        if(feature==null || feature==ManagedFeature.AUTOMATION) {
            actions["remote control"]={
                koin.get<com.meshlit.openclaw.AndroidControl>().setEnabled(false)
                koin.get<com.meshlit.settings.SettingsRepository>().setAndroidAutomationEnabled(false)
                koin.get<com.meshlit.openclaw.OpenClawNode>().disconnect()
                koin.get<com.meshlit.control.WebBridgeHost>().stop()
                koin.get<com.meshlit.openclaw.OpenClawHost>().stopSharing()
            }
            actions["command jobs"]={val backend=koin.get<com.meshlit.control.AgentBackend>();for(controller in listOf(backend.controller,backend.humanController)){controller.ready.await();controller.jobs.value.filter{!it.terminal}.forEach{controller.cancel(it.command.requestId)}}}
        }
        if(feature==null || feature==ManagedFeature.BROWSER) actions["browser autonomy"]={koin.get<com.meshlit.browser.BrowserSessionBroker>().stop()}
        if(feature==null || feature in setOf(ManagedFeature.VM,ManagedFeature.CYBER)) actions["VM / lab"]={koin.get<com.meshlit.sandbox.RuntimeHost>().stopVm()}
        if((feature==null || feature==ManagedFeature.SSH) && android.os.Build.VERSION.SDK_INT>=26) actions["SSH node"]={koin.get<com.meshlit.ssh.NodeSshHost>().stop()}
        if(feature==null || feature==ManagedFeature.RECOVERY) actions["replica listener"]={koin.get<com.meshlit.recovery.ReplicaHost>().stop()}
        }
        mutable.value=actions.mapValues{"stop requested"}
        supervisorScope { actions.map{(name,action)->launch {val status=try{withTimeout(15000){action()};"local stop completed"}catch(_:TimeoutCancellationException){"timeout; inspect local state"}catch(e:CancellationException){throw e}catch(_:Exception){"stop failed; inspect local state"};mutable.update{it+(name to status)}}}.joinAll() }
    } finally {synchronized(this@StopCoordinator){if(pending.decrementAndGet()==0)busy.value=false}} }
    }
}
