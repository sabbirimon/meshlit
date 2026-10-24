package com.meshlit.desktop

import com.meshlit.workspace.ChatTurn
import com.meshlit.workspace.GenerationBudget
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Explicit qualification command; generates a real answer, never seeds the UI. */
internal fun localCheck(output: String) {
    val engine = LocalEngine()
    try {
        val start = System.nanoTime()
        val session = engine.start()
        val loadSeconds = (System.nanoTime() - start) / 1_000_000_000.0
        // llama.cpp intentionally exposes model metadata on loopback without a key.
        // Generation is the protected operation, so verify that actual route.
        check(runCatching { HostClient(session.endpoint, "").use {
            it.generate(DesktopStarter.alias, listOf(ChatTurn("user", "Hello")), GenerationBudget(64)) {}
        } }.isFailure) {
            "Local generation accepted a missing token"
        }
        val reply = StringBuilder()
        val usage = HostClient(session.endpoint, session.token).use { client ->
            client.generateConfigured(DesktopStarter.alias, listOf(ChatTurn("user", "Explain in three short bullet points why backups help.")),
                GenerationBudget(128), DesktopStarter.prompt) { reply.append(it) }
        }
        check(reply.isNotBlank() && (usage.outputTokens ?: 0) > 0) { "No real output/token usage" }
        engine.stop()
        Thread.sleep(2500)
        check(runCatching { HostClient(session.endpoint, session.token).use { it.models() } }.isFailure) {
            "Local model API remained available after Unload"
        }
        val result = buildJsonObject {
            put("model", DesktopStarter.label); put("modelSha256", DesktopStarter.sha256)
            put("engine", "llama.cpp "+session.backend);put("nativeContextVerified",session.context); put("loadSeconds", loadSeconds)
            put("answer", reply.toString()); put("outputTokens", checkNotNull(usage.outputTokens))
            put("endToEndTokensPerSecond", checkNotNull(usage.tokensPerSecond))
            put("generationWithoutTokenRejected", true); put("loopbackModelMetadataPublic", true)
            put("unloadStoppedListener", true)
            put("scope", "Real bundled model generation on Intel Mac; no cloud, GPU, other-device or broad quality benchmark")
        }
        Files.writeString(Path.of(output), Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), result) + "\n")
        println("Real local model generation, authentication and unload checks passed.")
    } finally { engine.close() }
}
