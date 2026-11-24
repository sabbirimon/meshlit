package com.meshlit.core.inference.models

import com.meshlit.core.inference.speechOutputPcm16
import ai.runanywhere.proto.v1.AudioFormat
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class NaturalVoice(val id:String,val name:String)
data class NaturalEngine(val id:String,val name:String,val ready:Boolean,val voices:List<NaturalVoice>,val license:String="Unreported")
data class NaturalAudio(val bytes:ByteArray,val rate:Int)

/** OpenLive runs on an owner-configured host. ElevenLabs is a separate paid API.
 * Neither adapter installs models, follows redirects, falls back or receives mic audio. */
class NaturalSpeechClient(private val client:OkHttpClient=OkHttpClient.Builder()
    .connectTimeout(15,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS).callTimeout(240,TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    private val authorize:suspend(OnlineProfile,String)->Unit={_,_->}) {
    private class Authorization(val check:suspend()->Unit)
    internal fun request(p:OnlineProfile,key:String,eleven:Boolean,path:String,body:JsonObject?=null):Request {
        p.validate();require(p.enabled)
        require(p.protocol in setOf(OnlineProtocol.OPENAI,OnlineProtocol.OPENAI_COMPATIBLE))
        require(key.length<=4096 && key.none{it.isWhitespace()})
        require(!p.requiresApiKey || key.isNotBlank()) { "Save the speech connection credential" }
        val url=p.endpoint.toHttpUrl()
        if(eleven)require(url.isHttps && url.host=="api.elevenlabs.io" && url.port==443 && url.encodedPath.trimEnd('/')=="/v1" && p.requiresApiKey && key.isNotBlank()) { "ElevenLabs requires https://api.elevenlabs.io/v1 and a saved API key" }
        val builder=Request.Builder().url(p.endpoint.trimEnd('/')+"/"+path).tag(Authorization::class.java,Authorization{authorize(p,key)})
        if(key.isNotBlank())builder.header(if(eleven) "xi-api-key" else "Authorization",if(eleven) key else "Bearer $key")
        if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        return builder.build()
    }
    private suspend fun <T> exchange(request:Request,limit:Int,consume:(Response,ByteArray)->T):T=withContext(Dispatchers.IO){coroutineScope{
        request.tag(Authorization::class.java)?.check?.invoke()
        val call=client.newCall(request)
        val cancellation=launch{try{awaitCancellation()}finally{call.cancel()}}
        try {call.execute().use{r->
            check(r.isSuccessful){"Speech server HTTP ${r.code}; check installation, voice, credentials and account access"}
            val body=r.body ?: error("Empty speech response");require(body.contentLength()<=limit)
            val source=body.source();source.request(limit.toLong()+1);require(source.buffer.size<=limit){"Speech response exceeds limit"}
            consume(r,source.readByteArray())
        }}catch(e:IOException){currentCoroutineContext().ensureActive();throw e}finally{cancellation.cancel()}
    }}
    suspend fun engines(p:OnlineProfile,key:String):List<NaturalEngine> = exchange(request(p,key,false,"engines"),2*1024*1024){_,bytes->
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonArray.flatMap{entry->
            val family=entry.jsonObject
            if(family["family"]?.jsonPrimitive?.content !in setOf("kokoro-native","pocket"))emptyList()
            else family["variants"]!!.jsonArray.map{v->val item=v.jsonObject
                NaturalEngine(item["id"]!!.jsonPrimitive.content,item["name"]!!.jsonPrimitive.content,
                    item["installed"]?.jsonPrimitive?.booleanOrNull==true && item["runnable"]?.jsonPrimitive?.booleanOrNull==true,
                    item["voices"]?.jsonArray.orEmpty().map{val voice=it.jsonObject;NaturalVoice(voice["id"]!!.jsonPrimitive.content,voice["name"]!!.jsonPrimitive.content)},item["license"]?.jsonPrimitive?.content ?: "Unreported")
            }
        }.take(32)
    }
    suspend fun elevenVoices(p:OnlineProfile,key:String):List<NaturalVoice> = exchange(request(p,key,true,"voices"),2*1024*1024){_,bytes->
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["voices"]!!.jsonArray.take(256).map{val v=it.jsonObject;NaturalVoice(v["voice_id"]!!.jsonPrimitive.content,v["name"]!!.jsonPrimitive.content)}
    }
    internal fun checkFreeAllowance(account:JsonObject,characters:Int) {
        require(characters in 1..4000)
    check(account["tier"]?.jsonPrimitive?.content=="free"){"Free-tier-only mode requires an ElevenLabs Free account"}
    val used=account["character_count"]?.jsonPrimitive?.longOrNull ?: error("Provider did not report speech usage")
    val limit=account["character_limit"]?.jsonPrimitive?.longOrNull ?: error("Provider did not report a speech allowance")
    check(used>=0 && limit>=used && characters<=limit-used){"Insufficient provider-reported free voice allowance"}
    }
    suspend fun requireElevenFreeAllowance(p:OnlineProfile,key:String,characters:Int) {
        require(characters in 1..4000)
        exchange(request(p,key,true,"user/subscription"),128*1024){_,bytes->
            checkFreeAllowance(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject,characters)
        }
    }
    suspend fun synthesize(p:OnlineProfile,key:String,eleven:Boolean,engine:String,voice:String,text:String,speed:Float=1f,requireFreeTier:Boolean=true):NaturalAudio {
        require(text.isNotBlank() && text.length<=4000 && speed.isFinite() && speed in 0.5f..2f)
        require(voice.matches(Regex("[A-Za-z0-9_-]{1,80}")) && engine.matches(Regex("[A-Za-z0-9_.-]{1,160}")))
        if(eleven && requireFreeTier)requireElevenFreeAllowance(p,key,text.length)
        val payload=if(eleven)buildJsonObject{put("text",text);put("model_id",engine)} else buildJsonObject{put("text",text);put("engine",engine);put("voice",voice);put("speed",speed);put("lang","en")}
        val route=if(eleven) "text-to-speech/$voice?output_format=pcm_22050" else "tts"
        return exchange(request(p,key,eleven,route,payload),16*1024*1024){response,bytes->
            require(response.header("Content-Type")?.substringBefore(';') in setOf("application/octet-stream","audio/pcm")){"Speech server must return raw PCM"}
            val rate=if(eleven)22050 else response.header("x-sample-rate")?.toIntOrNull() ?: error("Speech server omitted the sample rate")
            NaturalAudio(speechOutputPcm16(bytes,if(eleven)AudioFormat.AUDIO_FORMAT_PCM_S16LE else AudioFormat.AUDIO_FORMAT_PCM,rate),rate)
        }
    }
}
