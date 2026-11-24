package com.meshlit.media
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.providers.OnlineProviders
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID

@Serializable data class SavedVideo(val profileId:String,val endpoint:String,val job:RemoteVideoJob)
data class MediaResult(val file:File?=null,val mime:String?=null,val text:String?=null,val usage:OnlineReply?=null)

class MediaGeneration(private val context:Context,private val providers:OnlineProviders) {
    private val operations=com.meshlit.operations.OperationsControl.get(context).gate
    private suspend fun <T> managed(work:suspend()->T):T = operations.run(com.meshlit.core.common.control.ManagedFeature.MEDIA) { operations.run(com.meshlit.core.common.control.ManagedFeature.CLOUD) { lock.withLock { work() } } }
    val client=OnlineMediaClient()
    private val directory=File(context.filesDir,"generated-media").apply{mkdirs()}
    private val store=EncryptedCredentialStore(context,"media-video-jobs")
    private val _videos=MutableStateFlow(runCatching{store.get("videos")?.let{Json.decodeFromString<List<SavedVideo>>(it)}}.getOrNull().orEmpty())
    val videos=_videos.asStateFlow()
    private val lock=Mutex()
    private fun profile(id:String)=providers.profiles.value.firstOrNull{it.id==id} ?: error("Provider profile no longer exists")
    private fun target(extension:String):File {
        val reservation=when(extension){"mp4"->64L;"wav"->16L;else->8L}*1024*1024
        require(directory.listFiles().orEmpty().sumOf{it.length()}+reservation<=128L*1024*1024){"Media quota reached; export/delete existing outputs first"}
        return File(directory,"${UUID.randomUUID()}.$extension")
    }
    private fun save(items:List<SavedVideo>){store.put("videos",Json.encodeToString(items));_videos.value=items}
    suspend fun generate(profileId:String,kind:String,model:String,prompt:String,voice:String="coral",image:Uri?=null,expectedProfile:OnlineProfile?=null):MediaResult=managed {
        val p=profile(profileId)
        require(expectedProfile==null || p==expectedProfile){"Photo provider changed. Review the selected model and send again."}
        val key=providers.resolveToken(p)
        when(kind){
            "image"->MediaResult(client.image(p,key,model,prompt,target("png")),"image/png")
            "speech"->MediaResult(client.speech(p,key,model,prompt,voice,target("wav")),"audio/wav")
            "vision"->{val reply=client.visionReply(p,key,readImage(image ?: error("Select an image first")),prompt);MediaResult(text=reply.text,usage=reply)}
            "video"->{require(_videos.value.size<20){"At most 20 saved remote video references"};val job=client.startVideo(p,key,model,prompt);save(_videos.value+SavedVideo(p.id,p.endpoint,job));MediaResult(text="Remote job ${job.id} · ${job.status}. Check status below; this is not a finished video.")}
            else->error("Unsupported media operation")
        }
    }
    suspend fun refresh(video:SavedVideo)=managed {
        val p=profile(video.profileId);require(p.endpoint==video.endpoint){"Profile endpoint changed; refusing to send the old job reference"}
        val updated=video.copy(job=client.videoStatus(p,providers.resolveToken(p),video.job.id))
        save(_videos.value.map{if(it.profileId==video.profileId && it.job.id==video.job.id) updated else it})
    }
    suspend fun download(video:SavedVideo):MediaResult=managed {
        require(video.job.status=="completed"){"Video is not complete"}
        val p=profile(video.profileId);require(p.endpoint==video.endpoint)
        MediaResult(client.videoContent(p,providers.resolveToken(p),video.job.id,target("mp4")),"video/mp4")
    }
    fun forget(video:SavedVideo){save(_videos.value.filterNot{it.profileId==video.profileId && it.job.id==video.job.id})}
    private val extensions=mapOf("image/png" to "png","image/jpeg" to "jpg","image/webp" to "webp","image/gif" to "gif",
        "video/mp4" to "mp4","video/webm" to "webm","audio/mpeg" to "mp3","audio/mp3" to "mp3","audio/ogg" to "ogg",
        "audio/wav" to "wav","audio/x-wav" to "wav","audio/mp4" to "m4a","audio/flac" to "flac",
        "application/pdf" to "pdf","text/markdown" to "md","text/plain" to "txt","application/json" to "json","application/xml" to "xml",
        "application/javascript" to "js","text/javascript" to "js","text/css" to "css","text/html" to "html","text/x-python" to "py",
        "text/csv" to "csv","application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx")
    fun mimeFor(filename:String)=extensions.entries.firstOrNull { it.value==filename.substringAfterLast('.') }?.key ?: "application/octet-stream"
    suspend fun saveToolFile(mime:String,encoded:String?,text:String?):File=withContext(Dispatchers.IO) {
        lock.withLock {
            val ext=extensions[mime.substringBefore(';').lowercase()] ?: if(mime.startsWith("text/"))"txt" else "bin"
            require((encoded?.length ?: text?.length ?: 0) in 1..11_184_812) { "Attachment exceeds the tool file limit" }
            val bytes=if(encoded!=null)android.util.Base64.decode(encoded,android.util.Base64.DEFAULT) else requireNotNull(text).toByteArray()
            require(bytes.size in 1..8*1024*1024)
            val file=target(ext)
            try {file.writeBytes(bytes);file} catch(e:Exception){file.delete();throw e}
        }
    }
    fun files()=directory.listFiles().orEmpty().filter{it.isFile && (it.extension in extensions.values || it.extension=="bin")}.sortedByDescending{it.lastModified()}
    fun delete(file:File){require(file.parentFile?.canonicalFile==directory.canonicalFile);check(file.delete())}
    internal suspend fun readImage(uri:Uri):ByteArray=withContext(Dispatchers.IO) {
        require(uri.scheme=="content")
        val bytes=context.contentResolver.openInputStream(uri)?.use{input->
            val out=ByteArrayOutputStream();val buffer=ByteArray(32768)
            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break;require(out.size()+n<=8*1024*1024){"Image exceeds 8 MiB"};out.write(buffer,0,n)}
            out.toByteArray()
        } ?: error("Image permission is unavailable")
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..100000 && bounds.outHeight in 1..100000){"Unsupported image"}
        var scale=1;while(bounds.outWidth/scale>1024 || bounds.outHeight/scale>1024) scale*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=scale}) ?: error("Cannot decode image")
        try {ByteArrayOutputStream().use{out->check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out));out.toByteArray().also{require(it.size<=2*1024*1024)}}}
        finally{bitmap.recycle()}
    }
}
