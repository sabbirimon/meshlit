package com.meshlit.core.inference.openclaw
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
class OpenAiPhoneServerTest {
    private val token="a".repeat(64)
    private val info=ModelInfo("private.gguf","test",512,1,"Q4",1,24,0)
    private val body="""{"model":"meshlit-local","messages":[{"role":"user","content":"Hello"}],"max_tokens":16}"""
    private fun server(loaded:Boolean=true)=OpenAiPhoneServer("127.0.0.1",0,token,{if(loaded) info else null}){request ->
        assertTrue(request.prompt.contains("user: Hello"))
        MeshlitResult.Success(InferenceResult(3,2,20,100f,FinishReason.NATURAL_STOP,"real engine callback"))
    }
    private fun request(server:OpenAiPhoneServer,data:String?=body,auth:String=token):Response {
        val builder=Request.Builder().url("http://127.0.0.1:${server.listeningPort}${if(data==null) "/v1/models" else "/v1/chat/completions"}")
            .header("Authorization","Bearer $auth")
        if(data!=null) builder.post(data.toRequestBody("application/json".toMediaType()))
        return OkHttpClient().newCall(builder.build()).execute()
    }
    @Test fun unknownUsageIsOmittedForStrictOpenAiClients(){
        val server=OpenAiPhoneServer("127.0.0.1",0,token,{info}){
            MeshlitResult.Success(InferenceResult(null,null,20,null,FinishReason.NATURAL_STOP,"test response"))
        };server.start()
        try{request(server).use{assertEquals(200,it.code);assertFalse("usage" in Json.parseToJsonElement(it.body!!.string()).jsonObject)}}finally{server.stop()}
    }
    @Test fun authAndNoModelAreExplicit(){
        val server=server(false);server.start()
        try{request(server,auth="wrong").use{assertEquals(401,it.code)}
            request(server).use{assertEquals(503,it.code)}
            request(server,null).use{assertEquals(0,Json.parseToJsonElement(it.body!!.string()).jsonObject["data"]!!.jsonArray.size)}}finally{server.stop()}
    }
    @Test fun textResponseAndBufferedSseMatchOpenAiShape(){
        val server=server();server.start()
        try{request(server).use{assertEquals(200,it.code)
            assertEquals("real engine callback",Json.parseToJsonElement(it.body!!.string()).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)}
            request(server,body.dropLast(1)+",\"stream\":true}").use{assertEquals(200,it.code);val text=it.body!!.string();assertTrue(text.contains("[DONE]"));assertTrue(text.contains("chat.completion.chunk"))}}
        finally{server.stop()}
    }
    @Test fun unsupportedToolsAndImagesAreRejected(){
        val server=server();server.start()
        try{request(server,body.dropLast(1)+",\"tools\":[{\"type\":\"function\"}]}").use{assertEquals(400,it.code)}
            request(server,body.replace("\"Hello\"","[{\"type\":\"image_url\"}]")).use{assertEquals(400,it.code)}}finally{server.stop()}
    }
    @Test fun oversizedContextAndInvalidSamplingAreRejected(){
        val base=Json.parseToJsonElement(body).jsonObject
        assertThrows(IllegalArgumentException::class.java){PhoneChatRequest.parse(JsonObject(base+ ("temperature" to JsonPrimitive(9))))}
        assertThrows(IllegalArgumentException::class.java){PhoneChatRequest.parse(JsonObject(base+("messages" to JsonArray(listOf(buildJsonObject{put("role","user");put("content","a".repeat(32001))})))))}
    }
}
