package com.meshlit.sandbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meshlit.core.sandbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

/** Human-facing terminal commands; privileged commands are not agent tools. */
class RuntimeTerminal(private val context: Context, private val host: RuntimeHost) {
    suspend fun execute(command: String, args: List<String>): List<String> = when (command) {
        "runtime" -> runtime(args)
        "vm" -> vm(args)
        "net" -> network(args)
        "artifact" -> artifact(args)
        else -> error("Unknown runtime command")
    }

    private suspend fun runtime(args: List<String>): List<String> = when (args.firstOrNull()) {
        "status" -> listOf("Command backend: ${host.config().mode}", "VM: ${host.vm.state}",
            "Agent VM activation: ${host.allowAgentVm()}", "Workspace: ${host.workspace}",
            "RunAnywhere inference is independent of the command backend")
        "use" -> {
            require(args.size >= 2) { "runtime use app|root|root_chroot|proot|bubblewrap|vm_ssh [binary] [rootfs]" }
            val mode = RuntimeMode.valueOf(args[1].uppercase())
            require(mode != RuntimeMode.VM_SSH) { "Use runtime ssh <binary> <user> <key> <known_hosts> [port]" }
            host.configure(RuntimeConfig(mode, args.getOrElse(2) { "" }, args.getOrElse(3) { "" }))
            listOf("Selected $mode; missing binaries or kernel support will fail explicitly")
        }
        "ssh" -> {
            require(args.size in 5..6) { "runtime ssh <binary> <user> <key> <known_hosts> [port]" }
            host.configure(RuntimeConfig(mode = RuntimeMode.VM_SSH, executable = args[1],
                sshUser = args[2], sshIdentity = args[3], sshKnownHosts = args[4],
                sshPort = args.getOrNull(5)?.toInt() ?: host.vmConfig().sshPort))
            listOf("Guest SSH selected; verified host keys and identity are required")
        }
        "exec" -> {
            val rootConsent = args.getOrNull(1) == "--allow-root"
            val argv = args.drop(if (rootConsent) 2 else 1)
            val config = host.config()
            val plan = RuntimePlanner(host.workspace).plan(config, argv, rootConsent)
            val result = host.execute(argv, rootConsent)
            listOf(plan.isolation) + result.stdout.lineSequence().filter { it.isNotEmpty() }.take(500).toList() +
                result.stderr.lineSequence().filter { it.isNotEmpty() }.map { "stderr: $it" }.take(100).toList() +
                "exit=${result.exitCode ?: "timeout"}; truncated=${result.truncated}"
        }
        else -> help()
    }

    private suspend fun vm(args: List<String>): List<String> = when (args.firstOrNull()) {
        "configure" -> {
            require(args.size in 4..6) { "vm configure <qemu-binary> <qcow2> x86_64|aarch64 [kernel] [initrd]" }
            host.configureVm(host.vmConfig().copy(executable = args[1], disk = args[2],
                architecture = GuestArchitecture.valueOf(args[3].uppercase()),
                kernel = args.getOrElse(4) { "" }, initrd = args.getOrElse(5) { "" }))
            listOf("VM configured; starts on demand only. Disk persistence: ${host.vmConfig().persistDisk}")
        }
        "resources" -> {
            require(args.size == 3) { "vm resources <memory-MiB> <cpus>" }
            host.configureVm(host.vmConfig().copy(memoryMb = args[1].toInt(), cpus = args[2].toInt()))
            listOf("VM resource budget saved")
        }
        "storage" -> {
            require(args.size==2 && args[1] in listOf("ephemeral","persistent")) {"vm storage ephemeral|persistent"}
            host.configureVm(host.vmConfig().copy(persistDisk=args[1]=="persistent"))
            listOf("Disk mode ${args[1]}: persistent mode writes guest changes to the supplied qcow2 disk")
        }
        "network" -> {
            require(args.size==2 && args[1] in listOf("on","off")) {"vm network on|off"}
            host.configureVm(host.vmConfig().copy(allowOutboundNetwork=args[1]=="on"))
            listOf("Guest outbound network ${args[1]}; applies at next VM start. Inbound forwarding stays loopback-only")
        }
        "desktop" -> {
            require(args.size == 2 && args[1] in listOf("on", "off"))
            host.configureVm(host.vmConfig().copy(enableDesktop = args[1] == "on"))
            listOf("Desktop ${args[1]}; requires a guest image with a graphical desktop")
        }
        "agent" -> {
            require(args.size == 2 && args[1] in listOf("on", "off"))
            host.setAllowAgentVm(args[1] == "on")
            listOf("Agent VM activation ${args[1]}; root access is separate")
        }
        "start" -> listOf("VM: ${host.startVm()}; use vm wait to check guest SSH readiness")
        "wait" -> listOf("Guest SSH ready: ${host.waitForGuest()}")
        "stop" -> { host.stopVm(); listOf(if(host.vmConfig().persistDisk) "VM stopped; persistent disk writes retained" else "VM stopped; ephemeral guest changes discarded") }
        "status" -> listOf("VM: ${host.vm.state}", "Last failure: ${host.lastError ?: "none"}")
        "console" -> withContext(Dispatchers.IO) { host.vm.consoleTail().lineSequence().takeLastLines(100) }
        "vnc" -> {
            val uri = host.vm.desktopUri()
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            listOf("Opened $uri in an installed VNC viewer")
        }
        else -> help()
    }

