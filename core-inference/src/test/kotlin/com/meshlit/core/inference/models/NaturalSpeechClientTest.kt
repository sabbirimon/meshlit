package com.meshlit.core.inference.models

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NaturalSpeechClientTest {
    private fun profile(server:MockWebServer)=OnlineProfile("voice","Voice",server.url("/").newBuilder().host("127.0.0.1").build().toString(),model="speech",enabled=true,requiresApiKey=false)
    @Test fun catalogueUsesActualInstalledStateAndFloatAudioIsNormalized()=runBlocking<Unit>{
        val server=MockWebServer();server.start()
        try{
            val client=NaturalSpeechClient();val p=profile(server)
            server.enqueue(MockResponse().setBody("""[{"family":"pocket","variants":[{"id":"pocket-int8","name":"Pocket","installed":false,"runnable":true,"voices":[{"id":"alba","name":"Alba"}]}]}]"""))
            val engines=client.engines(p,"");assertFalse(engines.single().ready);assertEquals("alba",engines.single().voices.single().id)
            val floats=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(-1f).putFloat(0.5f).array()
            server.enqueue(MockResponse().setHeader("Content-Type","application/octet-stream").setHeader("x-sample-rate","24000").setBody(Buffer().write(floats)))
            val audio=client.synthesize(p,"",false,"pocket-int8","alba","Hello")
            assertEquals(24000,audio.rate);assertArrayEquals(byteArrayOf(0,-128,0,64),audio.bytes)
            server.takeRequest();val speech=server.takeRequest();assertEquals("/tts",speech.path);assertTrue(speech.body.readUtf8().contains("pocket-int8"))
        }finally{server.shutdown()}
    }
    @Test fun freeOnlyModeBlocksPaidUnknownAndExhaustedAccounts(){
        val client=NaturalSpeechClient()
        fun account(json:String)=kotlinx.serialization.json.Json.parseToJsonElement(json).let{it as kotlinx.serialization.json.JsonObject}
        client.checkFreeAllowance(account("""{"tier":"free","character_count":1,"character_limit":100}"""),50)
        for(json in listOf("""{"tier":"starter","character_count":1,"character_limit":100}""","""{"tier":"free","character_count":100,"character_limit":100}""","""{"tier":"free"}"""))assertThrows(IllegalStateException::class.java){client.checkFreeAllowance(account(json),1)}
    }
    @Test fun revokedConnectionIsRejectedAtDispatchWithoutSendingRequests()=runBlocking<Unit>{
        val server=MockWebServer();server.start()
        try{
            val client=NaturalSpeechClient(authorize={_,_->error("revoked")})
            try{client.engines(profile(server),"");fail()}catch(e:IllegalStateException){assertEquals("revoked",e.message)}
            assertEquals(0,server.requestCount)
        }finally{server.shutdown()}
    }
    @Test fun elevenKeyIsBoundToOfficialOriginAndUsesItsOwnHeader(){
        val client=NaturalSpeechClient();val p=OnlineProfile("eleven","ElevenLabs","https://api.elevenlabs.io/v1",model="eleven_multilingual_v2",enabled=true)
        val request=client.request(p,"test-key",true,"voices")
        assertEquals("test-key",request.header("xi-api-key"));assertNull(request.header("Authorization"))
        for(endpoint in listOf("https://example.com/v1","https://api.elevenlabs.io:8443/v1","https://api.elevenlabs.io/other"))assertThrows(IllegalArgumentException::class.java){client.request(p.copy(endpoint=endpoint),"test-key",true,"voices")}
    }
    @Test fun wrongAudioFormatAndInterruptedRequestsCannotReturnSpeech()=runBlocking<Unit>{
        val server=MockWebServer();server.start()
        try{
            val client=NaturalSpeechClient();val p=profile(server)
            server.enqueue(MockResponse().setHeader("Content-Type","audio/mpeg").setHeader("x-sample-rate","24000").setBody("1234"))
            try{client.synthesize(p,"",false,"pocket-int8","alba","Hello");fail()}catch(_:IllegalArgumentException){}
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try{withTimeout(150){client.synthesize(p,"",false,"pocket-int8","alba","Hello")};fail()}catch(_:TimeoutCancellationException){}
        }finally{server.shutdown()}
    }
}
