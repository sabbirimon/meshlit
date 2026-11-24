package com.meshlit.media

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.inference.CpuWorkGate
import com.meshlit.core.inference.models.*
import com.meshlit.operations.OperationsControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Serializable data class LocalVisionModel(val id:String,val label:String,val modelSha:String,val projectorSha:String,val modelBytes:Long,val projectorBytes:Long) {
    fun validate(){
        require(UUID.fromString(id).toString()==id && label.length in 1..80 && label.none(Char::isISOControl))
        require(modelSha.matches(Regex("[a-f0-9]{64}")) && projectorSha.matches(Regex("[a-f0-9]{64}")) && modelSha!=projectorSha)
        require(modelBytes in 24..LocalVisionHost.MAX_MODEL && projectorBytes in 24..LocalVisionHost.MAX_PROJECTOR)
    }
}

/** One-shot native vision runner. Image bytes and the process remain on this installation. */
class LocalVisionHost(private val context:Context) {
    private val gate=OperationsControl.get(context).gate
    private val root=File(context.noBackupFilesDir,"vision-pair-v1").apply{check(mkdirs() || isDirectory)}
    private val manifest=AtomicFile(File(root,"selected.json"))
    private val mutex=Mutex()
    private val executable get()=File(context.applicationInfo.nativeLibraryDir,"libmeshlit_pipeline_server.so")
    private val mutable=MutableStateFlow(runCatching{
        if(!manifest.baseFile.exists())null else manifest.openRead().use{input->
            val bytes=com.meshlit.util.readBounded(input,4096)
            Json.decodeFromString<LocalVisionModel>(bytes.toString(Charsets.UTF_8)).also{it.validate()}
        }
    }.getOrNull())
    val model=mutable.asStateFlow()
    fun available()=executable.isFile && executable.canExecute()

    private suspend fun importFile(uri:Uri,target:File,max:Long):Long {
        require(uri.scheme=="content"){"Use the Android file picker for both GGUF files"}
        val input=context.contentResolver.openInputStream(uri) ?: error("File permission is unavailable")
        var size=0L
        input.use { source -> FileOutputStream(target).use{output->
            val buffer=ByteArray(256*1024)
            while(true){currentCoroutineContext().ensureActive();val n=source.read(buffer);if(n<0)break;size+=n
                require(size<=max){"Selected GGUF exceeds the size limit"};output.write(buffer,0,n)}
            output.fd.sync()
        } }
        ModelFiles.validateGguf(target)
        return size
    }
    suspend fun install(label:String,modelUri:Uri,projectorUri:Uri):LocalVisionModel=gate.run(ManagedFeature.MEDIA) {
        gate.run(ManagedFeature.MODEL_TRANSFERS){mutex.withLock{withContext(Dispatchers.IO){
            require(available()){ "Native vision runtime is unavailable for this Android ABI" }
            require(modelUri!=projectorUri){"Choose separate model and projector files"}
            val title=label.trim();require(title.length in 1..80 && title.none(Char::isISOControl))
            val id=UUID.randomUUID().toString();val directory=File(root,id);check(directory.mkdir())
            var committed=false
            try {
                val modelFile=File(directory,"model.gguf");val projector=File(directory,"projector.gguf")
                val modelBytes=importFile(modelUri,modelFile,MAX_MODEL);val projectorBytes=importFile(projectorUri,projector,MAX_PROJECTOR)
                val item=LocalVisionModel(id,title,ModelFiles.sha256(modelFile),ModelFiles.sha256(projector),modelBytes,projectorBytes).also{it.validate()}
                currentCoroutineContext().ensureActive()
                val encoded=Json.encodeToString(item).toByteArray(Charsets.UTF_8)
                val output=manifest.startWrite()
                try{output.write(encoded);manifest.finishWrite(output)}catch(e:Exception){manifest.failWrite(output);throw e}
                check(manifest.openRead().use{com.meshlit.util.readBounded(it,4096)}.contentEquals(encoded)){"Vision manifest was not saved"}
                val previous=mutable.value;mutable.value=item;committed=true
                previous?.takeIf{it.id!=id}?.let{File(root,it.id).deleteRecursively()}
                item
            }finally{if(!committed)directory.deleteRecursively()}
        }}}
    }
    suspend fun clear()=mutex.withLock{withContext(Dispatchers.IO){
        val previous=mutable.value
        manifest.delete();check(!manifest.baseFile.exists()){"Could not remove the offline vision manifest"};mutable.value=null
        previous?.let{check(File(root,it.id).deleteRecursively()){ "Could not remove the imported GGUF files" }}
    }}
    suspend fun analyze(expected:LocalVisionModel,jpeg:ByteArray,prompt:String):OnlineReply =
        withSession(expected) { describe -> describe(jpeg,prompt) }

