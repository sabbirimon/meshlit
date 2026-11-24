package com.meshlit.core.mcp.integrations

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Original Meshlit API adapter. No upstream service implementation or installer is bundled. */
class CompanionHttp(address:String,private val secret:String="",private val apiKey:Boolean=false,client:OkHttpClient=OkHttpClient()) {
    val base=address.trim().trimEnd('/').toHttpUrl().also{url->
        require(address.length<=2048 && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
        require(url.isHttps || (url.scheme=="http" && url.host in setOf("127.0.0.1","::1"))){"Use HTTPS or literal loopback HTTP"}
        require(!url.encodedPath.contains('%') && url.pathSegments.none{it=="." || it==".."})
        require(secret.length<=8192 && secret.none(Char::isISOControl))
        require(url.host in setOf("127.0.0.1","::1") || secret.isNotBlank()){"A remote companion requires authentication"}
    }
    private val http=client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(20,TimeUnit.SECONDS).build()
    suspend fun request(path:String,body:JsonObject?=null,query:Map<String,String> = emptyMap()):JsonElement {
        require(path.matches(Regex("/[A-Za-z0-9_/-]{1,200}")))
        val url=base.newBuilder().encodedPath(base.encodedPath.trimEnd('/')+path).apply{query.forEach{(key,value)->addQueryParameter(key,value)}}.build()
        val request=Request.Builder().url(url).header("Accept","application/json").apply{
            if(secret.isNotEmpty())header(if(apiKey)"X-API-Key" else "Authorization",if(apiKey)secret else "Bearer $secret")
            if(body!=null)post(body.toString().also{require(it.length<=32000)}.toRequestBody("application/json".toMediaType()))
        }.build()
        return suspendCancellableCoroutine{continuation->
            val call=http.newCall(request);continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("Companion request failed"))}
                override fun onResponse(call:Call,response:Response){
                    val result=runCatching{response.use{
                        require(it.isSuccessful){"Companion rejected request (${it.code})"}
                        require(it.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()=="application/json"){"Companion did not return JSON"}
                        val bytes=it.body.byteStream().use{stream->
                            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                            while(true){val count=stream.read(buffer);if(count<0)break;require(output.size()+count<=262144){"Companion response exceeds limit"};output.write(buffer,0,count)}
                            output.toByteArray()
                        }
                        val text=bytes.decodeToString(throwOnInvalidSequence=true)
                        var depth=0;var quoted=false;var escaped=false
                        text.forEach{c->if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=24)};']','}'->depth--}}
                        Json.parseToJsonElement(text)
                    }}
                    if(continuation.isActive)result.fold({continuation.resume(it)},{continuation.resumeWithException(it)})
                }
            })
        }
    }
}

data class VikingHit(val uri:String,val kind:String,val layer:Int,val excerpt:String,val score:Double?)
class OpenVikingClient(private val http:CompanionHttp,val namespace:String) {
    init{validateUri(namespace)}
    private fun scoped(uri:String):Boolean {validateUri(uri);return uri==namespace.trimEnd('/') || uri.startsWith(namespace.trimEnd('/')+"/")}
    suspend fun find(query:String,limit:Int=4,layer:Int=0):List<VikingHit> {
        require(query.isNotBlank() && query.length<=4000 && limit in 1..6 && layer in 0..2)
        val envelope=http.request("/api/v1/search/find",buildJsonObject{
            put("query",query);put("target_uri",namespace);put("limit",limit);put("level",layer);put("include_provenance",true);put("read_content",false)
        }).jsonObject
        require(envelope["status"]?.jsonPrimitive?.content=="ok")
        val result=envelope.getValue("result").jsonObject
        return listOf("memories" to "memory","resources" to "resource","skills" to "skill").flatMap{(key,kind)->
            val values=result[key] as? JsonArray ?: JsonArray(emptyList());require(values.size<=100)
            values.map{item->val hit=item.jsonObject
                val uri=hit.getValue("uri").jsonPrimitive.content;require(scoped(uri)){"Out-of-scope context URI"}
                val excerpt=hit["abstract"]?.jsonPrimitive?.content.orEmpty()
                val level=hit["level"]?.jsonPrimitive?.int ?: layer;require(level in 0..2 && excerpt.length<=16000)
                val score=(hit["score"] as? JsonPrimitive)?.doubleOrNull?.also{require(it.isFinite())}
                VikingHit(uri,kind,level,excerpt.take(4000),score)
            }
        }.take(limit)
    }
    suspend fun read(uri:String,layer:Int=0):String {
        require(scoped(uri) && layer in 0..2)
        val path=when(layer){0->"abstract";1->"overview";else->"read"}
        val value=http.request("/api/v1/content/$path",query=mapOf("uri" to uri,"offset" to "0","limit" to "80")).jsonObject
        require(value["status"]?.jsonPrimitive?.content=="ok")
        return value.getValue("result").jsonPrimitive.also{require(it.isString)}.content.also{require(it.length<=32000)}
    }
    companion object {
        fun validateUri(uri:String){
            require(uri.length in 10..1024 && uri.matches(Regex("viking://[A-Za-z0-9_~.-]+(?:/[A-Za-z0-9_~.-]+)*/?")))
            require(uri.removePrefix("viking://").split('/').none{it=="." || it==".."})
        }
    }
}

/** Artemis runs on an explicitly configured device host. It is separate from on-phone Accessibility. */
class ArtemisClient(private val http:CompanionHttp) {
    suspend fun status()=http.request("/api/status")
    suspend fun readiness()=http.request("/api/system/readiness")
    suspend fun devices()=http.request("/api/devices")
    suspend fun task(id:String)=http.request("/api/sessions/${uuid(id)}")
    /** Human-reviewed task; app lock is an upstream constraint, not a Meshlit permission attestation. */
    suspend fun submit(id:String,goal:String,serial:String,appPackage:String,profile:String="flash"):JsonElement {
        uuid(id);require(goal.isNotBlank() && goal.length<=4000 && serial.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(appPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) && profile in setOf("flash","pro"))
        val response=http.request("/api/run",buildJsonObject{put("session_id",id);put("goal",goal);put("device_serial",serial);put("locked_app_package",appPackage);put("profile",profile);put("ingress","meshlit");put("enable_outputter",false)}).jsonObject
        require(response["status"]?.jsonPrimitive?.content!="rejected"){"Artemis host rejected the task"}
        val admitted=response["tasks"]?.jsonArray?.singleOrNull()?.jsonObject ?: error("Artemis admission was not acknowledged")
        require(admitted["session_id"]?.jsonPrimitive?.content==id){"Artemis acknowledged a different task"}
        admitted["device_serial"]?.jsonPrimitive?.content?.let{require(it==serial){"Artemis acknowledged a different device"}}
        return response
    }
    suspend fun stop(id:String):Boolean = http.request("/api/stop",buildJsonObject{put("session_id",uuid(id))}).jsonObject["status"]?.jsonPrimitive?.content=="stopped"
    private fun uuid(id:String)=java.util.UUID.fromString(id).toString().also{require(it==id)}
}
