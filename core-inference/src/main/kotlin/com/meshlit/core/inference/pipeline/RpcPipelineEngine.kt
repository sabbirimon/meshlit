package com.meshlit.core.inference.pipeline

import com.meshlit.core.common.*
import com.meshlit.core.inference.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Adapter for a verified loopback llama-server launched with --split-mode layer.
 * Startup/stop belongs to the app host; this engine does not claim to implement
 * missing JNI layer APIs or replace SDK local inference. */
class RpcPipelineEngine(private val port:Int,private val credential:String,private val modelPath:String,
    override val engineTag:String="llama-rpc-layer",private val onStop:suspend()->Unit):InferenceEngine {
    @Volatile private var info:ModelInfo?=null
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(120,TimeUnit.SECONDS)
        .callTimeout(180,TimeUnit.SECONDS).followRedirects(false).build()
    override fun isReady()=info!=null
    override fun loadedModel()=info
    override suspend fun loadModel(request:ModelLoadRequest):MeshlitResult<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            require(request.modelPath==modelPath) {"Pipeline model changed; restart its native host"}
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/health").header("Authorization","Bearer $credential").build())
                .execute().use { require(it.code==200) {"Pipeline is not ready"} }
            val properties=client.newCall(Request.Builder().url("http://127.0.0.1:$port/props").header("Authorization","Bearer $credential").build()).execute().use { response ->
                require(response.code==200) { "Native runtime did not report effective model settings" }
                val body=requireNotNull(response.body);require(body.contentLength()<=1024*1024)
                val source=body.source();require(!source.request(1024*1024+1L)) { "Native properties exceed size limit" }
                Json.parseToJsonElement(source.readUtf8()).jsonObject
            }
            val actualContext=properties["default_generation_settings"]?.jsonObject?.get("n_ctx")?.jsonPrimitive?.intOrNull
                ?: error("Native runtime did not report context capacity")
            require(actualContext==request.contextSize) { "Native context differs from requested size ($actualContext versus ${request.contextSize})" }
            val file=File(modelPath)
            val actualPath=properties["model_path"]?.jsonPrimitive?.contentOrNull ?: error("Native model path is missing")
            require(File(actualPath).canonicalFile==file.canonicalFile) { "Native runtime loaded another model" }
            val quant=com.meshlit.core.inference.models.GgufMetadata.read(file).quantization ?: "Unknown"
            val loaded=ModelInfo(file.absolutePath,file.nameWithoutExtension,actualContext,0,quant,0,file.length(),System.currentTimeMillis())
            info=loaded;MeshlitResult.Success(loaded)
        } catch(e:CancellationException){throw e}
        catch(e:Exception){info=null;MeshlitResult.Failure(MeshlitError.Native("pipeline.load:${e.message}",e))}
    }
    override suspend fun unloadModel(){info=null;onStop()}
    override suspend fun infer(request:InferenceRequest):MeshlitResult<InferenceResult> = withContext(Dispatchers.IO) {
        if(info==null) return@withContext MeshlitResult.Failure(MeshlitError.Invalid("pipeline.not_loaded"))
        val started=System.nanoTime();val output=StringBuilder();var tokens:Int?=null;var promptTokens:Int?=null;var rate:Float?=null;var reason=FinishReason.NATURAL_STOP;var cached:Int?=null
        val payload=buildJsonObject {
            put("prompt",request.prompt);put("n_predict",request.maxTokens);put("temperature",request.temperature)
            put("top_p",request.topP);put("top_k",request.topK);put("repeat_penalty",request.repeatPenalty)
            put("seed",request.seed);put("stream",true);put("cache_prompt",request.reuseContext)
            put("stop",buildJsonArray{request.stopSequences.forEach {add(it)}})
        }
        val call=client.newCall(Request.Builder().url("http://127.0.0.1:$port/completion")
            .header("Authorization","Bearer $credential").post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        try {
            coroutineScope {
                val watcher=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
                try {
                    call.execute().use { response ->
                        require(response.code==200){"Pipeline request failed (HTTP ${response.code})"}
                        val source=requireNotNull(response.body).source()
                        var finished=false
                        while(!source.exhausted()) {
                            currentCoroutineContext().ensureActive()
                            val line=source.readUtf8LineStrict(65536)
                            if(!line.startsWith("data:")) continue
                            val data=line.removePrefix("data:").trim()
                            if(data=="[DONE]") {finished=true;break}
                            val chunk=Json.parseToJsonElement(data).jsonObject
                            require("error" !in chunk){"Native pipeline reported an error"}
                            val content=chunk["content"]?.jsonPrimitive?.content.orEmpty()
                            if(content.isNotEmpty()){output.append(content);request.onToken(content)}
                            if(chunk["stop"]?.jsonPrimitive?.booleanOrNull==true){
                                // tokens_cached is final slot occupancy; timings.cache_n is reused prompt prefix.
                                cached=chunk["timings"]?.jsonObject?.get("cache_n")?.jsonPrimitive?.intOrNull?.takeIf{it>=0}
                                tokens=chunk["tokens_predicted"]?.jsonPrimitive?.intOrNull?.takeIf{it>=0}
                                promptTokens=chunk["tokens_evaluated"]?.jsonPrimitive?.intOrNull?.takeIf{it>=0}
                                rate=chunk["timings"]?.jsonObject?.get("predicted_per_second")?.jsonPrimitive?.floatOrNull?.takeIf{it.isFinite() && it>=0}
                                reason=when {
                                    chunk["stop_type"]?.jsonPrimitive?.contentOrNull=="limit" || chunk["stopped_limit"]?.jsonPrimitive?.booleanOrNull==true->FinishReason.MAX_TOKENS
                                    chunk["stop_type"]?.jsonPrimitive?.contentOrNull=="word" || chunk["stopped_word"]?.jsonPrimitive?.booleanOrNull==true->FinishReason.STOP_SEQUENCE
                                    else->FinishReason.NATURAL_STOP
                                }
                                finished=true;break
                            }
                        }
                        require(finished){"Pipeline stream ended before completion"}
                    }
                } finally {watcher.cancel()}
            }
            val duration=(System.nanoTime()-started)/1_000_000
            val result=InferenceResult(promptTokens,tokens,duration,rate,
                reason,output.toString(),cached)
            request.onComplete(result);MeshlitResult.Success(result)
        } catch(e:CancellationException){throw e}
        catch(e:Exception){MeshlitResult.Failure(MeshlitError.Network("pipeline.infer:${e.message}",e))}
    }
}
