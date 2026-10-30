package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.control.AgentBackend
import com.meshlit.di.koinInject
import com.meshlit.openclaw.AndroidControl
import com.meshlit.operations.OperationsControl
import com.meshlit.search.AppSearchService
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*

/** Main-settings controls only. Chat controls must never call these grant setters. */
@Composable internal fun AgentAccessPanel(onOpen:(String)->Unit,onChanged:()->Unit={}) {
    val operations=koinInject<OperationsControl>()
    val backend=koinInject<AgentBackend>()
    val search=koinInject<AppSearchService>()
    val phone=koinInject<AndroidControl>()
    val settings=koinInject<SettingsRepository>()
    val policy by operations.gate.policy.collectAsStateWithLifecycle()
    val searchAccess by search.access.state.collectAsStateWithLifecycle()
    val automation by settings.androidAutomationEnabledFlow.collectAsStateWithLifecycle(initialValue=false)
    val scope=rememberCoroutineScope()
    var revision by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var hasKey by remember{mutableStateOf<Boolean?>(null)}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME) revision++}
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(search,revision) {
        try{search.ready.await();hasKey=withContext(Dispatchers.IO){search.hasWebKey()}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){hasKey=null;error="Search settings could not be read. Open search setup."}
    }
    fun change(work:suspend()->Unit) {scope.launch {
        busy=true;error=null
        try{withContext(Dispatchers.IO){work()}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message ?: "Access could not be saved"}
        finally{busy=false;revision++;onChanged()}
    }}
    SettingsFeatureSection("Agent access · whole app") {
        Text("These controls apply to this Meshlit installation. Each chat still chooses its own tools; role presets never grant access.")
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Agents on this device",Modifier.weight(1f))
            Switch(!policy.agentsPaused && !policy.emergencyStopped,{enabled->change{operations.gate.setAgentsEnabled(enabled)}},
                enabled=!busy && !policy.emergencyStopped,modifier=Modifier.testTag("agent-device-access"))
        }
        Text(if(policy.emergencyStopped) "Emergency stop is active. Resume deliberately in Operations before enabling agents."
            else if(policy.agentsPaused) "Agents paused. Managed agent jobs and tool loops are stopped; ordinary human chat stays available."
            else "Agents may use their saved grants. Turning this off cancels managed agent work; turning it on does not restart tasks.")
        TextButton(onClick={onOpen("operations")}){Text("Operations and emergency stop")}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Allow saved agents to run model tasks",Modifier.weight(1f))
            Switch(backend.delegated(AgentBackend.Scope.MODELS),{enabled->change{backend.setDelegated(AgentBackend.Scope.MODELS,enabled)}},
                enabled=!busy,modifier=Modifier.testTag("agent-model-access"))
        }
        Text("Applies to Run task and parallel agent jobs. Online models also require their provider grant. Tools in ordinary chat have their own switches.")
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Agent internet access",Modifier.weight(1f))
            Switch(searchAccess.webEnabled && searchAccess.agentWeb && !searchAccess.agentWebPaused,{enabled->change {
                val current=search.access.state.value
                search.access.saveHuman(if(enabled) current.copy(webEnabled=true,agentWeb=true,agentWebPaused=false)
                    else current.copy(agentWeb=false))
            }},enabled=!busy,modifier=Modifier.testTag("agent-internet-access"))
        }
        Text(when(hasKey){true->"Brave key stored. Authorized search queries go to Brave and may incur provider charges."
            false->"Setup needed: save a Brave Search API key before searches can work."
            null->"Search credential status is not yet available."})
        Text("Off cancels agent searches while preserving the human search setting. Page crawling has separate host/domain setup.")
        TextButton(onClick={onOpen("search")}){Text("Search key and app-wide setup")}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Agent local-content search",Modifier.weight(1f))
            Switch(searchAccess.agentLocal,{enabled->change{search.access.saveHuman(search.access.state.value.copy(agentLocal=enabled))}},
                enabled=!busy,modifier=Modifier.testTag("agent-local-search-access"))
        }
        Text("Allows matching snippets from saved chats and imported articles. This does not grant arbitrary file access.")
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("Phone app access",Modifier.weight(1f))
            Switch(phone.enabled() && automation,{enabled->change {
                if(enabled){settings.setAndroidAutomationEnabled(true);phone.setEnabled(true)}
                else try{phone.setEnabled(false)}finally{settings.setAndroidAutomationEnabled(false)}
            }},enabled=!busy && !com.meshlit.BuildConfig.PLAY_REVIEW,modifier=Modifier.testTag("agent-phone-access"))
        }
        val serviceBound=com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService.instance!=null
        Text(if(com.meshlit.BuildConfig.PLAY_REVIEW) "Phone app control is unavailable in Play review."
            else "Android Accessibility: ${if(serviceBound) "connected" else "needs your approval"}. App scope: ${if(phone.allApps()) "all supported apps" else "${phone.packages().size} approved packages"}.")
        TextButton(onClick={onOpen("openclaw")}){Text("Android permission and allowed apps")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
