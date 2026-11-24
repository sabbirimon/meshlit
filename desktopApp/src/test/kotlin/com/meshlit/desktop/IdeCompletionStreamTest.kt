package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class IdeCompletionStreamTest {
    private val first = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello 🙂\"},\"finish_reason\":null}]}\n\n"
    private val last = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":5}}\n\ndata: [DONE]\n\n"
    @Test fun textArrivesBeforeGenerationCompletesAndUsageRemainsNative() {
        val input = PipedInputStream(8192); val output = PipedOutputStream(input)
        val executor = Executors.newSingleThreadExecutor(); val seen = CountDownLatch(1)
        try {
            val result = executor.submit<JsonObject> { input.use { readIdeCompletionStream(it) { chunk ->
                assertNull(chunk["usage"], "Usage must wait for ledger settlement")
                assertTrue(chunk["choices"]!!.jsonArray.all { choice -> choice.jsonObject["finish_reason"] == JsonNull })
                if (chunk.toString().contains("Hello 🙂")) seen.countDown()
            } } }
            output.write(first.toByteArray()); output.flush()
            assertTrue(seen.await(3, TimeUnit.SECONDS), "Text was buffered until completion")
            assertFalse(result.isDone)
            output.write(last.toByteArray()); output.close()
            val completed = result.get(3, TimeUnit.SECONDS)
            assertEquals(5, completed["usage"]!!.jsonObject["completion_tokens"]!!.jsonPrimitive.int)
            assertEquals(1, completed["terminalChunks"]!!.jsonArray.size)
        } finally { output.close(); input.close(); executor.shutdownNow() }
    }
    @Test fun truncatedOrErroredStreamsNeverBecomeSuccess() {
        for (text in listOf(first, first + "data: [DONE]\n\n", "data: {\"error\":\"failed\"}\n\n")) {
            assertFails { readIdeCompletionStream(text.byteInputStream()) {} }
        }
    }
    @Test fun oversizedLinesAndInvalidUsageAreRejected() {
        assertFails { readIdeCompletionStream(("data: " + "x".repeat(65537)).byteInputStream()) {} }
        assertFails { readIdeCompletionStream(last.replace("\"completion_tokens\":5", "\"completion_tokens\":-1").byteInputStream()) {} }
    }
    @Test fun missingUsageRemainsUnknown() {
        val result = readIdeCompletionStream("data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".byteInputStream()) {}
        assertNull(result["usage"])
    }
}
