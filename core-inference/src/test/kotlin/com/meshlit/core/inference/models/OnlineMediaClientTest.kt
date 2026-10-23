package com.meshlit.core.inference.models
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Offline protocol/response failure checks; not paid API or model-quality proof. */
class OnlineMediaClientTest {
    private val p=OnlineProfile("media","media","https://example.invalid/v1","vision-model",enabled=true)
    private fun client(body:String,onRequest:(Request)->Unit={})=OnlineMediaClient(OkHttpClient.Builder().addInterceptor{chain->
        onRequest(chain.request());Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())
    private fun wav()=ByteArray(32044).apply {
        "RIFF".toByteArray().copyInto(this,0);"WAVE".toByteArray().copyInto(this,8)
    }
    @Test fun visionUsesReviewedModelAndKeepsAuthoritativeUsage()=runBlocking {
        val reply=client("""{"choices":[{"message":{"content":"A red mug."}}],"usage":{"prompt_tokens":42,"completion_tokens":6}}"""){request->
            assertEquals("/v1/chat/completions",request.url.encodedPath)
            val buffer=okio.Buffer();request.body!!.writeTo(buffer)
            val raw=buffer.readUtf8()
            assertTrue(raw.contains("vision-model"));assertTrue(raw.contains("image_url"))
            assertTrue(raw.contains("What is in this photo?"));assertTrue(raw.contains("max_completion_tokens"))
        }.visionReply(p.copy(protocol=OnlineProtocol.OPENAI),"test-key",byteArrayOf(-1,-40,0,0),"What is in this photo?")
        assertEquals("A red mug.",reply.text);assertEquals(42L,reply.inputTokens);assertEquals(6L,reply.outputTokens)
    }
    @Test fun visionDoesNotInventCountsAndRejectsEmptyResponses()=runBlocking {
        for(usage in listOf("",",\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":\"bad\"}")) {
            val reply=client("""{"choices":[{"message":{"content":"Answer"}}]$usage}""")
                .visionReply(p,"test-key",byteArrayOf(-1,-40,0,0),"Read this")
            assertNull(reply.inputTokens);assertNull(reply.outputTokens)
        }
        try { client("""{"choices":[{"message":{"content":""}}]}""")
            .visionReply(p,"test-key",byteArrayOf(-1,-40,0,0),"Read this");fail("empty response") }
        catch(_:IllegalStateException) { }
    }
    @Test fun visionRejectsDisabledProfilesAndOversizedQuestionsBeforeSending()=runBlocking {
        var calls=0;val c=client("{}"){calls++}
        for(profile in listOf(p.copy(enabled=false),p.copy(protocol=OnlineProtocol.ANTHROPIC),p.copy(model=""))) {
            try { c.visionReply(profile,"test-key",byteArrayOf(-1,-40,0,0),"Read this");fail("unsupported profile") }
            catch(_:IllegalArgumentException) { }
        }
        try { c.visionReply(p,"test-key",byteArrayOf(-1,-40,0,0),"x".repeat(4001));fail("long question") }
        catch(_:IllegalArgumentException) { }
        assertEquals(0,calls)
    }
    @Test fun cancelledVisionCannotReturnAnAnswer()=runBlocking {
        val started=CompletableDeferred<Unit>()
        val release=java.util.concurrent.CountDownLatch(1)
        var returned=false
        val c=client("""{"choices":[{"message":{"content":"late answer"}}]}""") {
            started.complete(Unit)
            check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))
        }
        val job=launch {
            c.visionReply(p,"test-key",byteArrayOf(-1,-40,0,0),"Read this")
            returned=true
        }
        try { withTimeout(5000){started.await()};job.cancel() }
        finally {release.countDown()}
        job.join();assertTrue(job.isCancelled);assertFalse(returned)
    }
    @Test fun transcriptionUsesSeparateModelAndAudioEndpointWithExplicitCredential()=runBlocking {
        val text=client("""{"text":"Actual response field"}"""){request->
            assertEquals("/v1/audio/transcriptions",request.url.encodedPath)
            assertEquals("Bearer test-key",request.header("Authorization"))
            val body=request.body as MultipartBody
            val encoded=okio.Buffer();body.writeTo(encoded);val raw=encoded.readUtf8()
            assertTrue(raw.contains("recognition-model"));assertTrue(raw.contains("filename=\"turn.wav\""))
            assertFalse(raw.contains("vision-model"))
        }.transcribe(p,"test-key","recognition-model",wav())
        assertEquals("Actual response field",text)
    }
    @Test fun transcriptionRejectsDisabledInvalidAudioAndEmptyTranscript()=runBlocking {
        var calls=0;val c=client("""{"text":""}"""){calls++}
        for(profile in listOf(p.copy(enabled=false),p.copy(protocol=OnlineProtocol.ANTHROPIC))) {
            try{c.transcribe(profile,"test-key","recognition-model",wav());fail("unsupported profile")}
            catch(_:IllegalArgumentException){assertEquals(0,calls)}
        }
        try{c.transcribe(p,"test-key","recognition-model",ByteArray(50));fail("invalid audio")}
        catch(_:IllegalArgumentException){assertEquals(0,calls)}
        try{c.transcribe(p,"test-key","recognition-model",wav());fail("empty transcript")}
        catch(_:IllegalStateException){assertEquals(1,calls)}
    }
    @Test fun disabledProfilesFailBeforeAnyRequest()=runBlocking {
        var calls=0
        try{client("{}"){calls++}.startVideo(p.copy(enabled=false),"secret","video-model","task");fail("disabled")}
        catch(_:IllegalArgumentException){assertEquals(0,calls)}
    }
    @Test fun videoJobUsesMultipartAndActualReturnedState()=runBlocking {
        val r=client("""{"id":"video_123","status":"queued","progress":7}"""){request->
            assertEquals("/v1/videos",request.url.encodedPath);assertEquals("Bearer secret",request.header("Authorization"))
            assertTrue(request.body is MultipartBody)
        }.startVideo(p,"secret","video-model","task")
        assertEquals("video_123",r.id);assertEquals("queued",r.status);assertEquals(7,r.progress)
    }
    @Test fun invalidRemoteIdCannotBecomeARequestPath()=runBlocking {
        var calls=0
        try{client("{}"){calls++}.videoStatus(p,"secret","../other");fail("bad ID")}
        catch(_:IllegalArgumentException){assertEquals(0,calls)}
    }
    @Test fun imageUrlIsNotAutomaticallyFetchedAndNoFileIsInstalled()=runBlocking {
        val f=File.createTempFile("media-contract-",".png").apply{delete()}
        try{client("""{"data":[{"url":"https://other.invalid/image"}]}""").image(p,"secret","image-model","task",f);fail("URL-only response")}
        catch(_:IllegalStateException){assertFalse(f.exists())}finally{f.delete()}
    }
    @Test fun nonPngPayloadIsNotAnInstalledImage()=runBlocking {
        val f=File.createTempFile("media-contract-",".png").apply{delete()}
        try{client("""{"data":[{"b64_json":"bm90LWltYWdlLWJ5dGVz"}]}""").image(p,"secret","image-model","task",f);fail("bad container")}
        catch(_:IllegalArgumentException){assertFalse(f.exists())}finally{f.delete()}
    }
}
