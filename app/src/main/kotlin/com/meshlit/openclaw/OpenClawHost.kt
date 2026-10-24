package com.meshlit.openclaw

import android.content.Context
import android.content.Intent
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.openclaw.OpenAiPhoneServer
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class OpenClawState(val sharing:Boolean=false,val error:String?=null)
/** Separate optional operator client and phone-model provider; local SDK remains default. */
class OpenClawHost(private val context:Context,private val inference:InferenceCoordinator) {
    private val store by lazy{EncryptedCredentialStore(context,"openclaw-integration")}
    private val mutable=MutableStateFlow(OpenClawState())
    val state=mutable.asStateFlow()
    private var server:OpenAiPhoneServer?=null
    @Volatile var sharingGeneration:String?=null;private set
    val port=18791
    fun gateway()=store.get("gateway").orEmpty()
    fun agent()=store.get("agent") ?: "openclaw/default"
    fun gatewayToken()=store.get("gateway-token").orEmpty()
    fun providerToken():String=store.get("provider-token") ?: ByteArray(32).also{SecureRandom().nextBytes(it)}
        .joinToString(""){"%02x".format(it)}.also{store.put("provider-token",it)}
    fun saveGateway(url:String,token:String,target:String){
        val parsed=url.toHttpUrl()
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query==null && parsed.fragment==null)
        require(parsed.isHttps || parsed.host in setOf("127.0.0.1","localhost","::1")){"Use HTTPS for remote gateways"}
        require(token.isNotBlank() && token.length<=4096 && token.none{it=='\n' || it=='\r'})
        require(target.matches(Regex("openclaw(?:[/ :][A-Za-z0-9_-]+)?"))){"Use openclaw/default or openclaw/agent-id"}
        store.put("gateway",parsed.toString().trimEnd('/'));store.put("gateway-token",token);store.put("agent",target)
    }
    @Synchronized fun startSharing(){
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.AUTOMATION)
        if(server!=null) return
        try {
            // Loopback only. Remote access requires an owner-configured authenticated TLS tunnel.
            val next=OpenAiPhoneServer("127.0.0.1",port,providerToken(),inference::loadedModel,inference::infer)
            sharingGeneration=java.util.UUID.randomUUID().toString()
            androidx.core.content.ContextCompat.startForegroundService(context,Intent(context,OpenClawService::class.java).putExtra("generation",sharingGeneration))
            next.start(15_000,false);server=next;mutable.value=OpenClawState(sharing=true)
        }catch(e:Exception){context.stopService(Intent(context,OpenClawService::class.java));mutable.value=OpenClawState(error=e.message);throw e}
    }
    @Synchronized fun stopSharing(){sharingGeneration=null;server?.stop();server=null;mutable.value=OpenClawState();context.stopService(Intent(context,OpenClawService::class.java))}
    @Synchronized fun stopIfGeneration(id:String?){if(id!=null && sharingGeneration==id) stopSharing()}
    suspend fun send(text:String,session:String):String=withContext(Dispatchers.IO){
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.AUTOMATION)
        require(text.isNotBlank() && text.length<=32_000)
        val base=gateway();check(base.isNotBlank()){ "Configure an OpenClaw gateway first" }
        val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS).callTimeout(185,TimeUnit.SECONDS).build()
        val body=buildJsonObject{put("model",agent());put("user",session);put("stream",false)
            put("messages",buildJsonArray{add(buildJsonObject{put("role","user");put("content",text)})})}
        val call=client.newCall(Request.Builder().url("$base/v1/chat/completions").header("Authorization","Bearer ${gatewayToken()}")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build())
        val watcher=CoroutineScope(coroutineContext).launch{try{awaitCancellation()}finally{call.cancel()}}
        try{call.execute().use{response ->
            check(response.isSuccessful){"OpenClaw returned HTTP ${response.code}; enable its chatCompletions endpoint and verify the token"}
            val source=checkNotNull(response.body).source();val buffer=okio.Buffer()
            while(true){val read=source.read(buffer,8192);if(read<0) break;require(buffer.size<=1_048_576){"OpenClaw response is too large"}}
            val bytes=buffer.readByteArray()
            val payload=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            payload["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: error("OpenClaw returned no text answer")
        }}finally{watcher.cancel();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
}
