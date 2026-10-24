package com.meshlit.ui.modern
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.media.*
import com.meshlit.providers.OnlineProviders
import com.meshlit.core.inference.models.OnlineProtocol
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun MediaGenerationScreen(initialKind:String="vision",onBack:()->Unit) {
    val media=koinInject<MediaGeneration>();val videos by media.videos.collectAsStateWithLifecycle()
    val providers=koinInject<OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var kind by remember{mutableStateOf(initialKind)};var profile by remember{mutableStateOf<String?>(null)}
    var model by remember{mutableStateOf("")};var prompt by remember{mutableStateOf("")};var voice by remember{mutableStateOf("coral")}
    var image by remember{mutableStateOf<Uri?>(null)};var result by remember{mutableStateOf<MediaResult?>(null)}
    var error by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    var running by remember{mutableStateOf(false)};var files by remember{mutableStateOf(media.files())}
    var player by remember{mutableStateOf<android.media.MediaPlayer?>(null)}
    fun run(action:suspend()->Unit){job=scope.launch{running=true;error=null;try{action();files=media.files()}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}finally{running=false}}}
    DisposableEffect(Unit){onDispose{job?.cancel();player?.release()}}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->image=uri}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val file=result?.file
        if(uri!=null && file!=null) run{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}} ?: error("Cannot export media")}}
    }
    Scaffold(topBar={TopAppBar(title={Text("Media studio")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}})}){insets->
    Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).padding(horizontal=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Selected online providers receive the prompt and any chosen image. Generation may incur charges. No automatic retry. Stop cancels this app's request; a provider may already be processing or charging it.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("vision","image","speech","video").forEach{k->FilterChip(kind==k,{kind=k;result=null},label={Text(k)})}}
        profiles.filter{it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}.forEach{p->FilterChip(profile==p.id,{profile=p.id},label={Text(p.name)})}
        if(profiles.none{it.enabled && it.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)}) Text("Enable an OpenAI-format provider in Settings → Online providers. Provider speech and vision require an enrolled profile. On-device speech/vision need separately qualified adapters; the starter text model does not provide them.")
        Text("Vision uses the profile's model. Generation needs a separate supported media model ID; a text model does not imply image/video/audio support.")
        if(kind!="vision") OutlinedTextField(model,{model=it},label={Text("Media model ID")},singleLine=true)
        if(kind=="speech") OutlinedTextField(voice,{voice=it},label={Text("Voice ID")},singleLine=true)
        if(kind=="vision") {OutlinedButton(enabled=!running,onClick={pick.launch(arrayOf("image/*"))}){Text(if(image==null) "Upload an image" else "Replace selected image")};if(image!=null) Text("Image selected · resized locally before upload")}
        OutlinedTextField(prompt,{prompt=it},Modifier.fillMaxWidth(),label={Text(if(kind=="speech") "Text to speak" else "Prompt / question")},minLines=3,maxLines=7)
        Row{Button(enabled=!running && profile!=null && prompt.isNotBlank(),onClick={run{result=media.generate(profile!!,kind,model,prompt,voice,image)}}){Text(if(kind=="video") "Create 4-second video job" else "Run")};TextButton(enabled=running,onClick={job?.cancel()}){Text("Stop request")}}
        if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.text?.let{SelectionContainer{Text(it)};MessageActions(it)}
        result?.file?.let{file->
            Text("${file.name} · ${file.length()} bytes")
            if(file.extension=="png") {
                val preview by produceState<android.graphics.Bitmap?>(null,file) {
                    value=withContext(Dispatchers.IO) {
                        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
                        if(bounds.outWidth !in 1..100000 || bounds.outHeight !in 1..100000) null else {
                            var sample=1;while(bounds.outWidth/sample>512 || bounds.outHeight/sample>512) sample*=2
                            BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})
                        }
                    }
                }
                preview?.let{bitmap->Image(bitmap.asImageBitmap(),"Actual generated image",Modifier.fillMaxWidth().heightIn(max=320.dp))}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={export.launch(file.name)}){Text("Save output")}
                FilledTonalButton(onClick={try{shareGeneratedFile(context,file)}catch(e:Exception){error="Output could not be shared"}}){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share")}
            }
            if(file.extension=="wav") TextButton(onClick={try{player?.release();player=android.media.MediaPlayer().apply{setDataSource(file.absolutePath);setOnPreparedListener{it.start()};setOnErrorListener{_,_,_->error="Audio playback failed";true};prepareAsync()}}catch(e:Exception){error=e.message}}){Text("Play AI-generated speech")}
        }
        videos.forEach{video->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){
            Text("${video.job.id} · ${video.job.status} · progress ${video.job.progress?.let{"$it%"} ?: "unknown"}")
            video.job.error?.let{Text(it)}
            Row{TextButton(enabled=!running,onClick={run{media.refresh(video)}}){Text("Check status")}
                if(video.job.status=="completed") TextButton(enabled=!running,onClick={run{result=media.download(video)}}){Text("Download MP4")}}
            TextButton(enabled=!running,onClick={media.forget(video)}){Text("Forget local reference (keeps remote job)")}
        }}}
        if(files.isNotEmpty()) Text("Local generated files",style=MaterialTheme.typography.titleMedium)
        files.forEach{file->Row{Text(file.name,Modifier.weight(1f));TextButton(onClick={result=MediaResult(file)}){Text("Select")};TextButton(enabled=!running,onClick={media.delete(file);files=media.files();if(result?.file==file) result=null}){Text("Delete")}}}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Text("Generic music/sound synthesis and on-device image/video generation need installed model-specific adapters. They are unavailable here. Video status is retained locally; provider retention and billing remain provider-controlled.")
    }
}

}
