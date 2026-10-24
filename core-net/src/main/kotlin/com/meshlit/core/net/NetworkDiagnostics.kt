package com.meshlit.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

data class PortDiagnostic(val address: String, val port: Int, val state: String, val durationMs: Long)

/** Read-only diagnostics for one explicitly selected, authorized host. */
class NetworkDiagnostics {
    suspend fun dns(host: String): List<String> = runInterruptible(Dispatchers.IO) {
        validateHost(host)
        InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct()
    }

    suspend fun tcp(host: String, ports: List<Int>, authorized: Boolean): List<PortDiagnostic> =
        withContext(Dispatchers.IO) {
            require(authorized) { "Use --authorized for a host you own or may test" }
            validateHost(host)
            require(ports.isNotEmpty() && ports.size <= 16 && ports.all { it in 1..65535 }) { "Select 1..16 explicit ports" }
            // Resolve once: no subnet expansion, service exploitation or raw packets.
            val address = InetAddress.getAllByName(host).first()
            ports.distinct().map { port ->
                coroutineContext.ensureActive()
                val start = System.nanoTime()
                val state = try {
                    Socket().use { socket -> socket.connect(InetSocketAddress(address, port), 1000) }
                    "open"
                } catch (_: SocketTimeoutException) { "timeout" }
                  catch (_: java.net.ConnectException) { "closed" }
                  catch (_: java.io.IOException) { "unreachable" }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                delay(100)
                PortDiagnostic(address.hostAddress ?: host, port, state, elapsed)
            }
        }

    suspend fun interfaces(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().associate { iface ->
            iface.name to iface.inetAddresses.toList().mapNotNull { it.hostAddress }
        }
    }

    private fun validateHost(host: String) {
        require(host.isNotBlank() && host.length <= 253 &&
            host.all { it.isLetterOrDigit() || it in ".-:_" }) { "Use one hostname or IP address" }
    }
}
