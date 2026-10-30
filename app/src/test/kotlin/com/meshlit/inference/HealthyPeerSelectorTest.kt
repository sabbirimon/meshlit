package com.meshlit.inference

import org.junit.Assert.*
import org.junit.Test

class HealthyPeerSelectorTest {
    private val now = 100_000L
    private fun healthy(at: Long = now) = PeerHealthCache.PeerHealth(true, true, at)

    @Test fun `eligible peers receive alternating turns without duplicate weighting`() {
        val selector = HealthyPeerSelector()
        val peers = listOf("a", "a", "b")
        val health = mapOf("a" to healthy(), "b" to healthy())
        assertEquals(listOf("a", "b", "a", "b"), List(4) { selector.pick(peers, health, now) })
    }

    @Test fun `health never admits a peer outside approved candidates`() {
        assertNull(HealthyPeerSelector().pick(emptyList(), mapOf("unapproved" to healthy()), now))
    }

    @Test fun `stale future unavailable and unloaded peers are excluded`() {
        val health = mapOf(
            "stale" to healthy(now - PeerHealthCache.STALE_AFTER_MS - 1),
            "future" to healthy(now + 1),
            "unavailable" to healthy().copy(ok = false),
            "unloaded" to healthy().copy(modelLoaded = false),
            "valid" to healthy(),
        )
        assertEquals("valid", HealthyPeerSelector().pick(health.keys.toList(), health, now))
    }

    @Test fun `removing the previous peer does not retain its turn`() {
        val selector = HealthyPeerSelector()
        val health = mapOf("a" to healthy(), "b" to healthy())
        assertEquals("a", selector.pick(listOf("a", "b"), health, now))
        assertEquals("b", selector.pick(listOf("b"), health, now))
        assertNull(selector.pick(listOf("b"), health, now + PeerHealthCache.STALE_AFTER_MS + 1))
    }
}
