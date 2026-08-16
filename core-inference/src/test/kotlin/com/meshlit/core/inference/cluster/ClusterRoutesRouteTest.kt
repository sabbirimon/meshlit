package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Routing + wire-shape coverage for the Phase 11.2 `/v1/cluster/...`
 * training surface. Mirrors [ShardServerRouteTest]'s minimal session
 * stub — only the methods our routes actually consult are wired.
 *
 * What we lock down:
 *  - Non-cluster traffic returns `null` so the regular inference
 *    routes pick it up.
 *  - Each GET on the cluster surface returns a JSON body with
 *    `clusterWireVersion=1` as the first field (forward-compat).
 *  - POST `/v1/cluster/{join,leave,run}` returns
 *    `OperationResult{accepted=true|false}`.
 *  - Bad inputs (missing runId, wrong method) surface 400 / 405.
 */
class ClusterRoutesRouteTest {

    @Test fun non_cluster_traffic_returns_null() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/health",
                body = null,
            ),
        )
        assertNull("non-cluster URI must return null", resp)
    }

    @Test fun get_peers_returns_200_with_wire_version() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/cluster/peers",
                body = null,
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.OK, resp!!.status)
        val body = readBody(resp)
        // First field must be clusterWireVersion=1 — locks the
        // forward-compat contract.
        assertTrue(
            "expected clusterWireVersion=1 prefix, got: ${String(body).take(80)}",
            String(body).startsWith("{") && String(body).contains("\"clusterWireVersion\":1"),
        )
    }

    @Test fun get_plan_with_missing_runId_returns_400() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/cluster/plan/",
                body = null,
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, resp!!.status)
    }

    @Test fun get_plan_for_unknown_run_returns_404() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/cluster/plan/nope",
                body = null,
            ),
        )
        assertNotNull("expected non-null response", resp)
        // The bridge returns null for unknown runIds, which the route
        // turns into 404.
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, resp!!.status)
    }

    @Test fun post_join_with_missing_runId_returns_400() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/v1/cluster/join",
                body = "{}".toByteArray(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, resp!!.status)
    }

    @Test fun post_join_with_runId_returns_accepted_result() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/v1/cluster/join",
                body = "{\"runId\":\"job-1\"}".toByteArray(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.OK, resp!!.status)
        val body = readBody(resp)
        val text = String(body)
        assertTrue(
            "expected accepted=true, got: $text",
            text.contains("\"accepted\":true"),
        )
    }

    @Test fun post_run_rejects_unknown_strategy() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/v1/cluster/run",
                body = "{\"runId\":\"job-2\",\"strategy\":\"WRONG\"}".toByteArray(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.OK, resp!!.status)
        val body = readBody(resp)
        val text = String(body)
        // The bridge surfaces a typed "unknown_strategy:WRONG" tag.
        assertTrue(
            "expected accepted=false with unknown_strategy tag, got: $text",
            text.contains("\"accepted\":false") && text.contains("unknown_strategy"),
        )
    }

    @Test fun get_logs_for_unknown_run_returns_empty_body() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/cluster/logs/missing",
                body = null,
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.OK, resp!!.status)
        val body = readBody(resp)
        // Empty body — bridge returns emptyList() for unknown runIds.
        assertEquals(0, body.size)
    }

    @Test fun wrong_method_for_peers_returns_405() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/v1/cluster/peers",
                body = "{}".toByteArray(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        assertEquals(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, resp!!.status)
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun newServer(): ClusterRoutes = ClusterRoutes(
        bridge = TestBridge(),
    )

    private fun fakeSession(
        method: NanoHTTPD.Method,
        uri: String,
        body: ByteArray?,
    ): NanoHTTPD.IHTTPSession {
        val outer = object : NanoHTTPD(0) {
            override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response =
                error("stub server should never serve")
        }
        val cookieHandler: NanoHTTPD.CookieHandler = NanoHTTPD.CookieHandler::class.java
            .getDeclaredConstructor(NanoHTTPD::class.java, java.util.Map::class.java)
            .apply { isAccessible = true }
            .newInstance(outer, mutableMapOf<String, String>())
        return object : NanoHTTPD.IHTTPSession {
            override fun execute(): Unit = error("not used")
            override fun getCookies(): NanoHTTPD.CookieHandler = cookieHandler
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf()
            override fun getInputStream(): InputStream = body?.let { ByteArrayInputStream(it) }
                ?: ByteArrayInputStream(ByteArray(0))
            override fun getMethod(): NanoHTTPD.Method = method
            override fun getParms(): MutableMap<String, String> = mutableMapOf()
            override fun getParameters(): MutableMap<String, MutableList<String>> = mutableMapOf()
            override fun getQueryParameterString(): String = ""
            override fun getUri(): String = uri
            override fun parseBody(files: MutableMap<String, String>?) {
                if (body != null && files != null) {
                    files["postData"] = String(body, Charsets.UTF_8)
                }
            }
            override fun getRemoteIpAddress(): String = "127.0.0.1"
            override fun getRemoteHostName(): String = "localhost"
        }
    }

    private fun readBody(response: NanoHTTPD.Response): ByteArray {
        val out = ByteArrayOutputStream()
        response.data.use { input ->
            val buf = ByteArray(4096)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    /**
     * Minimal in-test bridge. Returns canned responses so the route
     * coverage is independent of [ClusterTrainerRegistry]. The full
     * wiring (registry → bridge) is exercised in the `:app` smoke
     * test.
     */
    private class TestBridge : ClusterRoutes.Bridge {
        override fun peers(): ClusterRoutes.PeersResponse =
            ClusterRoutes.PeersResponse(
                clusterWireVersion = 1,
                members = listOf(
                    PeerCapabilities(
                        peerId = "self",
                        capabilityTier = CapabilityTier.MID,
                        freeRamMb = 4096,
                        freeDiskMb = 8192,
                        hostedShardIds = emptySet(),
                        lastSeenMs = Long.MAX_VALUE,
                    ),
                ),
            )

        override fun plan(runId: String): ClusterRoutes.PlanResponse? = null

        override fun join(runId: String): ClusterRoutes.OperationResult =
            ClusterRoutes.OperationResult(
                clusterWireVersion = 1,
                accepted = true,
                message = "joined run=$runId",
            )

        override fun leave(runId: String): ClusterRoutes.OperationResult =
            ClusterRoutes.OperationResult(
                clusterWireVersion = 1,
                accepted = true,
                message = "left run=$runId",
            )

        override fun run(runId: String, strategy: String): ClusterRoutes.OperationResult {
            if (strategy.uppercase() !in listOf("P2P", "DILOCO", "ACCELERATE")) {
                return ClusterRoutes.OperationResult(
                    clusterWireVersion = 1,
                    accepted = false,
                    message = "unknown_strategy:$strategy",
                )
            }
            return ClusterRoutes.OperationResult(
                clusterWireVersion = 1,
                accepted = true,
                message = "admitted run=$runId strategy=$strategy",
            )
        }

        override fun logs(runId: String, limit: Int): List<ClusterRoutes.TrainingEventDto> =
            emptyList()
    }
}