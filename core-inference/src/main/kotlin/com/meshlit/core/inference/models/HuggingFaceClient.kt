package com.meshlit.core.inference.models
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable data class HubArtifact(val repo:String,val revision:String,val fileName:String,val sizeBytes:Long?,val sha256:String?,val url:String)
@Serializable data class HubRepository(val id:String,val revision:String,val private:Boolean,val gated:String,val license:String?,val files:List<HubArtifact>)
@Serializable data class HostedModelConfig(val enabled:Boolean=false,val model:String="",val endpoint:String="https://router.huggingface.co/v1/chat/completions",val billTo:String="") {
    fun validate() {
        require(model.isBlank() || model.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(:[A-Za-z0-9_-]+)?"))) { "Enter namespace/model with an optional provider suffix" }
        require(billTo.isBlank() || billTo.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val url=endpoint.toHttpUrl()
        require(url.isHttps && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null &&
            (url.host=="router.huggingface.co" || url.host.endsWith(".endpoints.huggingface.cloud"))) { "Use the HF router or your dedicated HF HTTPS endpoint" }
        require(url.encodedPath.endsWith("/v1/chat/completions")) { "Endpoint must support /v1/chat/completions" }
    }
}
/** Real Hub discovery and explicit hosted requests. No automatic cloud fallback, redirects or paid retries. */
class HuggingFaceClient {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS)
        .callTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private suspend fun fetch(request:Request):JsonElement=withContext(Dispatchers.IO) {
        val call=client.newCall(request)
        coroutineScope {
            val watcher=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
            try {call.execute().use { response ->
                require(response.isSuccessful) { when(response.code){
                    401->"HF authentication failed (401): check token and permissions"
                    403->"HF access denied (403): accept the model license or request repository access"
                    402->"HF billing required (402): check credits or provider billing"
                    404->"HF repository, model or endpoint not found (404)"
                    429->"HF rate limit (429): wait and retry manually"
                    else->"HF request failed (HTTP ${response.code})"
                } }
                val body=requireNotNull(response.body);require(body.contentLength()<=4L*1024*1024) { "HF response exceeds size limit" }
                val source=body.source();val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){currentCoroutineContext().ensureActive();val n=source.read(buffer);if(n<0) break
                    require(out.size()+n<=4*1024*1024) { "HF response exceeds size limit" };out.write(buffer,0,n)}
                Json.parseToJsonElement(out.toString("UTF-8"))
            }} finally {watcher.cancel()}
        }
    }
    private fun builder(url:String,token:String)=Request.Builder().url(url).header("User-Agent","Meshlit/0.2.3").apply {
        if(token.isNotBlank()) header("Authorization","Bearer $token")
    }
    suspend fun search(query:String,token:String=""):List<String> {
        require(query.length in 1..200)
        val url="https://huggingface.co/api/models".toHttpUrl().newBuilder().addQueryParameter("search",query)
            .addQueryParameter("filter","gguf").addQueryParameter("limit","20").build()
        return fetch(builder(url.toString(),token).build()).jsonArray.mapNotNull{it.jsonObject["id"]?.jsonPrimitive?.contentOrNull}
    }
    suspend fun repository(id:String,token:String=""):HubRepository {
        require(id.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Enter a Hugging Face namespace/repository" }
        val data=fetch(builder("https://huggingface.co/api/models/$id?blobs=true",token).build()).jsonObject
        val revision=data["sha"]?.jsonPrimitive?.content ?: error("HF did not report a revision")
        require(revision.matches(Regex("[a-f0-9]{40}")))
        val files=data["siblings"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item=element.jsonObject;val name=item["rfilename"]?.jsonPrimitive?.content ?: return@mapNotNull null
            // Split GGUF is deliberately rejected by the single-file loader.
            if(!name.endsWith(".gguf",true) || Regex("-\\d{5}-of-\\d{5}\\.gguf$",RegexOption.IGNORE_CASE).containsMatchIn(name)) return@mapNotNull null
            val lfs=item["lfs"] as? JsonObject
            val sha=lfs?.get("sha256")?.jsonPrimitive?.contentOrNull?.takeIf{it.matches(Regex("[0-9a-f]{64}"))}
            val url="https://huggingface.co".toHttpUrl().newBuilder().addPathSegment(id.substringBefore('/'))
                .addPathSegment(id.substringAfter('/')).addPathSegment("resolve").addPathSegment(revision)
            name.split('/').forEach{url.addPathSegment(it)}
            HubArtifact(id,revision,name,lfs?.get("size")?.jsonPrimitive?.longOrNull ?: item["size"]?.jsonPrimitive?.longOrNull,sha,url.build().toString())
        }.take(200)
        return HubRepository(id,revision,data["private"]?.jsonPrimitive?.booleanOrNull ?: false,
            data["gated"]?.jsonPrimitive?.content ?: "unknown",(data["cardData"] as? JsonObject)?.get("license")?.jsonPrimitive?.contentOrNull,files)
    }
    suspend fun hostedModels(token:String):JsonElement=fetch(builder("https://router.huggingface.co/v1/models",token).build())
    suspend fun whoAmI(token:String):String {
        require(token.startsWith("hf_") && token.length<=512 && token.none{it.isWhitespace()}) { "Enter a Hugging Face user access token" }
        val identity=fetch(builder("https://huggingface.co/api/whoami-v2",token).build()).jsonObject
        return identity["name"]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank() && it.length<=128}
            ?: error("Hugging Face did not return an account name")
    }
    suspend fun generate(config:HostedModelConfig,token:String,prompt:String,maxTokens:Int=256):JsonElement {
        config.validate();require(config.enabled) { "Hosted requests are disabled" };require(token.isNotBlank()) { "Save an inference-enabled HF token" }
        require(config.model.isNotBlank() && prompt.isNotBlank() && prompt.length<=32000 && maxTokens in 1..2048)
        val data=buildJsonObject{put("model",config.model);put("stream",false);put("max_tokens",maxTokens)
            put("messages",buildJsonArray{add(buildJsonObject{put("role","user");put("content",prompt)})})}
        val request=builder(config.endpoint,token).apply{if(config.billTo.isNotBlank()) header("X-HF-Bill-To",config.billTo)}
            .post(data.toString().toRequestBody("application/json".toMediaType())).build()
        return fetch(request)
    }
}
