package com.meshlit.core.mcp.control
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URI
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.*
import java.util.concurrent.TimeUnit

/** Owner-bound HTTPS endpoint. Optional exact certificate fingerprint is a pinned identity, never a trust-all fallback. */
data class WebDeviceEndpoint(val url:String,val certificateSha256:String?=null) {
    init {
        val u=URI(url);require(url.length<=2048 && u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null && u.rawQuery==null && u.rawFragment==null && u.path in listOf("","/"))
        require(u.port==-1 || u.port in 1..65535)
        require(certificateSha256==null || certificateSha256.matches(Regex("[0-9a-fA-F]{64}")))
    }
    fun route(path:String):String {require(path in setOf("/api/v1/enroll","/api/v1/status","/api/v1/resources","/api/v1/commands","/api/v1/jobs","/api/v1/cancel","/api/v1/disconnect"));return URI(url).resolve(path).toString()}
}
class WebDeviceClient(private val endpoint:WebDeviceEndpoint) {
    private val canonicalHost=endpoint.url.toHttpUrl().host
    private fun fingerprint(cert:X509Certificate)=MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(""){"%02x".format(it)}
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).apply {
            endpoint.certificateSha256?.let{pin->
                val trust=object:X509TrustManager {
                    override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
                    override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){throw java.security.cert.CertificateException("Client identity unsupported")}
                    override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){
                        require(chain.isNotEmpty());chain[0].checkValidity()
                        if(!fingerprint(chain[0]).equals(pin,true))throw java.security.cert.CertificateException("Device certificate pin changed")
                    }
                }
                val tls=SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),null)}
                sslSocketFactory(tls.socketFactory,trust)
                hostnameVerifier{host,session->host==canonicalHost && runCatching{fingerprint(session.peerCertificates[0] as X509Certificate).equals(pin,true)}.getOrDefault(false)}
            }
        }.build()
    suspend fun request(path:String,token:String?=null,body:JsonObject?=null,invitation:String?=null):JsonObject=withContext(Dispatchers.IO){
        require(token==null || token.matches(Regex("[A-Za-z0-9_-]{32,256}")))
        require(invitation==null || invitation.matches(Regex("[A-Za-z0-9_-]{32,256}")))
        require(body==null || body.toString().length<=262144)
        val request=Request.Builder().url(endpoint.route(path)).apply {
            token?.let{header("Authorization","Bearer $it")};invitation?.let{header("X-Meshlit-Invitation",it)}
            if(body!=null)post(body.toString().toRequestBody("application/json".toMediaType())) else get()
        }.build()
        val call=client.newCall(request)
        coroutineScope {
            val watcher=launch{try{awaitCancellation()}finally{call.cancel()}}
            try{call.execute().use{response->
                check(response.code==200){"Device HTTP ${response.code}"}
                val source=checkNotNull(response.body).source();require(!source.request(262145)){"Device response exceeds limit"}
                val text=source.readUtf8();var depth=0;var quoted=false;var escape=false
                text.forEach{c->if(quoted){if(escape)escape=false else if(c=='\\')escape=true else if(c=='"')quoted=false}else when(c){'"'->quoted=true;'[','{'->{depth++;require(depth<=32)};']','}'->{depth--;require(depth>=0)}}}
                Json.parseToJsonElement(text).jsonObject
            }}finally{watcher.cancel()}
        }
    }
}
