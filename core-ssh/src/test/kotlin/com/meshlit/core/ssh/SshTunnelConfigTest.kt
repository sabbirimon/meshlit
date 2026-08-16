package com.meshlit.core.ssh

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshTunnelConfigTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun round_trip_password_auth() {
        val config = SshTunnelConfig(
            id = "config-1",
            label = "Home gateway",
            host = "gateway.example.com",
            username = "alice",
            auth = SshAuth.Password("hunter2"),
            remoteForwardHost = "127.0.0.1",
            remoteForwardPort = 8080,
        )
        val text = json.encodeToString(SshTunnelConfig.serializer(), config)
        val decoded = json.decodeFromString(SshTunnelConfig.serializer(), text)
        assertEquals(config, decoded)
        assertEquals("alice@gateway.example.com:22", config.fingerprint())
    }

    @Test
    fun round_trip_private_key_auth() {
        val config = SshTunnelConfig(
            id = "config-2",
            label = "Lab gateway",
            host = "10.0.0.5",
            username = "ops",
            auth = SshAuth.PrivateKeyInline(pem = "-----BEGIN...", passphrase = "secret"),
            remoteForwardHost = "127.0.0.1",
            remoteForwardPort = 9090,
        )
        val text = json.encodeToString(SshTunnelConfig.serializer(), config)
        val decoded = json.decodeFromString(SshTunnelConfig.serializer(), text)
        assertEquals(config, decoded)
    }

    @Test
    fun invalid_port_is_rejected() {
        try {
            SshTunnelConfig(
                id = "bad",
                label = "Bad",
                host = "x",
                port = 70000,
                username = "u",
                auth = SshAuth.Password("p"),
                remoteForwardHost = "127.0.0.1",
                remoteForwardPort = 80,
            )
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("port"))
        }
    }

    @Test
    fun blank_host_is_rejected() {
        try {
            SshTunnelConfig(
                id = "bad",
                label = "Bad",
                host = "  ",
                username = "u",
                auth = SshAuth.Password("p"),
                remoteForwardHost = "127.0.0.1",
                remoteForwardPort = 80,
            )
            assertTrue("expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("host"))
        }
    }
}
