package com.meshlit.inference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for the pure-logic portions of [PeerRegistry]:
 *  - [PeerRegistry.normalize] rejects bad input (hostnames, IPv6,
 *    empty strings, out-of-range octets).
 *  - [PeerRegistry.add] / [PeerRegistry.remove] / [PeerRegistry.snapshot]
 *    round-trip through the DataStore.
 *  - Duplicate [PeerRegistry.add] calls are no-ops.
 *  - [PeerRegistry.replaceAll] deduplicates and validates.
 *
 * Uses [PreferenceDataStoreFactory.create] with a temp file so the
 * persistence path is real (no in-memory mocks). The test runs on
 * the host JVM — no Android instrumentation required.
 */
class PeerRegistryTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var tempFile: File
    private lateinit var registry: PeerRegistry

    @Before
    fun setUp() {
        tempFile = File.createTempFile("peerregistry-test", ".preferences_pb")
        tempFile.deleteOnExit()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { tempFile })
        registry = PeerRegistry(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (tempFile.exists()) tempFile.delete()
    }

    // -----------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------
    @Test
    fun `normalize accepts dotted IPv4 addresses`() {
        assertEquals("192.168.1.42", PeerRegistry.normalize("192.168.1.42"))
        assertEquals("10.0.0.1", PeerRegistry.normalize("10.0.0.1"))
        assertEquals("0.0.0.0", PeerRegistry.normalize("0.0.0.0"))
    }

    @Test
    fun `normalize strips scheme and trailing path`() {
        assertEquals("192.168.1.42", PeerRegistry.normalize("http://192.168.1.42"))
        assertEquals("192.168.1.42", PeerRegistry.normalize("https://192.168.1.42/foo"))
        assertEquals("192.168.1.42", PeerRegistry.normalize("192.168.1.42:8080"))
    }

    @Test
    fun `normalize rejects hostnames and IPv6`() {
        assertNull(PeerRegistry.normalize("not-a-host"))
        assertNull(PeerRegistry.normalize("::1"))
        assertNull(PeerRegistry.normalize("fe80::1"))
        assertNull(PeerRegistry.normalize(""))
        assertNull(PeerRegistry.normalize("   "))
    }

    @Test
    fun `normalize rejects out-of-range octets`() {
        assertNull(PeerRegistry.normalize("256.0.0.1"))
        assertNull(PeerRegistry.normalize("1.2.3.4.5"))
        assertNull(PeerRegistry.normalize("1.2.3"))
        assertNull(PeerRegistry.normalize("-1.2.3.4"))
    }

    // -----------------------------------------------------------------
    // DataStore round-trip
    // -----------------------------------------------------------------
    @Test
    fun `add inserts ip and snapshot reads it back`() = runBlocking {
        registry.add("192.168.1.10")
        assertEquals(listOf("192.168.1.10"), registry.snapshot())
    }

    @Test
    fun `add dedups duplicate ips`() = runBlocking {
        registry.add("192.168.1.10")
        registry.add("192.168.1.10")
        registry.add("192.168.1.10")
        assertEquals(listOf("192.168.1.10"), registry.snapshot())
    }

    @Test
    fun `add is no-op on invalid input`() = runBlocking {
        registry.add("not-a-host")
        registry.add("::1")
        registry.add("")
        assertEquals(emptyList<String>(), registry.snapshot())
    }

    @Test
    fun `remove drops the matching ip`() = runBlocking {
        registry.add("192.168.1.10")
        registry.add("192.168.1.20")
        registry.remove("192.168.1.10")
        assertEquals(listOf("192.168.1.20"), registry.snapshot())
    }

    @Test
    fun `remove is no-op on absent ip`() = runBlocking {
        registry.add("192.168.1.10")
        registry.remove("192.168.1.99")
        assertEquals(listOf("192.168.1.10"), registry.snapshot())
    }

    @Test
    fun `replaceAll dedups and validates`() = runBlocking {
        registry.add("192.168.1.10")
        registry.replaceAll(
            listOf(
                "192.168.1.10",
                "192.168.1.20",
                "not-a-host",
                "192.168.1.10", // dup
            ),
        )
        val snap = registry.snapshot()
        assertEquals(2, snap.size)
        assertTrue(snap.contains("192.168.1.10"))
        assertTrue(snap.contains("192.168.1.20"))
        assertFalse(snap.contains("not-a-host"))
    }

    @Test
    fun `snapshot reads persisted state across registry restarts`() = runBlocking {
        registry.add("192.168.1.10")
        registry.add("192.168.1.20")

        // Spin up a second registry backed by the same DataStore.
        val second = PeerRegistry(dataStore)
        val snap = second.snapshot()
        assertEquals(setOf("192.168.1.10", "192.168.1.20"), snap.toSet())
    }
}
