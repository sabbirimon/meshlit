package com.meshlit.core.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket

class NetworkDiagnosticsTest {
    @Test fun explicitTargetAndAuthorizationAreRequired() = runBlocking {
        val diagnostics = NetworkDiagnostics()
        assertTrue(runCatching { diagnostics.tcp("127.0.0.1", listOf(80), false) }.isFailure)
        assertTrue(runCatching { diagnostics.tcp("127.0.0.1/24", listOf(80), true) }.isFailure)
        assertTrue(runCatching { diagnostics.tcp("127.0.0.1", (1..17).toList(), true) }.isFailure)
    }
    @Test fun reportsOpenLoopbackPortWithoutRoot() = runBlocking {
        ServerSocket(0).use { server ->
            val result = NetworkDiagnostics().tcp("127.0.0.1", listOf(server.localPort), true).single()
            assertEquals("open", result.state)
            assertEquals(server.localPort, result.port)
        }
    }
}
