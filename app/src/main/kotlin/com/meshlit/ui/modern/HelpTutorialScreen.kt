package com.meshlit.ui.modern

import android.webkit.*
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream

private data class TutorialStep(val id:String,val title:String,val instruction:String,val destination:String)
private val tutorialSteps=listOf(
    TutorialStep("device","Know your device","Open Device, Power and Acceleration. Check actual RAM/storage/ABI and usable backends. Choose smaller models/context on constrained phones.","device"),
    TutorialStep("models","Load your first local model","In Models, use the pinned bundled Qwen2.5 0.5B starter. Load, generate, stop and unload. Set startup loading only after it works. A downloaded file is not proof of inference.","models"),
    TutorialStep("style","Make it comfortable","Choose UI font, text size, light/dark palette and solid or tinted-glass surfaces. Readability and device constraints take priority over effects.","appearance"),
    TutorialStep("files","Manage AI assets","Use Files for granted storage, bounded text previews and streaming ZIP/unzip. Import validated GGUF through Models; keep datasets/adapters separate from executable models.","files"),
    TutorialStep("pairing","Add an owner-approved device","Review Network and pairing. Verify QR/fingerprint/token independently, choose roles/scopes and groups. Web/SSH enrollment does not automatically enroll a native model worker.","network"),
    TutorialStep("recovery","Try native local recovery","Load the native CPU backend, generate, save an encrypted checkpoint, stop/reload the identical model/settings, then restore. This does not prove replicated failover.","recovery"),
    TutorialStep("cloud","Configure credentials and cloud","Create an encrypted environment, configure a provider, enable only intended human actions, then refresh a real resource/catalog response. Agent access needs separate gates; missing costs stay unknown.","cloud"),
    TutorialStep("logs","Review failures and permission boundaries","Search/export logs, verify task outcomes, and enable only the agent/tool permissions you need. Never infer success from a running job, UI switch or planned capability.","logs")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HelpTutorialScreen(back:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val prefs=remember{context.getSharedPreferences("tutorial-progress",0)}
    var read by remember{mutableStateOf(prefs.getStringSet("read",emptySet()).orEmpty().toSet())}
    var full by remember{mutableStateOf(false)};var destination by remember{mutableStateOf<String?>(null)}
    var query by remember{mutableStateOf("")};var web by remember{mutableStateOf<WebView?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var html by remember{mutableStateOf<String?>(null)}
    BackHandler{if(destination!=null) destination=null else if(full) full=false else back()}
    LaunchedEffect(full){if(full && html==null)try{html=withContext(Dispatchers.IO){context.assets.open("help/index.html").bufferedReader().use{it.readText()}}}catch(e:CancellationException){throw e}catch(_:Exception){error="Offline guide unavailable"}}
    DisposableEffect(web){val owned=web;onDispose{owned?.stopLoading();owned?.destroy()}}
    LaunchedEffect(full){if(!full)web=null}
    destination?.let{ModernSettingsScreen(initialDestination=it,onExit={destination=null});return}
    Scaffold(topBar={TopAppBar(title={Text(if(full) "Full app guide" else "Guide and tutorial")},navigationIcon={IconButton(onClick={if(full) full=false else back()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding->
        if(full){Column(Modifier.fillMaxSize().padding(padding)){
            OutlinedTextField(query,{query=it;web?.findAllAsync(it)},Modifier.fillMaxWidth().padding(T.small),singleLine=true,label={Text("Find in offline guide")})
            html?.let{content->AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={WebView(it).apply{
                settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false;settings.blockNetworkLoads=true;setBackgroundColor(AndroidColor.WHITE)
                webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=request.url.host!="guide.meshlit.invalid"
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                }
                loadDataWithBaseURL("https://guide.meshlit.invalid/",content,"text/html","UTF-8",null);web=this
            }})} ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it)}
        }}else LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(T.large),verticalArrangement=Arrangement.spacedBy(T.medium)){
            item{Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(T.section)){
                Text("Your private AI workspace",style=MaterialTheme.typography.headlineSmall)
                Text("A phone first. An approved cluster when needed. Cloud and tools when you choose.")
                Text("${read.size} of ${tutorialSteps.size} lessons marked as read. This tracks reading, not successful device/model execution.")
                Button(onClick={full=true}){Text("Open illustrated full guide")}
            }}}
            tutorialSteps.forEachIndexed{index,step->item{Card{Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
                Text("${index+1}. ${step.title}",style=MaterialTheme.typography.titleMedium);Text(step.instruction)
                Row{OutlinedButton(onClick={destination=step.destination}){Text("Open section")};Checkbox(step.id in read,onCheckedChange={checked->scope.launch(Dispatchers.IO){val next=if(checked) read+step.id else read-step.id;if(prefs.edit().putStringSet("read",next).commit())read=next}});Text("Read")}
            }}}}
            item{TextButton(onClick={scope.launch(Dispatchers.IO){if(prefs.edit().remove("read").commit())read=emptySet()}}){Text("Reset tutorial reading progress")}}
        }
    }
}
