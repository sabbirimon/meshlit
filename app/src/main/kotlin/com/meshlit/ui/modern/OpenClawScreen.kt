package com.meshlit.ui.modern

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.meshlit.di.koinInject
import com.meshlit.openclaw.*
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OpenClawScreen(onBack:()->Unit){
    val host=koinInject<OpenClawHost>();val control=koinInject<AndroidControl>();val settings=koinInject<SettingsRepository>()
    val backend=koinInject<com.meshlit.control.AgentBackend>()
    val node=koinInject<OpenClawNode>();val nodeStatus by node.status.collectAsState()
    val context=LocalContext.current;val scope=rememberCoroutineScope();val status by host.state.collectAsState()
    var url by remember{mutableStateOf(host.gateway())};var token by remember{mutableStateOf(host.gatewayToken())}
    var agent by remember{mutableStateOf(host.agent())};var prompt by remember{mutableStateOf("")};var answer by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)};var sending by remember{mutableStateOf(false)};var task by remember{mutableStateOf<Job?>(null)}
    val session=remember{UUID.randomUUID().toString()}
    var delegated by remember{mutableStateOf(control.enabled())};var allApps by remember{mutableStateOf(control.allApps())}
    var installedBrowsers by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())}
    LaunchedEffect(context){installedBrowsers=withContext(Dispatchers.IO){context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://example.com")),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).map{it.activityInfo.packageName to it.loadLabel(context.packageManager).toString()}.distinctBy{it.first}.sortedBy{it.second}}}
    var packages by remember{mutableStateOf(control.packages().sorted().joinToString("\n"))}
    fun run(action:suspend()->Unit){scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}}
    Scaffold(topBar={TopAppBar(title={Text("Phone agent and autonomy")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{Text("Built-in phone agent",style=MaterialTheme.typography.titleLarge)
                Text("Runs with a model loaded on this device. In Chat → Conversation settings, choose the local model and turn on Allow phone tools. Enable delegation and save an app scope below. No gateway, cloud account or other host is required for phone actions.")
                Text("The agent can inspect and operate approved apps while this task runs. It stops after eight actions or five minutes, and Chat Stop cancels it. A small starter model may fail to plan a complex task; the app reports that failure instead of pretending it succeeded.",style=MaterialTheme.typography.bodySmall)}
            if(!com.meshlit.BuildConfig.PLAY_REVIEW){
            item{HorizontalDivider();Text("Android app control",style=MaterialTheme.typography.titleLarge)
                Text("Allow the local agent to snapshot, open, tap, type, go Back and Home within saved scope. Enable the Android accessibility service separately. Root, protected OS surfaces and password input are outside this permission.")
                Row{Text("Autonomous delegation",Modifier.weight(1f));Switch(delegated,{on ->run{
                    settings.setAndroidAutomationEnabled(on);control.setEnabled(on);delegated=on
                    if(on) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }})}
                Row{Text("All supported apps",Modifier.weight(1f));Switch(allApps,{control.setAllApps(it);allApps=it},enabled=delegated)}}
            item{Text("Phone web browsers",style=MaterialTheme.typography.titleMedium)
                Text("The local agent can use android_control open/snapshot/click/type/back after saved app scope and Android Accessibility permission. Login and verification need human input.")
                if(installedBrowsers.isEmpty())Text("No visible HTTPS browser found")
                installedBrowsers.forEach{(packageId,label)->OutlinedButton(enabled=!allApps,onClick={packages=(packages.lines().filter{it.isNotBlank()}+packageId).distinct().joinToString("\n")}){Text("Add $label to app scope")}}
                Text("Review package names and save the scope below. Selecting a browser does not grant Accessibility or autonomy.",style=MaterialTheme.typography.bodySmall)
            }
            item{OutlinedTextField(packages,{packages=it},Modifier.fillMaxWidth(),label={Text("Delegated package names, one per line")},minLines=3,enabled=!allApps)
                Button(onClick={run{control.savePackages(packages.lines().map{it.trim()}.filter{it.isNotBlank()}.toSet())}}){Text("Save app scope")}
                Button(onClick={control.setEnabled(false);delegated=false;node.disconnect();host.stopSharing();task?.cancel();run{settings.setAndroidAutomationEnabled(false)}}){Text("Emergency stop")}
                Text("Revocation affects the next dispatched action. Android grants remain managed by the operating system.",style=MaterialTheme.typography.bodySmall)}
            } else item{Text("Cross-app Android autonomy is unavailable in the Play review build.")}
            item{HorizontalDivider();Text("Optional OpenClaw gateway",style=MaterialTheme.typography.titleLarge)
                Text("Use this only when you choose an external gateway. Its agent runtime runs on that host; it is separate from the built-in phone agent.")}
            item{OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("Gateway HTTPS URL")},singleLine=true)}
            item{OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("Gateway operator token (encrypted)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                Text("This token grants gateway operator access. Use a private HTTPS gateway you own.",style=MaterialTheme.typography.bodySmall)}
            item{OutlinedTextField(agent,{agent=it},Modifier.fillMaxWidth(),label={Text("Agent target, e.g. openclaw/default")},singleLine=true)
                Button(onClick={run{host.saveGateway(url,token,agent)}},enabled=!sending){Text("Save gateway")}}
            item{OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),label={Text("Ask the OpenClaw agent")},minLines=2)
                Row(horizontalArrangement=Arrangement.spacedBy(T.small)){
                    Button(onClick={task=scope.launch{sending=true;error=null;try{answer=host.send(prompt,session)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{sending=false}}},enabled=!sending && prompt.isNotBlank()){Text("Send")}
                    if(sending) OutlinedButton(onClick={task?.cancel()}){Text("Stop")}
                }
                if(answer.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer{Text(answer)}}
            item{Text(nodeStatus);Row(horizontalArrangement=Arrangement.spacedBy(T.small)){Button(onClick={run{node.connect()}}){Text("Connect Android node")};OutlinedButton(onClick={node.disconnect()}){Text("Disconnect")}}}
            item{HorizontalDivider();Text("Pair a phone model",style=MaterialTheme.typography.titleLarge)
                Text("Load a model in Models or the layer pipeline first. Sharing exposes meshlit-local at http://127.0.0.1:${host.port}/v1. A remote gateway needs an owner-configured TLS tunnel. Text chat and buffered SSE are supported; function tool calls are currently rejected.")
                Row{Text("Phone model endpoint",Modifier.weight(1f));Switch(status.sharing,{on ->run{if(on) host.startSharing() else host.stopSharing()}})}}
            item{OutlinedButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Meshlit provider token",host.providerToken()))}){Text("Copy provider token")}
                Text("Keep this token private. Disabling sharing closes the listener immediately.",style=MaterialTheme.typography.bodySmall)}
            item{Text("Typed backend delegation",style=MaterialTheme.typography.titleLarge);com.meshlit.control.AgentBackend.Scope.entries.forEach{permission ->
                var allowed by remember{mutableStateOf(backend.delegated(permission))}
                Row{Text(permission.name.lowercase(),Modifier.weight(1f));Switch(allowed,{backend.setDelegated(permission,it);allowed=it})}
            }}
            (error ?: status.error)?.let{message ->item{ErrorCard(message){error=null}}}
        }
    }
}
