package com.meshlit.core.federation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the version-negotiation contract from ADR-008 and the test
 * matrix in plan §0 / §6.5. If any of these fail, the wire handshake
 * is broken across major versions and the federation protocol needs
 * an explicit bump.
 */
class FederationProtocolVersioningTest {

    @Test
    fun major_minor_patch_packing_is_stable() {
        // 1.0.0 → 1*10000 + 0*100 + 0 = 10000
        assertEquals(10000, FederationProtocol.PROTOCOL_VERSION)
        assertEquals(1, FederationProtocol.majorVersion(10000))
        assertEquals(0, FederationProtocol.minorVersion(10000))
        assertEquals(0, FederationProtocol.patchVersion(10000))

        // 1.2.3
        val v123 = 1 * 10000 + 2 * 100 + 3
        assertEquals(1, FederationProtocol.majorVersion(v123))
        assertEquals(2, FederationProtocol.minorVersion(v123))
        assertEquals(3, FederationProtocol.patchVersion(v123))

        // 2.0.0 — next major
        val v200 = 2 * 10000
        assertEquals(2, FederationProtocol.majorVersion(v200))
        assertEquals(0, FederationProtocol.minorVersion(v200))
        assertEquals(0, FederationProtocol.patchVersion(v200))

        // 1.99.99 — large minor / patch
        val v19999 = 1 * 10000 + 99 * 100 + 99
        assertEquals(1, FederationProtocol.majorVersion(v19999))
        assertEquals(99, FederationProtocol.minorVersion(v19999))
        assertEquals(99, FederationProtocol.patchVersion(v19999))
    }

    @Test
    fun formatVersion_renders_dotted_form() {
        assertEquals("1.0.0", FederationProtocol.formatVersion(10000))
        assertEquals("1.2.3", FederationProtocol.formatVersion(10203))
        assertEquals("2.0.0", FederationProtocol.formatVersion(20000))
    }

    @Test
    fun negotiate_returns_null_on_major_mismatch() {
        val local = 10000   // 1.0.0
        val remote = 20000  // 2.0.0
        assertNull(FederationProtocol.negotiate(local, remote))
        assertNull(FederationProtocol.negotiate(remote, local))
    }

    @Test
    fun negotiate_returns_minor_floor_on_same_major() {
        // Same version → returns that version
        assertEquals(10000, FederationProtocol.negotiate(10000, 10000))

        // 1.0.0 + 1.2.3 → returns the lower (1.0.0)
        assertEquals(10000, FederationProtocol.negotiate(10000, 10203))
        assertEquals(10000, FederationProtocol.negotiate(10203, 10000))

        // 1.5.0 + 1.2.0 → returns 1.2.0
        assertEquals(10200, FederationProtocol.negotiate(10500, 10200))
        assertEquals(10200, FederationProtocol.negotiate(10200, 10500))
    }

    @Test
    fun negotiate_far_future_minor_returns_current() {
        val local = 10000                       // 1.0.0
        val remote = 1 * 10_000 + 99 * 100 + 0 // 1.99.0 — major matches, minor wildly high
        // Same major → returns min, which is local
        assertEquals(10000, FederationProtocol.negotiate(local, remote))
    }

    @Test
    fun negotiate_refuses_major_9_to_local_1() {
        val local = 10000
        val remote = 9 * 10_000 + 99 * 100 + 0 // 9.99.0 — different major
        assertNull(FederationProtocol.negotiate(local, remote))
    }

    @Test
    fun negotiate_returns_null_for_zero_remote() {
        // Defensive: a peer that hasn't set its version header should not negotiate.
        assertNull(FederationProtocol.negotiate(10000, 0))
    }

    @Test
    fun protocol_version_constant_is_pinned() {
        // Sentinel — if this test ever fails, the federation
        // protocol version was bumped. Update the test, then audit
        // every peer before rolling the bump.
        assertEquals(10000, FederationProtocol.PROTOCOL_VERSION)
        assertTrue(
            "PROTOCOL_VERSION must be a 1.x version for v1 release",
            FederationProtocol.majorVersion(FederationProtocol.PROTOCOL_VERSION) == 1
        )
    }
}