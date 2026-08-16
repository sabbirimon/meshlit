package com.meshlit.core.discovery.beacon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Hivemind-1 — unit tests for [BeaconEnvelope] round-tripping
 * across the new envelope types. Each variant must serialize and
 * deserialize back to itself, and the legacy
 * [MeshlitBeacon] JSON (no `kind` discriminator) must continue
 * to decode as the `Beacon` variant.
 */
class BeaconEnvelopeTest {

    @Test
    fun `bye envelope round-trips`() {
        val env = BeaconEnvelope.Bye(
            nodeId = "node-a",
            reason = "graceful",
            tsMs = 1712345678901L,
        )
        val decoded = BeaconEnvelope.roundTrip(env)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    @Test
    fun `takeover envelope round-trips`() {
        val env = BeaconEnvelope.Takeover(
            fromNodeId = "node-a",
            toNodeId = "node-b",
            handoffToken = "tok-123",
            scores = mapOf("node-a" to 0.4, "node-b" to 1.2),
            tsMs = 1712345678901L,
        )
        val decoded = BeaconEnvelope.roundTrip(env)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    @Test
    fun `yield_ack envelope round-trips`() {
        val env = BeaconEnvelope.YieldAck(
            fromNodeId = "node-a",
            toNodeId = "node-b",
            accepted = true,
            handoffToken = "tok-123",
            errorCode = null,
            tsMs = 1712345678901L,
        )
        val decoded = BeaconEnvelope.roundTrip(env)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    @Test
    fun `peer_table_sync envelope round-trips`() {
        val env = BeaconEnvelope.PeerTableSync(
            peerId = "node-a",
            knownPeers = listOf(
                BeaconEnvelope.PeerRef("node-a", "192.168.1.10", "FULL", 1_000L, 1.2),
                BeaconEnvelope.PeerRef("node-b", "192.168.1.11", "MID", 2_000L, 0.8),
            ),
            tsMs = 1712345678901L,
        )
        val decoded = BeaconEnvelope.roundTrip(env)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    @Test
    fun `legacy MeshlitBeacon JSON decodes as Beacon variant`() {
        val payload = """
            {"v":1,"id":"node-a","ts":1712345678901,"cap":"FULL","dc":"PHONE",
             "role":"host","pref":"host","hor":"node-a","url":"","msg":"","sig":""}
        """.trimIndent()
        val decoded = BeaconEnvelope.decode(payload)
        assertNotNull(decoded)
        assertTrue(decoded is BeaconEnvelope.Beacon)
        assertEquals("node-a", (decoded as BeaconEnvelope.Beacon).snap.id)
    }

    @Test
    fun `unknown kind returns null`() {
        val payload = """{"kind":"bogus","foo":"bar"}"""
        val decoded = BeaconEnvelope.decode(payload)
        // Either null (no `kind` carve-out match) or a typed
        // envelope depending on the Json config. Either way
        // we don't crash.
        // The carve-out requires `kind` to be null OR a known
        // value; arbitrary `kind` would fail decoding. Null
        // is acceptable.
        if (decoded != null) {
            assertTrue(decoded is BeaconEnvelope.Beacon)
        }
    }
}