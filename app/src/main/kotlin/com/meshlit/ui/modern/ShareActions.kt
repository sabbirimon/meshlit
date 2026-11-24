package com.meshlit.ui.modern

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.meshlit.chat.ChatTokenUsage
import kotlinx.coroutines.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Human-initiated Android chooser; never publishes or sends in the background. */
internal fun shareGeneratedFile(context:Context,file:File) {
    val root=File(context.filesDir,"generated-media").canonicalFile
    require(file.canonicalFile.parentFile==root && file.isFile){"Only saved generated media can be shared"}
    val mime=when(file.extension.lowercase()){"png"->"image/png";"wav"->"audio/wav";"mp4"->"video/mp4";else->"application/octet-stream"}
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    val send=Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData=android.content.ClipData.newUri(context.contentResolver,"Meshlit output",uri)
    context.startActivity(Intent.createChooser(send,"Share output"))
}

@Composable internal fun MessageActions(text:String,usage:ChatTokenUsage?=null,showMetrics:Boolean=false,onReply:(()->Unit)?=null) {
    val clipboard=LocalClipboardManager.current
    val context=LocalContext.current
    var error by remember{mutableStateOf<String?>(null)}
    var reader by remember{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var details by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")){uri->if(uri!=null) scope.launch {
        busy=true
        try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{it.write(text)} ?: error("Cannot write selected document")}}
        catch(c:CancellationException){throw c}catch(e:Exception){error="Response export failed: ${e.javaClass.simpleName}"}finally{busy=false}
    }}
    if(reader) ReplyReader(text){reader=false}
    if(details) TokenUsageDetails(usage){details=false}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onClick={clipboard.setText(AnnotatedString(text))}){
            Icon(Icons.Default.ContentCopy,"Copy response",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick={reader=true}){
            Icon(Icons.Default.OpenInFull,"Read full response",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(enabled=!busy,onClick={export.launch("meshlit-response.md")}) { Icon(Icons.Default.FileDownload,"Download reply",Modifier.size(18.dp)) }
        androidx.compose.foundation.layout.Box {
        IconButton(onClick={menu=true}){
            Icon(Icons.Default.MoreVert,"Response actions",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu,{menu=false}) {
            onReply?.let { reply -> DropdownMenuItem(text={Text("Reply")},onClick={menu=false;reply()}) }
            DropdownMenuItem(text={Text("Share response")},onClick={menu=false;try {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share response"))
            } catch(e:Exception){error="No sharing app is available"}})
            DropdownMenuItem(text={Text("Save response as Markdown")},enabled=!busy,onClick={menu=false;export.launch("meshlit-response.md")})
            DropdownMenuItem(text={Text("Response token details")},onClick={menu=false;details=true})
        }
        }
    }
    if(showMetrics && usage!=null) TextButton(onClick={details=true}) {
        val rate=usage.tokensPerSecond?.let{java.lang.String.format(java.util.Locale.ROOT,"%.1f tok/s %s",it,if(usage.rateBasis?.startsWith("Runtime")==true)"decode" else "overall")}
        val elapsed=java.lang.String.format(java.util.Locale.ROOT,"%.1f s elapsed",usage.elapsedMs/1000.0)
        Text(listOfNotNull(rate ?: "Speed unreported",elapsed,usage.output?.let{"$it output tokens"},usage.contextCapacity?.let{"$it ctx"}).joinToString(" · "),
            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
    }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Cannot share")},text={Text(it)},
        confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}

@Composable internal fun DownloadGeneratedFile(filename:String) {
    val context=LocalContext.current
    val media=com.meshlit.di.koinInject<com.meshlit.media.MediaGeneration>()
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember(filename) { mutableStateOf<String?>(null) }
    val mime=when(filename.substringAfterLast('.')) {
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "wav" -> "audio/wav"
        else -> return
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try {
                withContext(Dispatchers.IO) {
                    val file=media.files().firstOrNull { it.name==filename } ?: error("The original file is no longer in Media studio.")
                    file.inputStream().use { input ->
                        val stream=context.contentResolver.openOutputStream(uri,"wt") ?: error("Cannot open the selected location")
                        stream.use { output -> input.copyTo(output) }
                    }
                }
                status="File saved"
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {status=e.message ?: "Download failed"}
            finally {busy=false}
        }
    }
    TextButton(enabled=!busy,onClick={save.launch(File(filename).name)}) {
        Icon(Icons.Default.FileDownload,null)
        Text("Download file")
    }
    status?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
}