    internal suspend fun <T> withSession(
        expected: LocalVisionModel,
        use: suspend (describe: suspend (ByteArray, String) -> OnlineReply) -> T,
    ): T = gate.run(ManagedFeature.MEDIA) {
        gate.run(ManagedFeature.INFERENCE) {mutex.withLock {CpuWorkGate.run("Offline photo") {withContext(Dispatchers.IO) {
            check(available() && mutable.value==expected){"Offline vision model changed or the native runtime is unavailable"}
            expected.validate()
            val directory=File(root,expected.id);val modelFile=File(directory,"model.gguf");val projector=File(directory,"projector.gguf")
            ModelFiles.validateGguf(modelFile);ModelFiles.validateGguf(projector)
            require(modelFile.length()==expected.modelBytes && projector.length()==expected.projectorBytes &&
                ModelFiles.sha256(modelFile)==expected.modelSha && ModelFiles.sha256(projector)==expected.projectorSha){"Vision files changed; import the model and projector again"}
            val memory=ActivityManager.MemoryInfo().also{(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)}
            require(!memory.lowMemory && offlineVisionFits(expected.modelBytes,expected.projectorBytes,memory.availMem)){
                "Not enough free memory for this vision pair. Unload the chat model or import a smaller pair."
            }
            com.meshlit.power.PowerRepository(context).loadBlockReason()?.let{error(it)}
            val port=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use{it.localPort}
            val token=ByteArray(32).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
            val key=File.createTempFile("vision-key-",".tmp",root)
            var process:Process?=null
            var logDrain:Thread?=null
            val http=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).connectTimeout(2,TimeUnit.SECONDS).readTimeout(120,TimeUnit.SECONDS)
                .callTimeout(150,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
            try {
                key.writeText(token+"\n")
                val args=listOf(executable.absolutePath,"-m",modelFile.absolutePath,"--mmproj",projector.absolutePath,
                    "--host","127.0.0.1","--port",port.toString(),"--api-key-file",key.absolutePath,"--alias","meshlit-vision",
                    "-c","2048","-t",com.meshlit.models.AccelerationPreferences(context).effectiveThreads().coerceIn(1,4).toString(),
                    "--parallel","1","--device","none","-ngl","0","--no-mmproj-offload","--fit","off","--no-agent","--no-webui","--no-warmup","--offline")
                val child=ProcessBuilder(args).directory(directory).redirectErrorStream(true)
                    .apply{environment().keys.removeAll{it.startsWith("LLAMA_") || it.startsWith("GGML_") || it.startsWith("HF_")}}.start()
                process=child
                val base="http://127.0.0.1:$port"
                val bound=AtomicBoolean()
                // Read only this child's bounded startup line; an unrelated HTTP service
                // racing for the ephemeral port must never receive an image or key.
                logDrain=thread(name="meshlit-vision-log",isDaemon=true) {
                    runCatching { child.inputStream.bufferedReader().use { reader ->
                        val line=StringBuilder(192)
                        while(true) {
                            val char=reader.read();if(char<0)break
                            if(char==10) {
                                if(line.trimEnd().endsWith("listening on $base"))bound.set(true)
                                line.setLength(0)
                            }else if(line.length<256)line.append(char.toChar())
                        }
                    } }
                }
                val health=http.newBuilder().callTimeout(2,TimeUnit.SECONDS).build()
                withTimeout(75_000){
                    while(true){
                        currentCoroutineContext().ensureActive()
                        check(processAlive(child)){"Vision runtime stopped loading. Check that the model and projector match."}
                        if(bound.get() && runCatching{health.newCall(Request.Builder().url("$base/health").build()).execute().use{it.code==200}}.getOrDefault(false))break
                        delay(400)
                    }
                }
                val route=OnlineProfile("offline-vision","On-device vision","$base/v1","meshlit-vision",enabled=true)
                val client = OnlineMediaClient(http)
                use { jpeg, prompt ->
                    currentCoroutineContext().ensureActive()
                    check(processAlive(child)) { "The on-device vision process stopped" }
                    client.visionReply(route,token,jpeg,prompt).also { currentCoroutineContext().ensureActive() }
                }
            } finally {
                withContext(NonCancellable+Dispatchers.IO){
                    try {
                        process?.let{child->
                            child.destroy()
                            if(!waitForVisionExit(child)) {
                                if(Build.VERSION.SDK_INT>=26)child.destroyForcibly() else child.destroy()
                                check(waitForVisionExit(child)){"The native vision process did not stop"}
                            }
                        }
                    }finally{
                        runCatching{process?.inputStream?.close()};logDrain?.join(500)
                        http.dispatcher.cancelAll();http.connectionPool.evictAll();check(key.delete() || !key.exists()){ "Could not remove vision session key" }
                    }
                }
            }
        }}}}
    }
    companion object {
        const val MAX_MODEL=2L*1024*1024*1024
        const val MAX_PROJECTOR=512L*1024*1024
    }
}

internal fun offlineVisionFits(modelBytes:Long,projectorBytes:Long,availableBytes:Long):Boolean {
    require(modelBytes in 24..LocalVisionHost.MAX_MODEL && projectorBytes in 24..LocalVisionHost.MAX_PROJECTOR)
    val estimated=(modelBytes+projectorBytes)*13/10+512L*1024*1024
    return availableBytes>0 && estimated<=(availableBytes*0.72).toLong()
}

private fun processAlive(child:Process)=try{child.exitValue();false}catch(_:IllegalThreadStateException){true}
private suspend fun waitForVisionExit(child:Process)=withTimeoutOrNull(2_000){while(processAlive(child))delay(50);true}==true
