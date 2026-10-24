package com.meshlit.search

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class WebSearchRow(val title:String,val url:String,val snippet:String)
fun interface WebSearchBridge {suspend fun search(query:String,key:String):List<WebSearchRow>}
fun validateSearchQuery(value:String):String=value.trim().also{require(it.isNotBlank() && it.length<=600 && it.split(Regex("\\s+")).size<=75 && it.none{c->c.isISOControl()}){"Use 1–600 characters, at most 75 words, without control characters"}}
fun safeSearchUrl(value:String):Boolean=runCatching {val uri=URI(value);value.length<=4096 && uri.scheme in setOf("https","http") && !uri.host.isNullOrBlank() && uri.rawUserInfo==null && value.none{it.isISOControl()}}.getOrDefault(false)
internal fun parseWebSearchRows(body:String):List<WebSearchRow> {
    require(body.toByteArray().size<=1024*1024){"Search response exceeds 1 MiB"}
    val root=Json.parseToJsonElement(body) as? JsonObject ?: error("Search response is not a JSON object")
    require("error" !in root){"Search provider returned an error"}
    val web=root["web"] as? JsonObject
    val rows=web?.get("results") as? JsonArray ?: return emptyList()
    fun clean(value:String,limit:Int)=value.replace(Regex("<[^>]{1,1024}>"),"").replace(Regex("[\\p{Cntrl}]")," ").take(limit)
    return rows.take(10).mapNotNull{entry->
        val item=entry as? JsonObject ?: return@mapNotNull null
        fun string(name:String)=(item[name] as? JsonPrimitive)?.takeIf{it.isString}?.content
        val url=string("url")?.takeIf(::safeSearchUrl) ?: return@mapNotNull null
        val title=string("title")?.takeIf{it.isNotBlank()} ?: return@mapNotNull null
        WebSearchRow(clean(title,300),url,clean(string("description").orEmpty(),1200))
    }.distinctBy{it.url}
}
/** Fixed Brave API origin. No redirects, arbitrary hosts, unbounded bodies or hidden retries. */
private class SearchResponseFailure(message:String):IllegalStateException(message)
class BraveWebSearchBridge(private val client:Call.Factory=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()):WebSearchBridge {
    override suspend fun search(query:String,key:String):List<WebSearchRow> {
        val url="https://api.search.brave.com/res/v1/web/search".toHttpUrl().newBuilder().addQueryParameter("q",validateSearchQuery(query)).addQueryParameter("count","10").build()
        val call=client.newCall(Request.Builder().url(url).header("X-Subscription-Token",key).header("Accept","application/json").build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:java.io.IOException){if(continuation.isActive) continuation.resumeWithException(IllegalStateException("Search request failed or timed out"))}
                override fun onResponse(call:Call,response:Response){
                    val result=runCatching{response.use{r->
                        if(!r.isSuccessful) throw SearchResponseFailure("Search provider HTTP ${r.code}; check key, account and request limit")
                        val body=r.body ?: throw SearchResponseFailure("Empty search response")
                        if(body.contentLength()>1024*1024) throw SearchResponseFailure("Search response exceeds 1 MiB")
                        val source=body.source();if(source.request(1024*1024+1L)) throw SearchResponseFailure("Search response exceeds 1 MiB")
                        parseWebSearchRows(source.readUtf8())
                    }}
                    // Decoder/transport exceptions may contain provider body or echoed secrets.
                    if(continuation.isActive) result.fold({continuation.resume(it)},{continuation.resumeWithException(IllegalStateException(if(it is SearchResponseFailure) it.message else "Invalid search provider response"))})
                }
            })
        }
    }
}
