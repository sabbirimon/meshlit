package com.meshlit.core.inference.models

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable enum class OnlineProtocol { OPENAI, OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }
@Serializable data class OnlineProfile(val id:String,val name:String,val endpoint:String,val model:String="",
    val protocol:OnlineProtocol=OnlineProtocol.OPENAI_COMPATIBLE,val enabled:Boolean=false,val agentAllowed:Boolean=false,
    val sendTemperature:Boolean=false,val inputPricePerMillion:Double?=null,val outputPricePerMillion:Double?=null,val currency:String="USD",val pricingSource:String?=null,val priceCheckedAtMs:Long?=null,val requiresApiKey:Boolean=true,val credentialEnvironmentId:String?=null,val credentialVariable:String="API_KEY") {
    fun validate() {
        require(credentialEnvironmentId==null || credentialEnvironmentId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(credentialVariable.matches(Regex("[A-Z_][A-Z0-9_]{0,79}")))
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")) && name.length in 1..100)
        val url=endpoint.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) { "Use an HTTPS API base URL without credentials or query parameters" }
        require(model.length<=200 && model.none{it.isWhitespace() || it=='?' || it=='#' || it=='\\'})
        require(currency.matches(Regex("[A-Z]{3}")))
        listOf(inputPricePerMillion,outputPricePerMillion).forEach{require(it==null || it.isFinite() && it in 0.0..100000.0)}
        if(enabled) require(model.isNotBlank()) { "Select or enter a model before enabling this profile" }
    }
}
data class OnlineMessage(val role:String,val text:String)
data class OnlineReply(val text:String,val inputTokens:Long?,val outputTokens:Long?) {
    fun estimatedCost(profile:OnlineProfile):Double? {
        val input=inputTokens ?: return null;val output=outputTokens ?: return null
        return input*(profile.inputPricePerMillion ?: return null)/1e6+output*(profile.outputPricePerMillion ?: return null)/1e6
    }
}
/** Explicit buffered cloud requests. No credentials in URLs, redirect forwarding, automatic retries or fallback. */
class OnlineModelClient {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS)
        .callTimeout(120,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    private fun request(profile:OnlineProfile,token:String,path:String):Request.Builder {
        profile.validate();require(!profile.requiresApiKey || token.isNotBlank() && token.length<=4096 && token.none{it.isWhitespace()}) { "Save an API key first" }
        val url=(profile.endpoint.trimEnd('/')+"/"+path).toHttpUrl()
        return Request.Builder().url(url).apply { if(profile.requiresApiKey) when(profile.protocol) {
            OnlineProtocol.ANTHROPIC -> {header("x-api-key",token);header("anthropic-version","2023-06-01")}
            OnlineProtocol.GEMINI -> header("x-goog-api-key",token)
            else -> header("Authorization","Bearer $token")
        } }
    }
    private suspend fun fetch(request:Request):JsonObject=withContext(Dispatchers.IO) { coroutineScope {
        val call=client.newCall(request)
        val cancellation=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
        try { call.execute().use{response ->
            check(response.isSuccessful) { "Provider HTTP ${response.code}; check API credentials, model availability, region, quota and billing" }
            val body=response.body ?: error("Empty provider response")
            require(body.contentLength()<=4*1024*1024) { "Provider response exceeds 4 MiB" }
            val source=body.source();source.request(4L*1024*1024+1)
            require(source.buffer.size<=4L*1024*1024) { "Provider response exceeds 4 MiB" }
            Json.parseToJsonElement(source.readUtf8()).jsonObject
        } } finally { cancellation.cancel() }
    } }
    suspend fun models(profile:OnlineProfile,token:String):List<String> {
        val discovery=if(profile.endpoint.toHttpUrl().host=="router.huggingface.co") profile.copy(requiresApiKey=false) else profile
        val data=fetch(request(discovery,token,if(profile.protocol==OnlineProtocol.GEMINI) "models?pageSize=100" else "models").build())
        return if(profile.protocol==OnlineProtocol.GEMINI) data["models"]?.jsonArray.orEmpty().mapNotNull {
            val model=it.jsonObject
            if(model["supportedGenerationMethods"]?.jsonArray?.any{method->method.jsonPrimitive.content=="generateContent"}==true)
                model["name"]?.jsonPrimitive?.content?.removePrefix("models/") else null
        } else data["data"]?.jsonArray.orEmpty().mapNotNull{it.jsonObject["id"]?.jsonPrimitive?.contentOrNull}
    }
    suspend fun generate(profile:OnlineProfile,token:String,messages:List<OnlineMessage>,system:String="",maxTokens:Int=1024,temperature:Float=0.7f):OnlineReply {
        profile.validate();require(profile.enabled) { "Enable the selected online profile first" }
        require(messages.isNotEmpty() && messages.size<=41 && messages.all{it.role in setOf("user","assistant")})
        require(messages.sumOf{it.text.length}+system.length<=96000 && maxTokens in 1..2048 && temperature.isFinite() && temperature in 0f..2f)
        val payload=buildJsonObject { when(profile.protocol) {
            OnlineProtocol.GEMINI -> {
                put("contents",buildJsonArray{messages.forEach{message->add(buildJsonObject{put("role",if(message.role=="assistant") "model" else "user");put("parts",buildJsonArray{add(buildJsonObject{put("text",message.text)})})})}})
                if(system.isNotBlank()) put("systemInstruction",buildJsonObject{put("parts",buildJsonArray{add(buildJsonObject{put("text",system)})})})
                put("generationConfig",buildJsonObject{put("maxOutputTokens",maxTokens);if(profile.sendTemperature) put("temperature",temperature)})
            }
            else -> {
                put("model",profile.model);put(if(profile.protocol==OnlineProtocol.OPENAI) "max_completion_tokens" else "max_tokens",maxTokens)
                if(profile.sendTemperature) put("temperature",temperature)
                if(profile.protocol==OnlineProtocol.ANTHROPIC && system.isNotBlank()) put("system",system)
                put("messages",buildJsonArray{
                    if(system.isNotBlank() && profile.protocol!=OnlineProtocol.ANTHROPIC) add(buildJsonObject{put("role","system");put("content",system)})
                    messages.forEach{message ->add(buildJsonObject{put("role",message.role);put("content",message.text)})}
                })
                put("stream",false)
            }
        } }
        val path=when(profile.protocol){OnlineProtocol.ANTHROPIC->"messages";OnlineProtocol.GEMINI->{require(profile.model.matches(Regex("[A-Za-z0-9_.-]+")));"models/${profile.model}:generateContent"};else->"chat/completions"}
        val data=fetch(request(profile,token,path).post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        val text=when(profile.protocol) {
            OnlineProtocol.ANTHROPIC -> data["content"]?.jsonArray.orEmpty().mapNotNull{it.jsonObject["text"]?.jsonPrimitive?.contentOrNull}.joinToString("")
            OnlineProtocol.GEMINI -> data["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty().mapNotNull{it.jsonObject["text"]?.jsonPrimitive?.contentOrNull}.joinToString("")
            else -> data["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.let{(it as? JsonPrimitive)?.contentOrNull}.orEmpty()
        }
        require(text.isNotBlank()) { "Provider returned no text; check safety, token budget and model modality" }
        val usage=(data[if(profile.protocol==OnlineProtocol.GEMINI) "usageMetadata" else "usage"] as? JsonObject)
        fun count(vararg keys:String)=keys.firstNotNullOfOrNull{usage?.get(it)?.jsonPrimitive?.longOrNull?.takeIf{n->n>=0}}
        return OnlineReply(text,count("prompt_tokens","input_tokens","promptTokenCount"),count("completion_tokens","output_tokens","candidatesTokenCount"))
    }
}

@Serializable data class PublishedModelPrice(val model:String,val provider:String,val inputPerMillion:Double?,val outputPerMillion:Double?,val currency:String="USD",val source:String,val fetchedAtMs:Long,val isFree:Boolean?=null)
class PublishedPricingClient {
    private val client=OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun huggingFace(model:String=""):List<PublishedModelPrice> = withContext(Dispatchers.IO) {coroutineScope {
        require(model.isBlank() || model.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(:[A-Za-z0-9_-]+)?")))
        val source="https://router.huggingface.co/v1/models"
        val call=client.newCall(Request.Builder().url(source).build())
        val cancel=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
        try{call.execute().use{response->
            check(response.isSuccessful){"Pricing HTTP ${response.code}"}
            val body=response.body ?: error("No pricing response");val stream=body.source();stream.request(4L*1024*1024+1);require(stream.buffer.size<=4L*1024*1024)
            val data=Json.parseToJsonElement(stream.readUtf8()).jsonObject["data"]?.jsonArray ?: error("Pricing catalog schema changed")
            val rows=if(model.isBlank()) data else data.filter{it.jsonObject["id"]?.jsonPrimitive?.content==model.substringBefore(':')}
            if(rows.isEmpty()) error("Model not in the live pricing catalog")
            rows.flatMap{row->val id=row.jsonObject["id"]?.jsonPrimitive?.content ?: return@flatMap emptyList()
                row.jsonObject["providers"]?.jsonArray.orEmpty().mapNotNull{entry->val offer=entry.jsonObject;val pricing=offer["pricing"] as? JsonObject
                    if(offer["status"]?.jsonPrimitive?.content!="live") return@mapNotNull null
                    fun rate(key:String)=pricing?.get(key)?.jsonPrimitive?.doubleOrNull?.takeIf{it.isFinite() && it>=0}
                    PublishedModelPrice(id,offer["provider"]?.jsonPrimitive?.content ?: return@mapNotNull null,rate("input"),rate("output"),source=source,fetchedAtMs=System.currentTimeMillis(),isFree=offer["is_free"]?.jsonPrimitive?.booleanOrNull)
                }
            }.take(4000)
        }}finally{cancel.cancel()}
    }}
    companion object {
        fun officialPage(endpoint:String):String?=when(runCatching{endpoint.toHttpUrl().host}.getOrNull()) {
            "api.openai.com"->"https://developers.openai.com/api/docs/pricing"
            "api.anthropic.com"->"https://platform.claude.com/docs/en/about-claude/pricing"
            "api.deepseek.com"->"https://api-docs.deepseek.com/quick_start/pricing"
            "generativelanguage.googleapis.com"->"https://ai.google.dev/gemini-api/docs/pricing"
            "router.huggingface.co"->"https://huggingface.co/inference/models"
            else->if(endpoint.contains("aliyuncs.com")) "https://www.alibabacloud.com/help/en/model-studio/model-pricing" else null
        }
    }
}
