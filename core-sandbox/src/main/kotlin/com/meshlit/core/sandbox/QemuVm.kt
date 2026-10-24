package com.meshlit.core.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

enum class GuestArchitecture { X86_64, AARCH64 }
enum class VmState { STOPPED, STARTING, RUNNING, SSH_READY, FAILED }

data class VmConfig(
    val executable: String, val disk: String, val architecture: GuestArchitecture,
    val kernel: String = "", val initrd: String = "", val memoryMb: Int = 512,
    val cpus: Int = 1, val sshPort: Int = 2222, val vncDisplay: Int = 1,
    val enableDesktop: Boolean = false,
    val allowOutboundNetwork: Boolean = false,
    val persistDisk: Boolean = false,
) {
    fun argv(): List<String> {
        require(memoryMb in 256..4096 && cpus in 1..4) { "VM resource limits exceeded" }
        require(sshPort in 1024..65535 && vncDisplay in 1..99 && sshPort != 5900 + vncDisplay)
        fun checked(path: String, executable: Boolean = false): String {
            val file = File(path)
            require(file.isAbsolute && file.isFile && (!executable || file.canExecute())) { "Missing VM artifact: $path" }
            return file.canonicalPath.replace(",", ",,")
        }
        // Executable is an argv item rather than a QEMU option value.
        val binary = checked(executable, true).replace(",,", ",")
        val args = mutableListOf(binary, "-accel", "tcg", "-m", memoryMb.toString(),
            "-smp", cpus.toString(), "-no-reboot",
            "-drive", "file=${checked(disk)},if=virtio,format=qcow2",
            "-netdev", "user,id=net0,restrict=${if(allowOutboundNetwork) "off" else "on"},hostfwd=tcp:127.0.0.1:$sshPort-:22",
            "-device", "virtio-net-pci,netdev=net0", "-monitor", "none",
            "-serial", "stdio")
        if (!persistDisk) args += "-snapshot"
        when (architecture) {
            GuestArchitecture.X86_64 -> args += listOf("-machine", "q35")
            GuestArchitecture.AARCH64 -> {
                args += listOf("-machine", "virt", "-cpu", "cortex-a72", "-kernel", checked(kernel),
                    "-append", "console=ttyAMA0 root=/dev/vda rw")
                if (initrd.isNotBlank()) args += listOf("-initrd", checked(initrd))
            }
        }
        if (enableDesktop) {
            args += listOf("-device", "virtio-gpu-pci", "-device", "qemu-xhci",
                "-device", "usb-kbd", "-device", "usb-tablet",
                "-vnc", "127.0.0.1:$vncDisplay")
        } else args += listOf("-display", "none")
        return args
    }
}

/** Foreground/session-bound VM controller. Persistent background FGS is separate. */
class QemuVm(private val workspace: File) {
    @Volatile private var process: Process? = null
    @Volatile var state: VmState = VmState.STOPPED
        private set
    @Volatile private var active: VmConfig? = null
    private val tail = ByteArray(65536)
    private var tailSize = 0

    @Synchronized
    fun start(config: VmConfig) {
        require(process == null) { "Stop the existing VM before starting another" }
        require(workspace.isDirectory)
        val args = config.argv() // Preflight before state mutation.
        state = VmState.STARTING
        tailSize = 0
        try {
            process = ProcessBuilder(args).directory(workspace).redirectErrorStream(true).start()
            active = config
            state = VmState.RUNNING // Process launched; guest readiness is a separate check.
        } catch (error: Exception) {
            state = VmState.FAILED
            throw error
        }
    }

    @Synchronized
    fun consoleTail(): String {
        val proc = process ?: return String(tail, 0, tailSize, Charsets.UTF_8)
        val chunk = ByteArray(8192)
        repeat(16) {
            val available = runCatching { proc.inputStream.available() }.getOrDefault(0)
            if (available <= 0) return@repeat
            val count = proc.inputStream.read(chunk, 0, minOf(chunk.size, available))
            if (count > 0) {
                val overflow = (tailSize + count - tail.size).coerceAtLeast(0)
                if (overflow > 0) { tail.copyInto(tail, 0, overflow, tailSize); tailSize -= overflow }
                chunk.copyInto(tail, tailSize, 0, count)
                tailSize += count
            }
        }
        if (runCatching { proc.exitValue() }.isSuccess) state = VmState.FAILED
        return String(tail, 0, tailSize, Charsets.UTF_8)
    }

    suspend fun waitForSsh(timeoutMs: Long = 45000): Boolean = withContext(Dispatchers.IO) {
        require(timeoutMs in 100..60000)
        withTimeoutOrNull(timeoutMs) {
            while (process != null) {
                consoleTail()
                if (state == VmState.FAILED) return@withTimeoutOrNull false
                val ready = runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", active!!.sshPort), 500)
                        socket.soTimeout = 500
                        val banner = ByteArray(4)
                        var received = 0
                        while (received < banner.size) {
                            val count = socket.getInputStream().read(banner, received, banner.size - received)
                            if (count < 0) break
                            received += count
                        }
                        received == 4 && String(banner, Charsets.US_ASCII) == "SSH-"
                    }
                }.getOrDefault(false)
                if (ready) { state = VmState.SSH_READY; return@withTimeoutOrNull true }
                delay(200)
            }
            false
        } ?: false
    }

    fun desktopUri(): String {
        val config = active ?: error("VM is stopped")
        require(config.enableDesktop) { "Desktop was not enabled at VM startup" }
        require(state == VmState.RUNNING || state == VmState.SSH_READY)
        return "vnc://127.0.0.1:${5900 + config.vncDisplay}"
    }

    @Synchronized
    fun stop() {
        process?.let { proc ->
            proc.destroy()
            runCatching { proc.inputStream.close() }
            runCatching { proc.outputStream.close() }
        }
        process = null
        active = null
        state = VmState.STOPPED
    }
}
