package com.meshlit.util

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bound accepted sockets before they reach TLS, HTTP parsing or request authorization. */
internal class BoundedHttpRunner(threads: Int, name: String) : NanoHTTPD.AsyncRunner {
    private val clients = mutableSetOf<NanoHTTPD.ClientHandler>()
    private var stopped = false
    private val workers = ThreadPoolExecutor(
        0, threads, 10L, TimeUnit.SECONDS, SynchronousQueue(),
    ) { task -> Thread(task, name).apply { isDaemon = true } }

    @Synchronized override fun exec(client: NanoHTTPD.ClientHandler) {
        if (stopped) {
            client.close()
            return
        }
        clients.add(client)
        try { workers.execute(client) }
        catch (_: RejectedExecutionException) { clients.remove(client); client.close() }
    }

    @Synchronized override fun closed(client: NanoHTTPD.ClientHandler) {
        clients.remove(client)
    }

    override fun closeAll() {
        val closing = synchronized(this) {
            stopped = true
            clients.toList().also { clients.clear() }
        }
        closing.forEach { it.close() }
        workers.shutdownNow()
    }
}
