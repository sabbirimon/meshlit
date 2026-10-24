package com.meshlit.desktop
import com.meshlit.workspace.*
import kotlinx.serialization.json.*

fun main(args: Array<String>) = runCli(args)

internal fun runCli(args: Array<String>) {
    if (args.contentEquals(arrayOf("--status"))) {
        val nodes = runCatching { NodeStore().load() }
        println(buildJsonObject {
            put("edition", "desktop-host-client")
            put("localOs", System.getProperty("os.name"))
            put("architecture", System.getProperty("os.arch"))
            put("logicalProcessors", Runtime.getRuntime().availableProcessors())
            val count = nodes.getOrNull()?.size
            if (count == null) put("savedEndpointCount", JsonNull) else put("savedEndpointCount", count)
            put("savedEndpointReadError", nodes.isFailure)
            put("remoteOnlineStatus", "unknown")
            put("workerEnrollment", false)
            put("layerSharding", false)
            put("clusterControl", false)
            put("containerRuntime", false)
        })
        return
    }
    if (args.isEmpty() || args[0] == "--help") {
        println("Meshlit experimental host client\nUsage: cli --status\n       cli <https://host/v1> models\n       cli <https://host/v1> chat <model-id> [max-output-tokens]\nToken: MESHLIT_CLIENT_TOKEN environment variable. Prompt: stdin (max 128 KiB).\nHTTP is allowed only on literal loopback. Packaged Intel Mac local model check: MeshlitPreview --local-check <output.json>.")
        return
    }
    try {
        require(args.size in 2..4)
        val endpoint = HostEndpoint.parse(args[0])
        HostClient(endpoint, System.getenv("MESHLIT_CLIENT_TOKEN").orEmpty()).use { client ->
            when (args[1]) {
                "models" -> { require(args.size == 2); client.models().forEach(::println) }
                "chat" -> {
                    require(args.size >= 3)
                    val input = System.`in`.readNBytes(131073); require(input.size <= 131072)
                    val prompt = input.decodeToString().trim(); require(prompt.isNotEmpty())
                    val usage = client.generate(args[2], listOf(ChatTurn("user", prompt)), GenerationBudget(args.getOrNull(3)?.toInt() ?: 2048)) { print(it); System.out.flush() }
                    println(); System.err.println("Reported output tokens: ${usage.outputTokens ?: "unknown"}; end-to-end tokens/s: ${usage.tokensPerSecond?.let { "%.2f".format(it) } ?: "unknown"}")
                }
                else -> error("Use models or chat")
            }
        }
    } catch (e: Exception) {
        // Never print a request/URL/parser exception that may embed secrets or host-provided content.
        System.err.println("Client failed (${e.javaClass.simpleName}). Check endpoint, trusted TLS, token and host compatibility.")
        kotlin.system.exitProcess(1)
    }
}
