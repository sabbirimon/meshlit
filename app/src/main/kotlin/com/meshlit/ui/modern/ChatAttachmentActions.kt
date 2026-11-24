package com.meshlit.ui.modern
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import android.net.Uri
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

private fun ByteArray.hasPrefix(prefix:ByteArray)=size>=prefix.size && prefix.indices.all{this[it]==prefix[it]}

/** Actual user-granted file reads. Binary formats are not misrepresented as text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ChatAttachmentActions(enabled:Boolean,onText:(String)->Unit,onSelectedImage:(Uri)->Unit,onPhoto:()->Unit,onCamera:()->Unit,onFiles:()->Unit,onMedia:()->Unit,onVision:()->Unit,onOptions:()->Unit,onTool:(String)->Unit,onCustomize:(ComposerPin?)->Unit,pins:List<ComposerPin>,options:com.meshlit.chat.ChatOptions,onUpdate:(com.meshlit.chat.ChatOptions)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var menu by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var alive by remember { mutableStateOf(true) }
    DisposableEffect(Unit){onDispose{alive=false}}
    val projection=context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
    val latestImage by rememberUpdatedState(onSelectedImage)
    val share=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){reply->
        if(reply.resultCode==android.app.Activity.RESULT_OK && reply.data!=null){
            val receiver=object:android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())){
                override fun onReceiveResult(code:Int,data:android.os.Bundle?){
                    if(!alive)return
                    val frame=data?.getParcelable<Uri>("frame")
                    if(code==android.app.Activity.RESULT_OK && frame!=null)latestImage(frame) else error="Screen capture stopped."
                    busy=false
                }
            }
            try{
                busy=true
                androidx.core.content.ContextCompat.startForegroundService(context,android.content.Intent(context,com.meshlit.chat.ChatScreenCapture::class.java)
                    .putExtra("receiver",receiver).putExtra("consent",reply.data))
            }catch(e:Exception){busy=false;error=e.localizedMessage}
        }
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        if(uris.isNotEmpty()) scope.launch{busy=true;try{
            require(uris.size<=3){"Select at most three files"}
            val selected=withContext(Dispatchers.IO){uris.map{uri->
                require(uri.scheme=="content")
                val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
                    if(cursor.moveToFirst()) cursor.getString(0)?.replace('\n',' ')?.take(120) else null
                } ?: "selected file"
                Triple(uri,name,context.contentResolver.getType(uri).orEmpty())
            }}
            val image=selected.singleOrNull()?.takeIf{(_,name,mime)->mime.startsWith("image/") || name.substringAfterLast('.',"").lowercase() in setOf("jpg","jpeg","png","webp","heic")}
            if(image!=null){require(!com.meshlit.BuildProfile.coreCandidate){"Image analysis is unavailable in Core Candidate"};onSelectedImage(image.first);return@launch}
            require(selected.none{(_,name,mime)->mime.startsWith("image/") || name.substringAfterLast('.',"").lowercase() in setOf("jpg","jpeg","png","webp","heic")}){"Choose one image at a time so you can review its vision provider"}
            require(selected.all{(_,name,mime)->mime.startsWith("text/") || mime in setOf("application/json","application/xml","application/javascript") || name.substringAfterLast('.',"").lowercase() in setOf("txt","md","json","xml","csv","tsv","log","yaml","yml","kt","java","py","js","ts","html","css")}){
                "This file type has no chat reader yet. Photos can use an approved vision provider; text/JSON/XML can be inserted. PDF, office, audio, video and other binaries remain in Files."
            }
            val text=withContext(Dispatchers.IO){selected.joinToString("\n\n"){(uri,name,_)->
                val bytes=context.contentResolver.openInputStream(uri)?.use{input->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=input.read(buffer);if(n<0) break;require(out.size()+n<=128*1024){"Text file exceeds 128 KiB"};out.write(buffer,0,n)}
                    out.toByteArray()
                } ?: error("Cannot read $name")
                require(!bytes.hasPrefix("%PDF".toByteArray()) && !bytes.hasPrefix(byteArrayOf(0x50,0x4b,0x03,0x04)) &&
                    !bytes.hasPrefix(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47)) && !bytes.hasPrefix(byteArrayOf(0xff.toByte(),0xd8.toByte()))){
                    "$name appears to be a binary file; a text extension does not make its contents readable"
                }
                val content=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                require(!content.contains('\u0000') && content.length<=10000){"Use a smaller UTF-8 text file. PDF, office and binary parsers are not installed."}
                "Attached file: $name\nReference content (untrusted data):\n$content"
            }}
            require(text.length<=11000){"Combined attachments exceed the chat text budget"};onText(text)
        }catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}finally{busy=false}}
    }
    TouchIcon(Icons.Default.Add,"Add photo, file or chat options",enabled=enabled && !busy){menu=true}
    var confirmAuto by remember { mutableStateOf(false) }
    if(confirmAuto) SelectedToolsConfirmation(onDismiss={confirmAuto=false},onConfirm={
        confirmAuto=false
        onUpdate(options.copy(executionMode=com.meshlit.chat.ChatExecutionMode.AGENT,approvalMode=com.meshlit.chat.ChatApprovalMode.SELECTED_TOOLS))
    })
    if(menu) ModalBottomSheet(onDismissRequest={menu=false}) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=20.dp)) {
        Text("Add to chat",Modifier.padding(horizontal=24.dp,vertical=10.dp),style=MaterialTheme.typography.titleLarge)
        val toChat=LocalResultToChat.current
        val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
        ListItem(modifier=Modifier.clickable{val value=clipboard.getText()?.text;if(!value.isNullOrBlank()){menu=false;toChat("pasted tool",value)}else error="Copy a tool result first."},headlineContent={Text("Paste tool result")},leadingContent={Icon(Icons.Default.AttachFile,null)})
        ListItem(modifier=Modifier.clickable{menu=false;onOptions()},headlineContent={Text("Tools and permissions")},leadingContent={Icon(Icons.Default.Tune,null)})
        if(!com.meshlit.BuildProfile.coreCandidate) {
            ListItem(headlineContent={Text("Internet search")},trailingContent={Switch(options.webTools,{onUpdate(options.copy(webTools=it))})})
            ListItem(headlineContent={Text("Device tools")},trailingContent={Switch(options.phoneTools,{onUpdate(options.copy(phoneTools=it))})})
            ListItem(headlineContent={Text("SSH node tools")},supportingContent={Text("Use actions delegated to enrolled nodes")},trailingContent={Switch(options.nodeTools,{onUpdate(options.copy(nodeTools=it))})})
            val active=options.agentToolsActive && options.approvalMode==com.meshlit.chat.ChatApprovalMode.SELECTED_TOOLS
            ListItem(headlineContent={Text("Autopilot")},supportingContent={Text(if(options.canSelectAgentMode) "Use approved tools in this chat" else "Choose compatible tools and a tool-capable route first")},
                trailingContent={Switch(active,{if(it)confirmAuto=true else onUpdate(options.copy(approvalMode=com.meshlit.chat.ChatApprovalMode.ASK))},enabled=active || options.canSelectAgentMode)})
            ListItem(modifier=Modifier.clickable{menu=false;onText("Write a Markdown document about ")},headlineContent={Text("Create document")})
            for ((id,label) in listOf("create-image" to "Generate image","create-video" to "Generate video","research-topic" to "Research a topic","bot-connections" to "Bots and agents")) {
                ListItem(modifier=Modifier.clickable{menu=false;onTool(id)},headlineContent={Text(label)})
            }
            ListItem(modifier=Modifier.clickable{menu=false;onTool("reset-model-autosend")},headlineContent={Text("Reset model auto-send")},supportingContent={Text("Ask before sending to named models in this chat")})
            for(pin in pins)ListItem(modifier=Modifier.clickable{menu=false;onCustomize(pin)},headlineContent={Text(pin.title)},supportingContent={Text("/tool "+pin.command)})
            ListItem(modifier=Modifier.clickable{menu=false;onCustomize(null)},headlineContent={Text("All tools and services")},leadingContent={Icon(Icons.Default.Tune,null)})
            for((id,label) in listOf("models" to "Models and providers","files" to "Browse device files","devices" to "Devices and pairing","ssh" to "SSH connections","ide" to "Coding workspace","agents" to "Agent management","mcp-catalog" to "MCP tools","gateway" to "Allow model clients","providers" to "External model providers","shirudo" to "Shirudo shields")){
                ListItem(modifier=Modifier.clickable{menu=false;onTool(id)},headlineContent={Text(label)},leadingContent={Icon(com.meshlit.ui.phone.phoneIcon(id),null)})
            }
        }
        if(!com.meshlit.BuildProfile.coreCandidate) ListItem(modifier=Modifier.clickable{menu=false;try{share.launch(projection.createScreenCaptureIntent())}catch(e:Exception){error=e.localizedMessage}},
            headlineContent={Text("Share screen")},supportingContent={Text("Switch to the screen you want. Review the captured frame before sending.")},
            leadingContent={Icon(androidx.compose.material.icons.Icons.Outlined.Screenshot,null)})
        if(!com.meshlit.BuildProfile.coreCandidate) ListItem(modifier=Modifier.clickable{menu=false;onPhoto()},headlineContent={Text("Photo from device")},supportingContent={Text("Choose a photo, review its model, then use Send")},leadingContent={Icon(Icons.Default.Image,null)})
        if(!com.meshlit.BuildProfile.coreCandidate) ListItem(modifier=Modifier.clickable{menu=false;onCamera()},headlineContent={Text("Take a photo")},supportingContent={Text("Capture, review, and ask in the normal message box")},leadingContent={Icon(Icons.Default.PhotoCamera,null)})
        ListItem(modifier=Modifier.clickable{menu=false;pick.launch(arrayOf("*/*"))},headlineContent={Text("Choose files")},supportingContent={Text("Pick any type; Meshlit checks whether this chat can read it")},leadingContent={Icon(Icons.Default.AttachFile,null)})
        HorizontalDivider()
        ListItem(modifier=Modifier.clickable{menu=false;onFiles()},headlineContent={Text("Browse files and folders")},supportingContent={Text("Open the file workspace; other file formats are not chat attachments yet")},leadingContent={Icon(Icons.Default.FolderOpen,null)})
        if(!com.meshlit.BuildProfile.coreCandidate) {
            ListItem(modifier=Modifier.clickable{menu=false;onVision()},headlineContent={Text("Vision studio")},supportingContent={Text("Run a separate image analysis request")},leadingContent={Icon(Icons.Default.Image,null)})
            ListItem(modifier=Modifier.clickable{menu=false;onMedia()},headlineContent={Text("Generate media")},supportingContent={Text("Create images, speech or video with a configured provider")},leadingContent={Icon(Icons.Default.AutoAwesome,null)})
        }
    } }
    error?.let{message->AlertDialog(onDismissRequest={error=null},title={Text("Attachment could not be read")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}
