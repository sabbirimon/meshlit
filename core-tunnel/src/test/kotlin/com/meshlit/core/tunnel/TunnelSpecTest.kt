package com.meshlit.core.tunnel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSpecTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun round_trip_default_tcp() {
        val spec = TunnelSpec(
            id = "spec-1",
            label = "Meshlit over home",
            localPort = 8080,
            remoteHost = "127.0.0.1",
            remotePort = 8080,
        )
        val text = json.encodeToString(TunnelSpec.serializer(), spec)
        val decoded = json.decodeFromString(TunnelSpec.serializer(), text)
        assertEquals(spec, decoded)
        assertEquals(TunnelMode.TCP, decoded.mode)
    }

    @Test
    fun round_trip_mesh_mode() {
        val spec = TunnelSpec(
            id = "spec-2",
            label = "Cluster mesh",
            mode = TunnelMode.MESH,
            localPort = 9090,
            remoteHost = "node-b",
            remotePort = 9090,
        )
        val text = json.encodeToString(TunnelSpec.serializer(), spec)
        val decoded = json.decodeFromString(TunnelSpec.serializer(), text)
        assertEquals(spec, decoded)
    }

    @Test
    fun invalid_port_is_rejected() {
        try {
            TunnelSpec(
                id = "x",
                label = "x",
                localPort = 0,
                remoteHost = "127.0.0.1",
                remotePort = 80,
            )
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("local port"))
        }
    }

    @Test
    fun in_memory_registry_round_trip() {
        val reg = InMemoryTunnelRegistry()
        val spec = TunnelSpec(
            id = "a",
            label = "a",
            localPort = 7000,
            remoteHost = "127.0.0.1",
            remotePort = 7000,
        )
        reg.upsert(spec)
        assertEquals(listOf(spec), reg.list())
        reg.remove("a")
        assertEquals(emptyList<TunnelSpec>(), reg.list())
    }
}
