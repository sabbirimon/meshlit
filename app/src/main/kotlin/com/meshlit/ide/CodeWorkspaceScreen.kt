package com.meshlit.ide
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meshlit.core.mcp.control.CodeWorkspace
import com.meshlit.di.koinInject
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CodeWorkspaceScreen(onBack:()->Unit){
    val context=LocalContext.current;val workspace=koinInject<CodeWorkspace>();val scope=rememberCoroutineScope()
    var files by remember{mutableStateOf<List<String>>(emptyList())};var name by remember{mutableStateOf("main.kt")}
    var text by remember{mutableStateOf("fun main() {\n    println(\"Hello, Meshlit\")\n}\n")};var hash by remember{mutableStateOf<String?>(null)}
    var message by remember{mutableStateOf<String?>(null)};var web by remember{mutableStateOf<WebView?>(null)};var ready by remember{mutableStateOf(false)}
    var newFile by remember{mutableStateOf(false)};var newName by remember{mutableStateOf("main.py")}
    fun refresh(){scope.launch{files=withContext(Dispatchers.IO){workspace.list().map{it.name}}}}
    LaunchedEffect(workspace){refresh()}
    LaunchedEffect(name,text,ready){if(ready) web?.evaluateJavascript("setEditor(${JsonPrimitive(text)},${JsonPrimitive(name)})",null)}
    val saveHandler by rememberUpdatedState<(String)->Unit>({value ->scope.launch{try{
        val result=withContext(Dispatchers.IO){workspace.write(name,value,hash)};hash=result.sha256;text=value;message="Saved $name";refresh()
    }catch(e:Exception){message=e.message}}})
    DisposableEffect(Unit){onDispose{web?.removeJavascriptInterface("NativeEditor");web?.destroy()}}
    Scaffold(topBar={TopAppBar(title={Text("Code workspace")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){padding ->
        Column(Modifier.fillMaxSize().padding(padding)){
            Row(Modifier.padding(T.small),horizontalArrangement=Arrangement.spacedBy(T.small)){
                Button(onClick={web?.evaluateJavascript("saveEditor()",null)}){Text("Save")}
                OutlinedButton(onClick={newFile=true}){Text("New")}
                var expanded by remember{mutableStateOf(false)}
                Box{OutlinedButton(onClick={expanded=true}){Text("Files (${files.size})")};DropdownMenu(expanded,{expanded=false}){files.forEach{file ->DropdownMenuItem(text={Text(file)},onClick={expanded=false;scope.launch{try{val item=withContext(Dispatchers.IO){workspace.read(file)};name=file;text=item.text.orEmpty();hash=item.sha256}catch(e:Exception){message=e.message}}})}}}
            }
            Text(name,Modifier.padding(horizontal=T.large),style=MaterialTheme.typography.titleSmall)
            Text("Offline CodeMirror editor · Save before switching files · Compiling uses an optional external/Linux toolchain",Modifier.padding(horizontal=T.large),style=MaterialTheme.typography.bodySmall)
            message?.let{Text(it,Modifier.padding(T.small),color=MaterialTheme.colorScheme.primary)}
            AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={WebView(it).apply{
                settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
                    override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse{
                        val path=request.url.path.orEmpty().removePrefix("/")
                        return if(request.url.scheme=="https" && request.url.host=="meshlit-editor.local" && path in setOf("index.html","editor.js"))
                            WebResourceResponse(if(path.endsWith(".js")) "application/javascript" else "text/html","UTF-8",context.assets.open("ide/$path"))
                        else WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                }
                addJavascriptInterface(object{
                    @JavascriptInterface fun ready(){scope.launch{ready=true}}
                    @JavascriptInterface fun save(value:String){if(value.length<=1_048_576) scope.launch{saveHandler(value)}}
                },"NativeEditor")
                web=this;loadUrl("https://meshlit-editor.local/index.html")
            }})
        }
    }
    if(newFile) AlertDialog(onDismissRequest={newFile=false},title={Text("New source file")},text={OutlinedTextField(newName,{newName=it},label={Text("Filename, e.g. main.kt")})},
        confirmButton={TextButton(onClick={scope.launch{try{val item=withContext(Dispatchers.IO){workspace.write(newName,"",null)};name=item.name;text="";hash=item.sha256;newFile=false;refresh()}catch(e:Exception){message=e.message}}}){Text("Create")}},dismissButton={TextButton(onClick={newFile=false}){Text("Cancel")}})
}
