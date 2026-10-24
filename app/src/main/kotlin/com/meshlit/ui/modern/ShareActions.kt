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
import androidx.compose.material.icons.filled.MoreVert
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

@Composable internal fun MessageActions(text:String,usage:ChatTokenUsage?=null) {
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
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) { Row {
        IconButton(onClick={clipboard.setText(AnnotatedString(text))}){
            Icon(Icons.Default.ContentCopy,"Copy response",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick={try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,text),"Share response"))
        }catch(e:Exception){error="No sharing app is available"}}){
            Icon(Icons.Default.Share,"Share response",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick={reader=true}){
            Icon(Icons.Default.OpenInFull,"Read full response",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick={menu=true}){
            Icon(Icons.Default.MoreVert,"Response actions",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("Save response as Markdown")},enabled=!busy,onClick={menu=false;export.launch("meshlit-response.md")})
            DropdownMenuItem(text={Text("Response token details")},onClick={menu=false;details=true})
        }
    } }
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Cannot share")},text={Text(it)},
        confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}
