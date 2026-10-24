package com.meshlit.openclaw

import android.content.Context
import android.os.Build
import android.util.Base64
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal paired Android node adapter. Protocol 4/v3 signing, not the upstream Android UI. */
class OpenClawNode(private val context:Context,private val host:OpenClawHost,private val control:AndroidControl,private val backend:com.meshlit.control.AgentBackend) {
    private val credentials by lazy{EncryptedCredentialStore(context,"openclaw-node")}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow("Disconnected")
    val status=mutable.asStateFlow()
    private var socket:WebSocket?=null
    private var client:OkHttpClient?=null
    @Volatile private var epoch=0L
    private var actionJob:Job?=null
    private val busy=AtomicBoolean(false)
    private val handled=linkedSetOf<String>()
    @Synchronized fun disconnect(){epoch++;actionJob?.cancel();actionJob=null;busy.set(false);context.stopService(android.content.Intent(context,OpenClawNodeService::class.java));socket?.cancel();socket=null;client?.dispatcher?.executorService?.shutdown();client?.connectionPool?.evictAll();client=null;mutable.value="Disconnected"}
    @Synchronized fun connect(){
        disconnect()
        check(host.gateway().isNotBlank()){ "Save a gateway first" }
        val active=epoch
        androidx.core.content.ContextCompat.startForegroundService(context,android.content.Intent(context,OpenClawNodeService::class.java).putExtra("epoch",active))
        val key=credentials.get("identity-seed")?.let{Base64.decode(it,Base64.DEFAULT)}
            ?: ByteArray(32).also{SecureRandom().nextBytes(it);credentials.put("identity-seed",Base64.encodeToString(it,Base64.NO_WRAP))}
        val privateKey=Ed25519PrivateKeyParameters(key,0)
        val publicKey=privateKey.generatePublicKey().encoded
        val deviceId=MessageDigest.getInstance("SHA-256").digest(publicKey).joinToString(""){"%02x".format(it)}
        val gatewayKey=MessageDigest.getInstance("SHA-256").digest(host.gateway().toByteArray()).joinToString(""){"%02x".format(it)}
        val auth=credentials.get("device-token-$gatewayKey") ?: host.gatewayToken()
        check(auth.isNotBlank()){ "Gateway token is required for initial pairing" }
        val connectId=UUID.randomUUID().toString()
        var admitted=false
        handled.clear()
        val transport=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        client=transport;mutable.value="Connecting; approve the device on your gateway"
        val url=host.gateway().replaceFirst("https://","wss://").replaceFirst("http://","ws://")
        socket=transport.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
            override fun onMessage(ws:WebSocket,text:String){
                if(active!=epoch) return
                if(text.length>262144){ws.close(1009,"Frame limit");mutable.value="Gateway frame too large";return}
                try{
                    val frame=Json.parseToJsonElement(text).jsonObject
                    if(frame["event"]?.jsonPrimitive?.contentOrNull=="connect.challenge"){
                        val challenge=frame["payload"]!!.jsonObject
                        val nonce=challenge["nonce"]!!.jsonPrimitive.content
                        val ts=challenge["ts"]!!.jsonPrimitive.long
                        require(ts>=0 && nonce.length in 1..1024)
                        // Canonical field order verified against upstream DeviceAuthPayload.buildV3.
                        val signatureBody=listOf("v3",deviceId,"openclaw-android","node","node","",ts.toString(),auth,nonce,"android","phone").joinToString("|")
                        val signer=Ed25519Signer().apply{init(true,privateKey)}
                        val bytes=signatureBody.toByteArray();signer.update(bytes,0,bytes.size)
                        val params=buildJsonObject{
                            put("minProtocol",4);put("maxProtocol",4)
                            put("client",buildJsonObject{put("id","openclaw-android");put("displayName","Meshlit Android");put("version",com.meshlit.BuildConfig.VERSION_NAME)
                                put("platform","android");put("mode","node");put("deviceFamily","phone")})
                            put("role","node");put("scopes",buildJsonArray{})
                            put("caps",buildJsonArray{add("device");if(control.enabled()) add("meshlit")})
                            put("commands",buildJsonArray{add("device.info");add("device.status");backend.specs().forEach{add("meshlit.${it.name}")};if(control.enabled()) add("meshlit.android.control")})
                            put("auth",buildJsonObject{put("token",auth)})
                            put("device",buildJsonObject{put("id",deviceId);put("publicKey",url64(publicKey));put("signature",url64(signer.generateSignature()));put("signedAt",ts);put("nonce",nonce)})
                        }
                        ws.send(rpc(connectId,"connect",params).toString())
                    }else if(frame["type"]?.jsonPrimitive?.contentOrNull=="res" && frame["id"]?.jsonPrimitive?.contentOrNull==connectId){
                        admitted=frame["ok"]?.jsonPrimitive?.booleanOrNull==true
                        if(admitted){frame["payload"]?.jsonObject?.get("auth")?.jsonObject?.get("deviceToken")?.jsonPrimitive?.contentOrNull
                            ?.let{credentials.put("device-token-$gatewayKey",it)};mutable.value="Paired Android node connected"}
                        else{mutable.value="Pairing or authorization required; inspect gateway devices list";ws.close(1000,"Not admitted")}
                    }else if(admitted && frame["event"]?.jsonPrimitive?.contentOrNull=="node.invoke.request"){
                        val request=frame["payload"]!!.jsonObject
                        val id=request["id"]!!.jsonPrimitive.content
                        if(!busy.compareAndSet(false,true)) {sendResult(ws,request,McpToolResult.Error(McpToolResult.ErrorCode.EXEC_FAILED,"Node busy"));return}
                        synchronized(handled){if(!handled.add(id)){busy.set(false);sendResult(ws,request,McpToolResult.Error(McpToolResult.ErrorCode.EXEC_FAILED,"Duplicate invoke refused"));return};if(handled.size>256) handled.remove(handled.first())}
                        actionJob=scope.launch{
                            try{
                                val outcome=withTimeout((request["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 15_000).coerceIn(100,30_000)){
                                    if(active!=epoch) error("Node disconnected")
                                    when(request["command"]?.jsonPrimitive?.contentOrNull){
                                        "device.info","device.status" -> McpToolResult.Json(buildJsonObject{put("platform","android");put("sdk",Build.VERSION.SDK_INT)
                                            put("manufacturer",Build.MANUFACTURER);put("model",Build.MODEL);put("abi",Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                                            put("autonomous_delegation",control.enabled())})
                                        "meshlit.android.control" -> {
                                            val args=request["paramsJSON"]?.jsonPrimitive?.contentOrNull?.let{Json.parseToJsonElement(it)} ?: request["params"] ?: JsonObject(emptyMap())
                                            control.specs().first{it.name=="android_control"}.handler(args)
                                        }
                                        else -> {
                                            val name=request["command"]?.jsonPrimitive?.contentOrNull?.removePrefix("meshlit.")
                                            val spec=backend.specs().firstOrNull{it.name==name}
                                            if(spec==null) McpToolResult.Error(McpToolResult.ErrorCode.NOT_FOUND,"Command is not advertised")
                                            else {val args=request["paramsJSON"]?.jsonPrimitive?.contentOrNull?.let{Json.parseToJsonElement(it)} ?: request["params"] ?: JsonObject(emptyMap());spec.handler(args)}
                                        }
                                    }
                                }
                                if(active==epoch) sendResult(ws,request,outcome)
                            }catch(_:Exception){if(active==epoch) sendResult(ws,request,McpToolResult.Error(McpToolResult.ErrorCode.EXEC_FAILED,"Node action failed or timed out"))}
                            finally{if(active==epoch) busy.set(false)}
                        }
                    }
                }catch(_:Exception){mutable.value="Invalid gateway protocol frame";ws.close(1002,"Protocol error")}
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){if(active==epoch) mutable.value="Connection failed; check HTTPS trust, reachability and pairing"}
            override fun onClosed(ws:WebSocket,code:Int,reason:String){if(active==epoch && admitted) mutable.value="Disconnected; reconnect explicitly"}
        })
        scope.launch{delay(20_000);if(active==epoch && !admitted){socket?.cancel();mutable.value="Gateway pairing timed out; approve and reconnect"}}
    }
    @Synchronized fun disconnectIfEpoch(value:Long){if(value==epoch) disconnect()}
    private fun sendResult(ws:WebSocket,request:JsonObject,result:McpToolResult){
        val params=buildJsonObject{
            put("id",request["id"]!!);put("nodeId",request["nodeId"]!!);put("ok",result !is McpToolResult.Error)
            when(result){is McpToolResult.Json->put("payload",result.value);is McpToolResult.Text->put("payload",buildJsonObject{put("text",result.text)})
                is McpToolResult.Error->put("error",buildJsonObject{put("code",result.code.wireValue);put("message",result.message)})}
        }
        ws.send(rpc(UUID.randomUUID().toString(),"node.invoke.result",params).toString())
    }
    private fun rpc(id:String,method:String,params:JsonObject)=buildJsonObject{put("type","req");put("id",id);put("method",method);put("params",params)}
    private fun url64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
