package com.meshlit.search

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

internal fun parseDuckDuckGoRows(html:String):List<WebSearchRow> {
    val results=mutableListOf<WebSearchRow>()
    val snippetRegex=Regex("""<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
    val titleRegex=Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
    fun clean(value:String,limit:Int)=value.replace(Regex("<[^>]{1,1024}>"),"").replace(Regex("[\\p{Cntrl}]")," ").trim().take(limit)

    val snippetMatches=snippetRegex.findAll(html).toList()
    val titleMatches=titleRegex.findAll(html).toList()

    for(i in 0 until minOf(10,maxOf(snippetMatches.size,titleMatches.size))) {
        val titleMatch=titleMatches.getOrNull(i)
        val snippetMatch=snippetMatches.getOrNull(i)

        var rawUrl=titleMatch?.groupValues?.getOrNull(1)
            ?: snippetMatch?.groupValues?.getOrNull(1)
            ?: continue

        if(rawUrl.contains("uddg=")) {
            val extracted=rawUrl.substringAfter("uddg=").substringBefore("&")
            rawUrl=runCatching{java.net.URLDecoder.decode(extracted,Charsets.UTF_8.name())}.getOrDefault(rawUrl)
        }
        if(!safeSearchUrl(rawUrl)) continue

        val rawTitle=titleMatch?.groupValues?.getOrNull(2)
            ?: snippetMatch?.groupValues?.getOrNull(2)
            ?: rawUrl
        val rawSnippet=snippetMatch?.groupValues?.getOrNull(2).orEmpty()

        results.add(
            WebSearchRow(
                title=clean(rawTitle,300).ifBlank{rawUrl},
                url=rawUrl,
                snippet=clean(rawSnippet,1200)
            )
        )
    }
    return results.distinctBy{it.url}.take(10)
}

internal fun parseUniversalWebSearchRows(body:String):List<WebSearchRow> {
    require(body.toByteArray().size<=1024*1024){"Search response exceeds 1 MiB"}
    val root=Json.parseToJsonElement(body) as? JsonObject ?: error("Search response is not a JSON object")
    require("error" !in root){"Search provider returned an error"}
    val rows = (root["results"] as? JsonArray)
        ?: (root["organic"] as? JsonArray)
        ?: (root["items"] as? JsonArray)
        ?: ((root["web"] as? JsonObject)?.get("results") as? JsonArray)
        ?: ((root["webPages"] as? JsonObject)?.get("value") as? JsonArray)
        ?: return emptyList()

    fun clean(value:String,limit:Int)=value.replace(Regex("<[^>]{1,1024}>"),"").replace(Regex("[\\p{Cntrl}]")," ").take(limit)
    return rows.take(10).mapNotNull{entry->
        val item=entry as? JsonObject ?: return@mapNotNull null
        fun string(vararg names:String):String? {
            for(n in names){
                val s=(item[n] as? JsonPrimitive)?.takeIf{it.isString}?.content
                if(!s.isNullOrBlank()) return s
            }
            return null
        }
        val url=string("url","link","href")?.takeIf(::safeSearchUrl) ?: return@mapNotNull null
        val title=string("title","name")?.takeIf{it.isNotBlank()} ?: return@mapNotNull null
        val snippet=string("snippet","description","content","body").orEmpty()
        WebSearchRow(clean(title,300),url,clean(snippet,1200))
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
                    if(continuation.isActive) result.fold({continuation.resume(it)},{continuation.resumeWithException(IllegalStateException(if(it is SearchResponseFailure) it.message else "Invalid search provider response"))})
                }
            })
        }
    }
}

/**
 * Free, zero-setup DuckDuckGo search bridge. No API key or registration required.
 */
class DuckDuckGoWebSearchBridge(private val client:Call.Factory=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()):WebSearchBridge {
    override suspend fun search(query:String,key:String):List<WebSearchRow> {
        val form=FormBody.Builder().add("q",validateSearchQuery(query)).add("b","").build()
        val request=Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .post(form)
            .build()
        val call=client.newCall(request)
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:java.io.IOException){if(continuation.isActive) continuation.resumeWithException(IllegalStateException("Search request failed or timed out"))}
                override fun onResponse(call:Call,response:Response){
                    val result=runCatching{response.use{r->
                        if(!r.isSuccessful) throw SearchResponseFailure("Search provider HTTP ${r.code}")
                        val body=r.body ?: throw SearchResponseFailure("Empty search response")
                        if(body.contentLength()>1024*1024) throw SearchResponseFailure("Search response exceeds 1 MiB")
                        val source=body.source();if(source.request(1024*1024+1L)) throw SearchResponseFailure("Search response exceeds 1 MiB")
                        val html=source.readUtf8()
                        if(r.code==202 || Regex("challenge-form|anomaly-modal|captcha",RegexOption.IGNORE_CASE).containsMatchIn(html)) throw SearchResponseFailure("DuckDuckGo requires human verification; search is unavailable")
                        parseDuckDuckGoRows(html)
                    }}
                    if(continuation.isActive) result.fold({continuation.resume(it)},{continuation.resumeWithException(IllegalStateException(if(it is SearchResponseFailure) it.message else "Invalid search provider response"))})
                }
            })
        }
    }
}

/**
 * Universal Search Bridge for Meshlit:
 * Automatically uses free DuckDuckGo by default when no key is set or vendor is DuckDuckGo.
 * Supports Brave, Custom, and multi-vendor API keys.
 */
class UniversalWebSearchBridge(
    private val client:Call.Factory=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    private val duckDuckGo:DuckDuckGoWebSearchBridge=DuckDuckGoWebSearchBridge(client),
    private val brave:BraveWebSearchBridge=BraveWebSearchBridge(client),
    private val vendorProvider:()->com.meshlit.core.cloudmcp.web.WebSearchVendor?={null},
    private val customEndpointProvider:()->String?={null},
    private val customHeaderProvider:()->String?={null},
):WebSearchBridge {
    override suspend fun search(query:String,key:String):List<WebSearchRow> {
        val vendor=vendorProvider() ?: if(key.isBlank()) com.meshlit.core.cloudmcp.web.WebSearchVendor.DuckDuckGo else com.meshlit.core.cloudmcp.web.WebSearchVendor.Brave
        if(vendor==com.meshlit.core.cloudmcp.web.WebSearchVendor.DuckDuckGo) {
            return duckDuckGo.search(query,"")
        }
        return when(vendor) {
            com.meshlit.core.cloudmcp.web.WebSearchVendor.Brave -> brave.search(query,key)
            com.meshlit.core.cloudmcp.web.WebSearchVendor.Custom -> dispatchCustom(query,key)
            else -> error("Selected search provider is not supported by this chat adapter")
        }
    }

    private suspend fun dispatchCustom(query:String,key:String):List<WebSearchRow> {
        val endpoint=customEndpointProvider()?.takeIf{it.isNotBlank()} ?: error("Configure the custom search endpoint")
        val headerName=customHeaderProvider()?.takeIf{it.isNotBlank()} ?: "Authorization"
        val sep=if(endpoint.contains("?")) "&" else "?"
        val url="${endpoint}${sep}q=${java.net.URLEncoder.encode(validateSearchQuery(query),Charsets.UTF_8.name())}&k=10"
        val builder=Request.Builder().url(url).header("Accept","application/json")
        if(key.isNotBlank()) {
            val authValue=if(headerName.equals("Authorization",ignoreCase=true) && !key.startsWith("Bearer ",ignoreCase=true)) "Bearer $key" else key
            builder.header(headerName,authValue)
        }
        val call=client.newCall(builder.build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:java.io.IOException){if(continuation.isActive) continuation.resumeWithException(IllegalStateException("Search request failed or timed out"))}
                override fun onResponse(call:Call,response:Response){
                    val result=runCatching{response.use{r->
                        if(!r.isSuccessful) throw SearchResponseFailure("Custom search provider HTTP ${r.code}")
                        val body=r.body ?: throw SearchResponseFailure("Empty search response")
                        val source=body.source();if(source.request(1024*1024+1L)) throw SearchResponseFailure("Search response exceeds 1 MiB")
                        parseUniversalWebSearchRows(source.readUtf8())
                    }}
                    if(continuation.isActive) result.fold({continuation.resume(it)},{continuation.resumeWithException(IllegalStateException(if(it is SearchResponseFailure) it.message else "Invalid search provider response"))})
                }
            })
        }
    }
}
