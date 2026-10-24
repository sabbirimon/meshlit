package com.meshlit.core.inference.models
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

@Serializable data class RemoteVideoJob(val id:String,val status:String,val progress:Int?=null,val error:String?=null)

/** Explicit OpenAI-format media APIs. Compatible servers must actually implement
 * these endpoints. No assumed entitlement, response substitution, retry or redirect. */
class OnlineMediaClient(private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
    .readTimeout(180,TimeUnit.SECONDS).callTimeout(240,TimeUnit.SECONDS).retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false).build()) {
    private fun request(p:OnlineProfile,key:String,path:String):Request.Builder {
        p.validate();require(p.enabled){"Enable the provider profile first"}
        require(p.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE)){"This media adapter requires OpenAI-format endpoints"}
        require(!p.requiresApiKey || key.isNotBlank() && key.length<=4096 && key.none{it.isWhitespace()}){"Save a provider API key first"}
        return Request.Builder().url(p.endpoint.trimEnd('/')+"/"+path).apply{if(p.requiresApiKey) header("Authorization","Bearer $key")}
    }
    private fun model(model:String){require(model.matches(Regex("[A-Za-z0-9_./:-]{1,160}"))){"Enter a supported media model ID"}}
    private fun prompt(text:String){require(text.isNotBlank() && text.length<=4000){"Enter 1–4000 characters"}}
    private suspend fun <T> exchange(request:Request,consume:(Response)->T):T=withContext(Dispatchers.IO){coroutineScope{
        val call=client.newCall(request)
        val watcher=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
        try {call.execute().use{r->check(r.isSuccessful){"Media provider HTTP ${r.code}; verify model, API access and billing"};consume(r)}}
        catch (error:IOException) {currentCoroutineContext().ensureActive();throw error}
        finally{watcher.cancel()}
    }}
    private fun json(response:Response,limit:Long=12L*1024*1024):JsonObject {
        val body=response.body ?: error("Empty media response");require(body.contentLength()<=limit)
        val source=body.source();source.request(limit+1);require(source.buffer.size<=limit){"Media response too large"}
        return Json.parseToJsonElement(source.readUtf8()).jsonObject
    }
    private fun post(p:OnlineProfile,key:String,path:String,payload:JsonObject)=request(p,key,path)
        .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
    suspend fun image(p:OnlineProfile,key:String,model:String,prompt:String,target:File):File {
        model(model);prompt(prompt)
        val result=exchange(post(p,key,"images/generations",buildJsonObject{put("model",model);put("prompt",prompt);put("n",1)})){json(it)}
        val encoded=result["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("b64_json")?.jsonPrimitive?.content
            ?: error("Provider returned no inline image; URL-only downloads require a separate approved adapter")
        val bytes=encoded.decodeBase64()?.toByteArray() ?: error("Invalid image encoding");require(bytes.size<=8*1024*1024 && bytes.size>=8)
        require(bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))){"Expected a PNG image"}
        return withContext(Dispatchers.IO){installBytes(target,bytes)}
    }
    suspend fun speech(p:OnlineProfile,key:String,model:String,text:String,voice:String,target:File):File {
        model(model);prompt(text);require(voice.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        return download(post(p,key,"audio/speech",buildJsonObject{put("model",model);put("input",text);put("voice",voice);put("response_format","wav")}),target,16L*1024*1024,"wav")
    }
    suspend fun transcribe(p:OnlineProfile,key:String,model:String,wav:ByteArray):String {
        model(model);require(wav.size in 44..1_000_000)
        require(wav.copyOfRange(0,4).toString(Charsets.US_ASCII)=="RIFF" && wav.copyOfRange(8,12).toString(Charsets.US_ASCII)=="WAVE")
        val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model",model)
            .addFormDataPart("file","turn.wav",wav.toRequestBody("audio/wav".toMediaType())).build()
        val result=exchange(request(p,key,"audio/transcriptions").post(body).build()){json(it,128*1024)}
        return result["text"]?.jsonPrimitive?.content?.trim()?.takeIf{it.isNotEmpty() && it.length<=12000}
            ?: error("Provider returned no usable transcript")
    }
    suspend fun vision(p:OnlineProfile,key:String,jpeg:ByteArray,prompt:String):String = visionReply(p,key,jpeg,prompt).text
    suspend fun visionReply(p:OnlineProfile,key:String,jpeg:ByteArray,prompt:String):OnlineReply {
        model(p.model);prompt(prompt);require(jpeg.size in 4..2*1024*1024 && jpeg[0]==(-1).toByte() && jpeg[1]==(-40).toByte())
        val payload=buildJsonObject {
            put("model",p.model);put(if(p.protocol==OnlineProtocol.OPENAI) "max_completion_tokens" else "max_tokens",512)
            put("messages",buildJsonArray{add(buildJsonObject{put("role","user");put("content",buildJsonArray{
                add(buildJsonObject{put("type","text");put("text",prompt)})
                add(buildJsonObject{put("type","image_url");put("image_url",buildJsonObject{put("url","data:image/jpeg;base64,"+jpeg.toByteString().base64())})})
            })})})
        }
        val data=exchange(post(p,key,"chat/completions",payload)){json(it,4L*1024*1024)}
        val text=data["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?.takeIf{it.isNotBlank() && it.length<=64000} ?: error("Provider returned no usable vision text; verify vision model support")
        val usage=data["usage"] as? JsonObject
        fun count(name:String)=(usage?.get(name) as? JsonPrimitive)?.longOrNull?.takeIf{it>=0}
        return OnlineReply(text,count("prompt_tokens"),count("completion_tokens"))
    }
    suspend fun startVideo(p:OnlineProfile,key:String,model:String,prompt:String,seconds:Int=4):RemoteVideoJob {
        model(model);prompt(prompt);require(seconds in setOf(4,8,12))
        val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("model",model)
            .addFormDataPart("prompt",prompt).addFormDataPart("size","1280x720").addFormDataPart("seconds",seconds.toString()).build()
        return exchange(request(p,key,"videos").post(body).build()){video(json(it,1024*1024))}
    }
    private fun video(data:JsonObject):RemoteVideoJob {
        val id=data["id"]?.jsonPrimitive?.content ?: error("Missing remote video ID");videoId(id)
        val status=data["status"]?.jsonPrimitive?.content ?: error("Missing video status")
        require(status.length<=80)
        return RemoteVideoJob(id,status,data["progress"]?.jsonPrimitive?.intOrNull?.takeIf{it in 0..100},data["error"]?.toString()?.take(1000))
    }
    private fun videoId(id:String){require(id.matches(Regex("[A-Za-z0-9_-]{1,160}")))}
    suspend fun videoStatus(p:OnlineProfile,key:String,id:String):RemoteVideoJob {
        videoId(id);return exchange(request(p,key,"videos/$id").build()){video(json(it,1024*1024))}
    }
    suspend fun videoContent(p:OnlineProfile,key:String,id:String,target:File):File {
        videoId(id);return download(request(p,key,"videos/$id/content").build(),target,64L*1024*1024,"mp4")
    }
    private fun installBytes(target:File,bytes:ByteArray):File {
        target.parentFile!!.mkdirs();require(target.parentFile!!.usableSpace>bytes.size+16L*1024*1024){"Insufficient media storage"}
        val part=File(target.path+".part")
        try {FileOutputStream(part).use{it.write(bytes);it.fd.sync()};check(part.renameTo(target));return target}
        finally{part.delete()}
    }
    private suspend fun download(request:Request,target:File,limit:Long,format:String):File {
        val context=currentCoroutineContext()
        return exchange(request){response->
        target.parentFile!!.mkdirs();require(target.parentFile!!.usableSpace>limit+16L*1024*1024){"Insufficient media storage"}
        val body=response.body ?: error("Empty media content");require(body.contentLength()<=limit)
        val part=File(target.path+".part")
        try {
            body.byteStream().use{input->FileOutputStream(part).use{output->
                val buffer=ByteArray(64*1024);var count=0L
                while(true){context.ensureActive();val n=input.read(buffer);if(n<0) break;count+=n;require(count<=limit){"Media exceeds limit"};output.write(buffer,0,n)}
                output.fd.sync()
            }}
            val header=ByteArray(12);require(part.inputStream().use{it.read(header)}==12){"Incomplete media"}
            require(if(format=="wav") header.copyOfRange(0,4).toString(Charsets.US_ASCII)=="RIFF" && header.copyOfRange(8,12).toString(Charsets.US_ASCII)=="WAVE"
                else header.copyOfRange(4,8).toString(Charsets.US_ASCII)=="ftyp"){"Unexpected media container"}
            check(part.renameTo(target));target
        } finally{part.delete()}
        }
    }
}
