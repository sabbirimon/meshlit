package com.meshlit.core.inference.models
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
class ModelDownloadTest {
    private lateinit var server:MockWebServer
    private lateinit var dir:File
    private lateinit var dest:File
    private val bytes=ByteBuffer.allocate(100003).order(ByteOrder.LITTLE_ENDIAN).apply{
        put("GGUF".toByteArray());putInt(3);putLong(1);putLong(1)
    }.array()
    @Before fun setup(){server=MockWebServer().apply{start()};dir=kotlin.io.path.createTempDirectory().toFile();dest=File(dir,"model.gguf")}
    @After fun cleanup(){server.shutdown();dir.deleteRecursively()}
    private fun response(body:ByteArray=bytes)=MockResponse().setBody(Buffer().write(body)).setHeader("ETag","\"v1\"")
    private fun resume(offset:Int,etag:String="\"v1\"") {
        File(dir,"model.gguf.part").writeBytes(bytes.copyOf(offset))
        Properties().apply{setProperty("source",server.url("/model.gguf").toString());setProperty("etag",etag)}
            .let{p ->File(dir,"model.gguf.resume").outputStream().use{p.store(it,null)}}
    }
    @Test fun downloadsUnknownLengthAndEmitsProgress()=runBlocking {
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(bytes),777))
        val events=mutableListOf<ModelDownload.Progress>()
        ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest,token="hf_DO_NOT_LEAK",onProgress={events+=it})
        assertArrayEquals(bytes,dest.readBytes());assertNull(server.takeRequest().getHeader("Authorization"))
        assertTrue(events.any{it.phase=="Downloading"});assertEquals("Installed",events.last().phase)
    }
    @Test fun resumesValidated206()=runBlocking {
        resume(30001)
        server.enqueue(response(bytes.copyOfRange(30001,bytes.size)).setResponseCode(206).setHeader("Content-Range","bytes 30001-${bytes.lastIndex}/${bytes.size}"))
        ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)
        val request=server.takeRequest();assertEquals("bytes=30001-",request.getHeader("Range"));assertEquals("\"v1\"",request.getHeader("If-Range"))
        assertArrayEquals(bytes,dest.readBytes())
    }
    @Test fun ignoredRangeDoesNotAppend()=runBlocking {
        resume(20001);server.enqueue(response())
        ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)
        assertArrayEquals(bytes,dest.readBytes())
    }
    @Test fun changedValidatorRestartsSafely()=runBlocking {
        resume(20001);server.enqueue(response(bytes.copyOfRange(20001,bytes.size)).setResponseCode(206)
            .setHeader("Content-Range","bytes 20001-${bytes.lastIndex}/${bytes.size}").setHeader("ETag","\"v2\""))
        server.enqueue(response().setHeader("ETag","\"v2\""))
        ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)
        server.takeRequest();assertNull(server.takeRequest().getHeader("Range"));assertArrayEquals(bytes,dest.readBytes())
    }
    @Test fun range416Restarts()=runBlocking {
        resume(20001);server.enqueue(MockResponse().setResponseCode(416));server.enqueue(response())
        ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)
        assertArrayEquals(bytes,dest.readBytes());assertEquals(2,server.requestCount)
    }
    @Test fun invalidFilesNeverReplaceInstalledModel()=runBlocking {
        dest.writeBytes(bytes)
        server.enqueue(MockResponse().setBody("<html>blocked</html>").setHeader("Content-Type","text/html"))
        val failed=runCatching{ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)}
        assertTrue(failed.isFailure);assertArrayEquals(bytes,dest.readBytes())
    }
    @Test fun checksumFailureDeletesBadPartial()=runBlocking {
        server.enqueue(response());dest.writeBytes(bytes)
        assertTrue(runCatching{ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest,expectedSha256="0".repeat(64))}.isFailure)
        assertArrayEquals(bytes,dest.readBytes());assertFalse(File(dir,"model.gguf.part").exists())
    }
    @Test fun cancellationInterruptsBlockingRead()=runBlocking {
        server.enqueue(response().setBodyDelay(30,TimeUnit.SECONDS))
        val job=launch{ModelDownload(permitTestHttp=true).download(server.url("/model.gguf").toString(),dest)}
        withContext(Dispatchers.IO){server.takeRequest(5,TimeUnit.SECONDS)}
        withTimeout(5000){job.cancelAndJoin()}
        assertFalse(dest.exists())
    }
    @Test fun productionRejectsPlainHttp()=runBlocking {
        assertTrue(runCatching{ModelDownload().download(server.url("/model.gguf").toString(),dest)}.isFailure)
        assertEquals(0,server.requestCount)
    }
}
