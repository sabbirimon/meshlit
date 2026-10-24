package com.meshlit.chat

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

@Serializable enum class SpeechKind { WHISPER_STT, PIPER_TTS }
@Serializable data class SpeechFile(val path:String,val bytes:Long,val sha256:String)
@Serializable data class SpeechPack(val format:String="meshlit-voice-pack/1",val id:String,val name:String,
    val kind:SpeechKind,val language:String,val license:String,val source:String,val files:List<SpeechFile>) {
    fun validate() {
        require(format=="meshlit-voice-pack/1" && id.matches(Regex("[a-z0-9-]{1,80}")))
        require(name.length in 1..100 && language.length in 1..40 && license.length in 1..200 && source.length in 1..500)
        require(files.size in 2..512 && files.map{it.path}.distinct().size==files.size)
        require(files.all{safeSpeechPath(it.path) && it.bytes in 1..MAX_SPEECH_PACK && it.sha256.matches(Regex("[a-f0-9]{64}"))})
        require(files.sumOf{it.bytes}<=MAX_SPEECH_PACK)
        val names=files.map{it.path}
        require(names.any{it.endsWith("tokens.txt")}) { "Speech pack needs tokens.txt" }
        if(kind==SpeechKind.WHISPER_STT) { require(language=="en") { "Current offline recognition adapter supports English" };require(names.any{it.endsWith("encoder.onnx")} && names.any{it.endsWith("decoder.onnx")}) { "Whisper needs encoder and decoder ONNX files" } }
        else require(names.any{it.endsWith(".onnx")} && names.any{it.startsWith("espeak-ng-data/")}) { "Piper needs ONNX weights and espeak-ng-data" }
    }
}
internal const val MAX_SPEECH_PACK=512L*1024*1024
internal fun safeSpeechPath(path:String)=path.length in 1..240 && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') && path.split('/').all{it.isNotBlank() && it!="." && it!=".."} && !path.endsWith(".so") && !path.endsWith(".sh")
internal fun speechDigest(file:File):String {
    val digest=MessageDigest.getInstance("SHA-256")
    file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0) break;digest.update(b,0,n)}}
    return digest.digest().joinToString(""){"%02x".format(it)}
}
/** Local model ZIPs have a manifest first, exact file allowlist, sizes and checksums.
 * Imports never download, execute code, replace an installed pack or change the chat model. */
internal suspend fun unpackSpeech(input:InputStream,stage:File):SpeechPack {
    stage.mkdirs()
    try {
        ZipInputStream(BufferedInputStream(input)).use{zip->
            val first=zip.nextEntry ?: error("Empty voice pack")
            require(first.name=="manifest.json" && !first.isDirectory) { "manifest.json must be the first ZIP entry" }
            val data=ByteArrayOutputStream();val buffer=ByteArray(65536)
            while(true){currentCoroutineContext().ensureActive();val n=zip.read(buffer);if(n<0)break;require(data.size()+n<=128*1024);data.write(buffer,0,n)}
            val pack=Json.decodeFromString<SpeechPack>(data.toString("UTF-8"));pack.validate()
            val expected=pack.files.associateBy{it.path};val seen=mutableSetOf<String>()
            var total=0L
            while(true){currentCoroutineContext().ensureActive();val entry=zip.nextEntry ?: break
                require(!entry.isDirectory && safeSpeechPath(entry.name) && seen.add(entry.name)) { "Unsafe or duplicate voice file" }
                val spec=expected[entry.name] ?: error("Unlisted file in voice pack")
                val target=File(stage,entry.name);require(target.canonicalPath.startsWith(stage.canonicalPath+File.separator));target.parentFile!!.mkdirs()
                var count=0L
                FileOutputStream(target).use{output->while(true){currentCoroutineContext().ensureActive();val n=zip.read(buffer);if(n<0)break;count+=n;total+=n;require(count<=spec.bytes && total<=MAX_SPEECH_PACK);output.write(buffer,0,n)};output.fd.sync()}
                require(count==spec.bytes && speechDigest(target)==spec.sha256) { "Voice file size or SHA-256 mismatch" }
            }
            require(seen==expected.keys) { "Incomplete voice pack" }
            File(stage,"manifest.json").writeText(Json.encodeToString(SpeechPack.serializer(),pack))
            return pack
        }
    } catch(e:Throwable){stage.deleteRecursively();throw e}
}
class VoiceModels(private val context:Context) {
    private val root=File(context.filesDir,"speech-models").apply{mkdirs();listFiles().orEmpty().filter{it.name.startsWith(".import-")}.forEach{it.deleteRecursively()}}
    private val lock=Mutex()
    private val _models=MutableStateFlow(scan());val models=_models.asStateFlow()
    private fun scan()=root.listFiles().orEmpty().filter{it.isDirectory && !it.name.startsWith('.')}.mapNotNull{dir->runCatching{Json.decodeFromString<SpeechPack>(File(dir,"manifest.json").readText()).also{it.validate();require(it.id==dir.name)}}.getOrNull()}.take(8)
    suspend fun install(uri:Uri)=withContext(Dispatchers.IO){lock.withLock{
        require(uri.scheme=="content");require(models.value.size<8) { "At most eight offline voice packs" }
        require(root.usableSpace>MAX_SPEECH_PACK+32L*1024*1024) { "Import requires 544 MiB of free staging space" }
        val stage=File(root,".import-${UUID.randomUUID()}")
        try{val pack=unpackSpeech(context.contentResolver.openInputStream(uri) ?: error("File grant unavailable"),stage)
            val target=File(root,pack.id);require(!target.exists()){ "Remove the installed pack before replacing it" };check(stage.renameTo(target));_models.value=scan();pack
        }finally{stage.deleteRecursively()}
    }}
    suspend fun verified(id:String):Pair<SpeechPack,File> =withContext(Dispatchers.IO){lock.withLock{
        val pack=models.value.singleOrNull{it.id==id} ?: error("Install and select an offline speech pack first")
        val dir=File(root,pack.id)
        pack.files.forEach{currentCoroutineContext().ensureActive();val f=File(dir,it.path);require(f.isFile && f.length()==it.bytes && speechDigest(f)==it.sha256) { "Installed speech pack was changed or is incomplete" }}
        pack to dir
    }}
    suspend fun remove(id:String)=withContext(Dispatchers.IO){lock.withLock{
        require(models.value.any{it.id==id});check(File(root,id).deleteRecursively());_models.value=scan()
    }}
}
