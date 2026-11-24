package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
class ControlBridgeServerTest {
    @Test fun authOriginAndBoundedJson(){
        var calls=0
        val server=ControlBridgeServer("127.0.0.1",0,"<h1>Connect</h1>"){_,_,token,_,body ->
            calls++;if(token!="test-token") throw BridgeHttpFailure(ResponseStatus.UNAUTHORIZED,"not_approved")
            buildJsonObject{put("accepted",body["requestId"]?.jsonPrimitive?.content.orEmpty())}
        }
        server.start(5000,false)
        try{
            fun request(body:String?,auth:String?=null,origin:String?=null):Pair<Int,String>{
                val client=OkHttpClient.Builder().callTimeout(3,TimeUnit.SECONDS).build()
                val request=Request.Builder().url("http://127.0.0.1:${server.listeningPort}/api/v1/commands")
                auth?.let{request.header("Authorization","Bearer $it")};origin?.let{request.header("Origin",it)}
                body?.let{request.post(it.toRequestBody("application/json".toMediaType()))}
                return client.newCall(request.build()).execute().use{it.code to it.body!!.string()}
            }
            assertEquals(401,request("{}").first)
            assertTrue(request("{\"requestId\":\"abc\"}","test-token").second.contains("abc"))
            val before=calls;assertEquals(403,request("{}","test-token","https://foreign.invalid").first);assertEquals(before,calls)
            assertEquals(400,request("x".repeat(65537),"test-token").first)
            assertEquals(400,request("[]","test-token").first)
            assertEquals(400,request("{\"nested\":"+"[".repeat(100)+"0"+"]".repeat(100)+"}","test-token").first)
        }finally{server.stop()}
    }
    @Test fun publicTransportAdmissionCannotSkipHandlerAuthOriginOrRevocation(){
        var approved=true
        val server=ControlBridgeServer("127.0.0.1",0,"connection controls"){_,_,token,_,_->
            if(token!="scoped-peer")throw BridgeHttpFailure(ResponseStatus.UNAUTHORIZED,"not_approved")
            buildJsonObject{put("ok",true)}
        }
        server.networkAllowed={false}
        server.ownerPublicNetworkAllowed={_,token,_,_,_->approved && token=="scoped-peer"}
        server.start(5000,false)
        try {
            val client=OkHttpClient()
            fun code(token:String?,origin:String?=null):Int {
                val request=Request.Builder().url("http://127.0.0.1:${server.listeningPort}/api/v1/status")
                token?.let{request.header("Authorization","Bearer $it")};origin?.let{request.header("Origin",it)}
                return client.newCall(request.build()).execute().use{it.code}
            }
            assertEquals(403,code(null));assertEquals(403,code("wrong"));assertEquals(200,code("scoped-peer"))
            assertEquals(403,code("scoped-peer","https://foreign.invalid"))
            approved=false;assertEquals(403,code("scoped-peer"))
        }finally{server.stop()}
    }

}
