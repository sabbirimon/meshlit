package com.meshlit.gateway
import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.core.common.control.ManagedFeature
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
@Serializable data class AcceleratorHostProfile(val endpoint:String="",val token:String="") {
    fun validate(){val url=endpoint.toHttpUrl();require(url.scheme=="https" || url.scheme=="http" && url.host in setOf("127.0.0.1","::1"));require(url.encodedPath=="/node" && url.query==null && url.fragment==null && url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty());require(token.length in 32..4096 && token.none(Char::isWhitespace))}
}
/** Exact authenticated HTTPS node or a private loopback tunnel. Probe is read-only and
 * explicitly enabled on the host. No discovery-supplied endpoints or driver install. */
class AcceleratorHosts(context:Context){
    private val store=EncryptedCredentialStore(context,"accelerator-host-profile")
    private val gate=com.meshlit.operations.OperationsControl.get(context).gate
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(80,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    fun saved()=store.get("profile")?.let{Json.decodeFromString<AcceleratorHostProfile>(it)} ?: AcceleratorHostProfile()
    fun saveHuman(profile:AcceleratorHostProfile){profile.validate();store.putCommitted("profile",Json.encodeToString(profile))}
    suspend fun probe():JsonObject=gate.run(ManagedFeature.GATEWAY){withContext(Dispatchers.IO){
        val profile=saved();profile.validate();val call=client.newCall(Request.Builder().url(profile.endpoint).header("Authorization","Bearer ${profile.token}").post("{\"operation\":\"accelerators\"}".toRequestBody("application/json".toMediaType())).build())
        val reply=suspendCancellableCoroutine<Response>{cont->call.enqueue(object:Callback{override fun onFailure(call:Call,e:java.io.IOException){if(cont.isActive)cont.resumeWithException(e)};override fun onResponse(call:Call,response:Response){cont.resume(response){_,value,_->value.close()}}});cont.invokeOnCancellation{call.cancel()}}
        reply.use{require(it.isSuccessful && it.header("Content-Type").orEmpty().startsWith("application/json"));val source=requireNotNull(it.body).source();require(!source.request(524289));val value=Json.parseToJsonElement(source.readUtf8()).jsonObject
            require(value["schema"]?.jsonPrimitive?.int==1 && value["inference_qualified"]?.jsonPrimitive?.boolean==false && value["vendors"] is JsonArray && saved()==profile){"Unexpected or revoked host observation"};value}
    }}
}
