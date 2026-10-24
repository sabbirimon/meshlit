package com.meshlit.core.mcp.control

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext

class BridgeHttpFailure(val status:ResponseStatus,val code:String):Exception(code)
enum class ResponseStatus { BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, TOO_MANY_REQUESTS }
/** Bounded JSON transport. Authority remains in the app's enrolled-device facade. */
class ControlBridgeServer(host:String,port:Int,private val html:String,
    private val handle:suspend(String,String,String?,String? ,JsonObject)->JsonElement):NanoHTTPD(host,port){
    @Volatile var networkAllowed:(String)->Boolean={true}
    private val permits=Semaphore(4)
    fun startSecure(context:SSLContext){makeSecure(context.serverSocketFactory,null);start(5000,false)}
    override fun serve(session:IHTTPSession):Response {
        if(!permits.tryAcquire()) return error(Response.Status.SERVICE_UNAVAILABLE,"busy")
        try{
            if(!networkAllowed(session.remoteIpAddress)) return error(Response.Status.FORBIDDEN,"network_policy_denied")
            if(session.method==Method.GET && session.uri=="/") return newFixedLengthResponse(Response.Status.OK,"text/html",html).apply{
                addHeader("Cache-Control","no-store");addHeader("Content-Security-Policy","default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'")
                addHeader("X-Content-Type-Options","nosniff")
            }
            val origin=session.headers["origin"]
            if(origin!=null && !origin.equals("https://${session.headers["host"]}",true)) return error(Response.Status.FORBIDDEN,"origin_denied")
            require(session.method in setOf(Method.GET,Method.POST)){"Unsupported method"}
            val body=if(session.method==Method.POST){
                val count=session.headers["content-length"]?.toIntOrNull() ?: throw IllegalArgumentException("Content-Length required")
                require(count in 1..65536 && session.headers["content-type"].orEmpty().substringBefore(';').trim()=="application/json")
                val bytes=ByteArray(count);var offset=0
                while(offset<count){val n=session.inputStream.read(bytes,offset,count-offset);require(n>0);offset+=n}
                Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: throw IllegalArgumentException("Object required")
            }else buildJsonObject{}
            val output=runBlocking{withTimeout(5000){handle(session.method.name,session.uri,
                session.headers["authorization"]?.takeIf{it.startsWith("Bearer ")}?.removePrefix("Bearer "),
                session.headers["x-meshlit-invitation"],body)}}
            return newFixedLengthResponse(Response.Status.OK,"application/json",output.toString()).apply{addHeader("Cache-Control","no-store");addHeader("X-Content-Type-Options","nosniff")}
        }catch(failed:BridgeHttpFailure){return error(when(failed.status){
            ResponseStatus.BAD_REQUEST->Response.Status.BAD_REQUEST;ResponseStatus.UNAUTHORIZED->Response.Status.UNAUTHORIZED
            ResponseStatus.FORBIDDEN->Response.Status.FORBIDDEN;ResponseStatus.NOT_FOUND->Response.Status.NOT_FOUND
            ResponseStatus.CONFLICT->Response.Status.CONFLICT;ResponseStatus.TOO_MANY_REQUESTS->Response.Status.TOO_MANY_REQUESTS},failed.code)
        }catch(_:AgentCommandFailure){return error(Response.Status.FORBIDDEN,"permission_denied")}
        catch(_:TimeoutCancellationException){return error(Response.Status.SERVICE_UNAVAILABLE,"deadline")}
        catch(_:NullPointerException){return error(Response.Status.BAD_REQUEST,"invalid_args")}
        catch(_:IllegalArgumentException){return error(Response.Status.BAD_REQUEST,"invalid_args")}
        catch(_:Exception){return error(Response.Status.INTERNAL_ERROR,"request_failed")}
        finally{permits.release()}
    }
    private fun error(status:Response.Status,code:String)=newFixedLengthResponse(status,"application/json",buildJsonObject{put("error",code)}.toString())
}
