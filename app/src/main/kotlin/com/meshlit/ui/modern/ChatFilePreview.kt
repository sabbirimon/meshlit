package com.meshlit.ui.modern

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

private fun bitmap(file:File,pageIndex:Int):Bitmap? {
    if(file.extension=="pdf"){
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                if(pdf.pageCount==0)return null
                pdf.openPage(pageIndex).use { page ->
                    val scale=minOf(960f/page.width,1280f/page.height)
                    val result=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                    result.eraseColor(android.graphics.Color.WHITE)
                    page.render(result,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return result
                }
            }
        }
    }
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth !in 1..100000 || bounds.outHeight !in 1..100000)return null
    var sample=1
    while(bounds.outWidth/sample>1280 || bounds.outHeight/sample>1280)sample*=2
    return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample})
}

private fun documentText(file:File):String {
    if(file.extension!="docx"){
        return file.bufferedReader().use { reader ->
            val buffer=CharArray(16001)
            var size=0
            while(size<buffer.size){val n=reader.read(buffer,size,buffer.size-size);if(n<0)break;size+=n}
            String(buffer,0,minOf(size,16000))+(if(size>16000)"…" else "")
        }
    }
    return ZipFile(file).use { zip ->
        val entry=requireNotNull(zip.getEntry("word/document.xml"))
        require(entry.size in 1..2L*1024*1024)
        zip.getInputStream(entry).use { input ->
            val parser=Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false)
            parser.setInput(input,"UTF-8")
            val text=StringBuilder()
            while(parser.next()!=XmlPullParser.END_DOCUMENT && text.length<16000){
                if(parser.eventType==XmlPullParser.START_TAG && parser.name.substringAfter(':')=="t")text.append(parser.nextText())
                if(parser.eventType==XmlPullParser.END_TAG && parser.name.substringAfter(':')=="p")text.appendLine()
            }
            text.take(16000).toString()
        }
    }
}

@Composable internal fun ChatFilePreview(file:File,mime:String) {
    var expanded by remember(file) { mutableStateOf(true) }
    TextButton(onClick={expanded=!expanded}) {Text(if(expanded)"Hide preview" else "Show preview")}
    if(!expanded)return
    when {
        mime.startsWith("image/") || mime=="application/pdf" -> {
            var pageIndex by remember(file) {mutableIntStateOf(0)}
            val pages by produceState(0,file){
                if(mime=="application/pdf")value=withContext(Dispatchers.IO){runCatching {
                    ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use {it.pageCount} }
                }.getOrDefault(0)}
            }
            val preview by produceState<Bitmap?>(null,file,pageIndex){
                value=withContext(Dispatchers.IO){try {bitmap(file,pageIndex)} catch(e:Exception){null}}
            }
            preview?.let { Image(it.asImageBitmap(),if(mime=="application/pdf")"Document page ${pageIndex+1}" else "Image preview",
                Modifier.fillMaxWidth().heightIn(max=340.dp),contentScale=ContentScale.Fit) }
            if(pages>1) Row {
                TextButton(enabled=pageIndex>0,onClick={pageIndex--}){Text("Previous")}
                Text("Page ${pageIndex+1} of $pages",Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                TextButton(enabled=pageIndex+1<pages,onClick={pageIndex++}){Text("Next")}
            }
        }
        mime.startsWith("audio/") || mime.startsWith("video/") -> {
            var active by remember(file){mutableStateOf(false)}
            var problem by remember(file){mutableStateOf(false)}
            if(problem)Text("Playback unavailable for this encoding")
            if(!active && mime.startsWith("video/") && android.os.Build.VERSION.SDK_INT>=27){
                val poster by produceState<Bitmap?>(null,file){
                    value=withContext(Dispatchers.IO){
                        val reader=android.media.MediaMetadataRetriever()
                        try{reader.setDataSource(file.path);reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,640,360)}
                        catch(e:Exception){null}finally{reader.release()}
                    }
                }
                poster?.let{Image(it.asImageBitmap(),"Video preview",Modifier.fillMaxWidth().heightIn(max=230.dp),contentScale=ContentScale.Fit)}
            }
            if(!active)TextButton(onClick={problem=false;active=true}){Text(if(mime.startsWith("video"))"Play video" else "Play audio")}
            else AndroidView(factory={context->VideoView(context).apply {
                val controls=MediaController(context)
                setMediaController(controls)
                controls.setAnchorView(this)
                setOnPreparedListener { start();controls.show(3000) }
                setOnErrorListener { _,_,_->problem=true;active=false;true }
                setOnCompletionListener { active=false }
                setVideoPath(file.absolutePath)
            }},modifier=Modifier.fillMaxWidth().height(if(mime.startsWith("video"))220.dp else 64.dp),onRelease={it.stopPlayback()})
        }
        mime.startsWith("text/") || file.extension in setOf("json","xml","docx","js") -> {
            val text by produceState("",file){
                value=withContext(Dispatchers.IO){try {documentText(file)} catch(e:CancellationException){throw e} catch(e:Exception){"Preview unavailable"}}
            }
            SelectionContainer { Text(text,fontFamily=if(file.extension in setOf("docx","md","txt"))null else FontFamily.Monospace,
                style=MaterialTheme.typography.bodyMedium) }
        }
        else -> Text("Preview unavailable for this file type")
    }
}