    private suspend fun network(args: List<String>): List<String> = when (args.firstOrNull()) {
        "interfaces" -> host.diagnostics.interfaces().map { (name, ips) -> "$name: ${ips.joinToString()}" }
        "dns" -> {
            require(args.size == 2) { "net dns <host>" }
            host.diagnostics.dns(args[1])
        }
        "tcp" -> {
            require(args.size == 4 && args[3] == "--authorized") { "net tcp <host> <port,port> --authorized" }
            val ports = args[2].split(',').map(String::toInt)
            val results = host.diagnostics.tcp(args[1], ports, true)
            val report = buildJsonObject {
                put("host", args[1]); put("timestamp_ms", System.currentTimeMillis())
                put("ports", buildJsonArray { results.forEach { r -> add(buildJsonObject {
                    put("address", r.address); put("port", r.port); put("state", r.state); put("duration_ms", r.durationMs)
                }) } })
            }
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "runtime/reports").apply { mkdirs() }
                // Retain a bounded history.
                dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(19)?.forEach { it.delete() }
                File(dir, "tcp-${System.currentTimeMillis()}.json").apply { writeText(report.toString()) }
            }
            results.map { "${it.address}:${it.port} ${it.state} (${it.durationMs} ms)" } + "Report saved: $file"
        }
        else -> help()
    }

    private suspend fun artifact(args: List<String>): List<String> = when (args.firstOrNull()) {
        "import" -> {
            require(args.size in 4..5 && (args.size == 4 || args[4] == "--replace")) {
                "artifact import <source-file> <name> <sha256> [--replace]"
            }
            val file = host.artifacts.install(File(args[1]), args[2], args[3], args.getOrNull(4) == "--replace")
            listOf("Verified artifact imported: $file")
        }
        "verify" -> {
            require(args.size == 3) { "artifact verify <file> <sha256>" }
            listOf("Checksum valid: ${host.artifacts.verify(File(args[1]), args[2])}")
        }
        else -> help()
    }

    private fun Sequence<String>.takeLastLines(count: Int): List<String> = toList().takeLast(count)

    fun help(): List<String> = listOf(
        "runtime status | runtime use app|root|root_chroot|proot|bubblewrap [binary] [rootfs]",
        "runtime exec [--allow-root] <binary> [args...] (quotes supported; batch execution)",
        "runtime ssh <ssh-binary> <guest-user> <identity> <verified-known_hosts> [port]",
        "vm configure <qemu-binary> <qcow2> x86_64|aarch64 [kernel] [initrd]",
        "vm resources <memory-MiB> <cpus> | vm desktop on|off | vm agent on|off",
        "vm storage ephemeral|persistent | vm network on|off",
        "vm start | vm wait | vm status | vm console | vm vnc | vm stop",
        "net interfaces | net dns <host> | net tcp <host> <port,port> --authorized",
        "artifact import <source> <name> <sha256> [--replace] | artifact verify <file> <sha256>",
    )
}
