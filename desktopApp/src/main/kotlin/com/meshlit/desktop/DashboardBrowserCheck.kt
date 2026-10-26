package com.meshlit.desktop

import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFrame
import javax.swing.SwingUtilities
import com.sun.net.httpserver.HttpServer

/** Headful qualification of the actual browser and read-page bridge; no vendor account or model calls. */
internal fun dashboardBrowserCheck(output: String) {
    val result = CompletableFuture<String>()
    val navigation = CompletableFuture<String>()
    val failed = CompletableFuture<Unit>()
    val closedPort = java.net.ServerSocket(0).use { it.localPort }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
        val html = """<!doctype html><title>Meshlit browser qualification</title><body><h1>Meshlit browser qualification</h1><input type=password value=DO_NOT_CAPTURE_THIS><textarea>DO_NOT_CAPTURE_THIS_EITHER</textarea><div>API key: sk-exampleSecretNeverExpose123</div><script>document.body.append(' JavaScript dashboard loaded');</script></body>""".toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, html.size.toLong()); exchange.responseBody.use { it.write(html) }
    }
    server.createContext("/redirect") { exchange ->
        exchange.responseHeaders.set("Location", "http://127.0.0.1:$closedPort/review")
        exchange.sendResponseHeaders(302, -1); exchange.close()
    }
    server.start()
    var session: DashboardSession? = null
    var frame: JFrame? = null
    try {
        session = DashboardSession.create("http://127.0.0.1:${server.address.port}",
            { state ->
                if(state.startsWith("Dashboard session")) session?.readPage()
                if(state.startsWith("Dashboard could not load")) failed.complete(Unit)
            }, { target -> if(target != null) navigation.complete(target) }, { result.complete(it) })
        val active = session
        SwingUtilities.invokeAndWait {
            val compose = ComposePanel().apply { setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize()) {
                        Text("Meshlit · Embedded dashboard qualification", Modifier.padding(12.dp))
                        SwingPanel(factory = { active.component }, modifier = Modifier.fillMaxSize())
                    }
                }
            } }
            frame = JFrame("Meshlit · Embedded browser check").apply { setSize(900,600); add(compose); isVisible = true }
        }
        val text = result.get(60, TimeUnit.SECONDS)
        check(text.contains("JavaScript dashboard loaded")) { "Live DOM capture did not run" }
        check(!text.contains("DO_NOT_CAPTURE") && !text.contains("sk-example")) { "Sensitive fixture text was not excluded" }
        active.allow("http://127.0.0.1:${server.address.port}/redirect")
        check(navigation.get(15, TimeUnit.SECONDS) == "http://127.0.0.1:$closedPort/review")
        active.allow("http://127.0.0.1:$closedPort/review")
        failed.get(15, TimeUnit.SECONDS)
    } finally {
        SwingUtilities.invokeAndWait { session?.close() }
        session?.disposed?.get(15, TimeUnit.SECONDS)
        SwingUtilities.invokeAndWait { frame?.dispose() }
        server.stop(0); DashboardEngine.shutdown()
    }
    Files.writeString(Path.of(output), "Embedded WebView in Compose SwingPanel loaded loopback HTML, executed page JavaScript and returned reviewed DOM text. Password/textarea values excluded; test API key redacted. Cross-origin redirect required confirmation; an unavailable gateway reported a load failure. Browser and engine closed cleanly. No vendor account qualification.\n")
    println("Embedded browser check passed, including shutdown")
}
