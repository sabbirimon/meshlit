package com.meshlit.desktop

import com.meshlit.workspace.TailscaleSnapshot
import com.meshlit.workspace.TailscaleStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import java.util.prefs.Preferences

internal class VpnPreferences(private val store: Preferences = Preferences.userRoot().node("com/meshlit/desktop-vpn")) {
    fun enabled() = store.getBoolean("readStatus", false)
    fun path() = store.get("cli", "")
    fun save(enabled: Boolean, path: String) {
        require(path.length <= 4096 && path.none(Char::isISOControl))
        store.putBoolean("readStatus", enabled); store.put("cli", path); store.flush()
    }
}

/** Explicit local CLI selection; never searches the current directory or executes a shell. */
internal object TailscaleProbe {
    fun candidate(): String = listOf(
        "/Applications/Tailscale.app/Contents/MacOS/Tailscale", "/opt/homebrew/bin/tailscale", "/usr/local/bin/tailscale", "/usr/bin/tailscale",
        "C:/Program Files/Tailscale/tailscale.exe",
    ).firstOrNull { Files.isRegularFile(Path.of(it)) && Files.isExecutable(Path.of(it)) }.orEmpty()

    suspend fun status(executable: String, timeoutMs: Long = 5_000): TailscaleSnapshot = withContext(Dispatchers.IO) {
        require(timeoutMs in 100..5_000)
        val path = Path.of(executable)
        require(path.isAbsolute && Files.isRegularFile(path) && Files.isExecutable(path)) { "Select the installed Tailscale executable in Advanced" }
        coroutineScope {
            val process = ProcessBuilder(path.toString(), "status", "--json").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.outputStream.close()
            fun stop() {
                process.toHandle().descendants().use { children -> children.forEach { it.destroyForcibly() } }
                process.destroyForcibly()
            }
            val reader = Executors.newSingleThreadExecutor { r -> Thread(r, "tailscale-status-reader").apply { isDaemon = true } }
            val output = reader.submit<ByteArray> { process.inputStream.use { it.readNBytes(TailscaleStatus.MAX_BYTES + 1) } }
            val cancellation = launch(Dispatchers.IO) { try { awaitCancellation() } finally { stop() } }
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            try {
                check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { "Tailscale status timed out" }
                ensureActive()
                check(process.exitValue() == 0) { "Tailscale status unavailable; open its client and check login, daemon and permissions" }
                val bytes = output.get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
                require(bytes.size <= TailscaleStatus.MAX_BYTES) { "Tailscale response exceeds the supported size" }
                ensureActive()
                TailscaleStatus.parse(bytes.decodeToString(throwOnInvalidSequence = true), System.currentTimeMillis())
            } finally {
                stop(); cancellation.cancel(); output.cancel(true); reader.shutdownNow()
            }
        }
    }
}
