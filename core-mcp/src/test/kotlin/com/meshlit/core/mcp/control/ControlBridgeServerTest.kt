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
        }finally{server.stop()}
    }
}
