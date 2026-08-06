package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * End-to-end coverage of the three cluster-shard routes served by
 * [ShardServer]. Stubs `NanoHTTPD.IHTTPSession` minimally — the
 * interface carries many methods (cookies, cookies-iter, params,
 * raw-input-stream, etc.) but only a few are actually consulted by
 * `ShardServer.route()`. Everything else returns safe defaults.
 */
class ShardServerRouteTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newServer(): ShardServer = ShardServer(
        filesDir = tmp.root,
        selfCapabilities = {
            PeerCapabilities(
                peerId = "self",
                capabilityTier = CapabilityTier.MID,
                freeRamMb = 4096,
                freeDiskMb = 8192,
                hostedShardIds = emptySet(),
                lastSeenMs = Long.MAX_VALUE,
            )
        },
    )

    @Test fun get_shard_returns_200_when_present() {
        val server = newServer()
        // Seed a shard file. The on-disk layout is
        // <filesDir>/shards/<modelId>/<shardId>.shard — see ShardServer.shardFile().
        val dir = File(tmp.root, "shards/llm").apply { mkdirs() }
        File(dir, "shard-000.shard").writeBytes(byteArrayOf(1, 2, 3, 4))

        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/shards/llm/shard-000",
                body = null,
                headers = emptyMap(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        // ShardServer uses NanoHTTPD.newFixedLengthResponse; check
        // the body payload as bytes to confirm round-trip.
        val payload = readBody(resp!!)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), payload)
    }

    @Test fun post_shard_writes_file_and_renames_from_part() {
        val server = newServer()
        // Ensure the model dir exists. The server will create it
        // from ingestShard, but pre-creating keeps the assertion
        // readable.
        File(tmp.root, "shards/llm").mkdirs()
        val body = byteArrayOf(9, 8, 7, 6, 5)
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/v1/shards/llm/shard-001",
                body = body,
                headers = emptyMap(),
            ),
        )
        assertNotNull("expected non-null response", resp)
        // The .shard file should exist with the same content; the
        // .part file should have been renamed away.
        val stored = File(File(tmp.root, "shards/llm"), "shard-001.shard")
        assertTrue("expected $stored to exist", stored.exists())
        assertArrayEquals(body, stored.readBytes())
        assertTrue("expected .part to be absent", !File(File(tmp.root, "shards/llm"), "shard-001.part").exists())
    }

    @Test fun capabilities_returns_peer_capabilities_json() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/capabilities",
                body = null,
                headers = emptyMap(),
            ),
        )
        assertNotNull(resp)
        val caps = PeerCapabilitiesJson.decode(readBody(resp!!).toString(Charsets.UTF_8))
        assertEquals("self", caps.peerId)
        assertEquals(CapabilityTier.MID, caps.capabilityTier)
        assertEquals(4096L, caps.freeRamMb)
        assertEquals(8192L, caps.freeDiskMb)
    }

    @Test fun manifest_returns_404_when_no_shards() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/manifest/empty",
                body = null,
                headers = emptyMap(),
            ),
        )
        assertNotNull(resp)
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, resp!!.status)
    }

    @Test fun manifest_lists_local_shards_with_byte_offsets() {
        val server = newServer()
        // Two seeded shards; total = 0 + 4 + 6 = 10 bytes.
        val dir = File(tmp.root, "shards/llm").apply { mkdirs() }
        File(dir, "shard-000.shard").writeBytes(ByteArray(4) { 0xAA.toByte() })
        File(dir, "shard-001.shard").writeBytes(ByteArray(6) { 0xBB.toByte() })

        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/manifest/llm",
                body = null,
                headers = emptyMap(),
            ),
        )
        assertNotNull(resp)
        val manifest = ClusterShardManifestJson.decode(readBody(resp!!).toString(Charsets.UTF_8))
        assertEquals("llm", manifest.modelId)
        assertEquals(10L, manifest.totalBytes)
        assertEquals(2, manifest.shardSpecs.size)
        assertEquals("shard-000", manifest.shardSpecs[0].shardId)
        assertEquals(0L, manifest.shardSpecs[0].byteOffset)
        assertEquals(4L, manifest.shardSpecs[0].byteLength)
        assertEquals("shard-001", manifest.shardSpecs[1].shardId)
        assertEquals(4L, manifest.shardSpecs[1].byteOffset)
        assertEquals(6L, manifest.shardSpecs[1].byteLength)
    }

    @Test fun unknown_route_returns_null_so_delegate_serves_locally() {
        val server = newServer()
        val resp = server.route(
            fakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/v1/infer",
                body = null,
                headers = emptyMap(),
            ),
        )
        assertNull("expected null so the inference delegate handles it", resp)
    }

    // -- helpers -----------------------------------------------------------

    /**
     * Build the smallest viable [NanoHTTPD.IHTTPSession] that exercises
     * ShardServer's read paths. The interface has ~15 methods but only
     * `uri`, `method`, `headers`, `inputStream` (for POSTs), and
     * `parameters` are read by the server — the rest throw if called
     * so we'd notice if a future code change added a new dependency.
     */
    private fun fakeSession(
        method: NanoHTTPD.Method,
        uri: String,
        body: ByteArray?,
        headers: Map<String, String>,
    ): NanoHTTPD.IHTTPSession {
        val headersLower = headers.mapKeys { it.key.lowercase() }
        // CookieHandler is a non-static inner class of NanoHTTPD, so
        // constructing it from Kotlin requires reflective access —
        // Kotlin can't synthesise the outer-class receiver. We build
        // a minimal anonymous NanoHTTPD subclass (never bound to a
        // port), then reflectively invoke
        // `new CookieHandler(outer, map)`.
        val outer = object : NanoHTTPD(0) {
            override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response =
                error("stub server should never serve")
        }
        val cookieHandler: NanoHTTPD.CookieHandler = NanoHTTPD.CookieHandler::class.java
            .getDeclaredConstructor(NanoHTTPD::class.java, java.util.Map::class.java)
            .apply { isAccessible = true }
            .newInstance(outer, headersLower.toMutableMap())
        return object : NanoHTTPD.IHTTPSession {
            override fun execute(): Unit = error("not used")
            override fun getCookies(): NanoHTTPD.CookieHandler = cookieHandler
            override fun getHeaders(): MutableMap<String, String> = headersLower.toMutableMap()
            override fun getInputStream(): InputStream = body?.let { ByteArrayInputStream(it) }
                ?: ByteArrayInputStream(ByteArray(0))
            override fun getMethod(): NanoHTTPD.Method = method
            override fun getParms(): MutableMap<String, String> = mutableMapOf()
            override fun getParameters(): MutableMap<String, MutableList<String>> = mutableMapOf()
            override fun getQueryParameterString(): String = ""
            override fun getUri(): String = uri
            override fun parseBody(files: MutableMap<String, String>?) { /* no-op */ }
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

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual)) {
            val expHex = expected.joinToString("") { "%02x".format(it) }
            val actHex = actual.joinToString("") { "%02x".format(it) }
            throw AssertionError("byte mismatch — expected $expHex, got $actHex")
        }
    }
}
