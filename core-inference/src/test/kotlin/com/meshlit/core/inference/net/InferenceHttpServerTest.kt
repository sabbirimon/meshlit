package com.meshlit.core.inference.net

import com.meshlit.core.inference.InferenceCoordinator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Wire-surface tests for [InferenceHttpServer]. Spins up the
 * NanoHTTPD server on a free port and exercises the three
 * production endpoints via OkHttp:
 *
 *  - `GET /v1/health` — JSON shape, status, engine, port.
 *  - `POST /v1/infer` — returns the typed `no_engine_for_format`
 *    failure as an SSE stream (no real GGUF in classpath).
 *  - `GET /v1/{junk}` — 404.
 *
 * The test does NOT need a real GGUF — the InferenceCoordinator
 * defaults to a no-op engine on the JVM, so the server returns
 * well-formed SSE error events instead of crashing.
 */
class InferenceHttpServerTest {

    private lateinit var server: InferenceHttpServer
    private var port: Int = 0
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        // The NanoHTTPD port argument is a hint; the actual port
        // is bound by `boundPort` after `start()` resolves the
        // ephemeral fallback. We use port 0 (any free port) so
        // CI can run multiple test instances in parallel.
        server = InferenceHttpServer(
            coordinator = InferenceCoordinator(),
            router = NoopRouter,
            forwarder = LocalForwarder,
        )
        server.start()
        port = server.boundPort
        assertTrue("server should bind to a non-zero port", port > 0)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `health endpoint returns 200 with stable JSON shape`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/v1/health")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
            val body = resp.body?.string().orEmpty()
            assertTrue("body should declare status", body.contains("\"status\""))
            assertTrue("body should declare engine", body.contains("\"engine\""))
            assertTrue("body should declare port", body.contains("\"port\""))
        }
    }

    @Test
    fun `infer endpoint returns a typed failure when no GGUF is loaded`() {
        val payload = """{"prompt":"hello","maxTokens":16}"""
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/v1/infer")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
            val body = resp.body?.string().orEmpty()
            // The no-op engine path emits an SSE `event: error`
            // with `tag: no_engine_for_format` (or similar).
            // We don't pin the exact tag, but the body must be
            // valid SSE and carry the error framing.
            assertTrue(
                "body should contain SSE error framing",
                body.contains("event: error") || body.contains("\"tag\":"),
            )
        }
    }

    @Test
    fun `infer rejects non-JSON payloads without crashing`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/v1/infer")
            .post("not-json".toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            // The server returns 400 / 200 with an error event; both
            // are acceptable. The contract is: no crash, no 500.
            assertTrue(
                "status should be client-error, not 5xx",
                resp.code in 200..499,
            )
            assertNotNull(resp.body)
        }
    }

    @Test
    fun `unknown path returns 404`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/v1/this-does-not-exist")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            assertEquals(404, resp.code)
        }
    }

    @Test
    fun `health on root path returns 404 not 200`() {
        val req = Request.Builder()
            .url("http://127.0.0.1:$port/")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            assertEquals(404, resp.code)
        }
    }

    private object NoopRouter : RouterRef {
        override suspend fun decideFor(
            request: InferRequest,
            hints: RequestHints?,
        ): RouterDecision = RouterDecision.local("test-noop")
    }

    private object LocalForwarder : Forwarder {
        override suspend fun forwardAndStream(
            peerBaseUrl: String,
            request: InferRequest,
            hints: RequestHints?,
            onToken: suspend (InferTokenEvent) -> Unit,
            onDone: suspend (InferDoneEvent) -> Unit,
            onError: suspend (InferErrorEvent) -> Unit,
        ): Result<Unit> = Result.failure(UnsupportedOperationException("LocalForwarder not used in test"))
    }
}
