package com.meshlit

import com.meshlit.core.cloudmcp.CloudMcpCoordinator
import com.meshlit.core.cloudmcp.llm.LlmEndpointConfig
import com.meshlit.core.cloudmcp.llm.NaraRouterClient
import com.meshlit.core.cloudmcp.llm.OpenAIMessage
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.trust.CloudCredentialStore
import com.meshlit.core.trust.LocalTrustPolicy
import com.meshlit.core.trust.TrustTier
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Runtime helpers that used to live inline on
 * `MeshlitApplication`. Phase 0.3 lifts them out so the
 * application class can stay focused on the Koin container +
 * `onCreate` boot flow.
 *
 * - [LocalPeerCapabilitiesResolver] reports the local peer's
 *   cluster-relevant state (disk, RAM, hosted shards, trust tier).
 * - [AgentPromptRunner] dispatches a single agent prompt against
 *   the user's chosen LLM endpoint and feeds the resulting chunks
 *   into the `CloudMcpCoordinator` events flow.
 */
class LocalPeerCapabilitiesResolver(
    private val filesDir: File,
    private val capabilityTier: () -> com.meshlit.capability.CapabilityTier,
) {
    fun resolve(): PeerCapabilities {
        val freeDiskMb = filesDir.usableSpace / (1024L * 1024L)
        val rt = Runtime.getRuntime()
        val freeRamMb = (rt.maxMemory() - rt.totalMemory() + rt.freeMemory()) / (1024L * 1024L)
        val hosted = mutableSetOf<String>()
        val root = File(filesDir, "shards")
        if (root.isDirectory) {
            root.listFiles()?.forEach { modelDir ->
                if (!modelDir.isDirectory) return@forEach
                val modelId = modelDir.name
                modelDir.listFiles { f -> f.isFile && f.extension == "shard" }?.forEach { shard ->
                    hosted += "$modelId/${shard.nameWithoutExtension}"
                }
            }
        }
        return PeerCapabilities(
            peerId = "self",
            capabilityTier = capabilityTier(),
            freeRamMb = freeRamMb,
            freeDiskMb = freeDiskMb,
            hostedShardIds = hosted,
            lastSeenMs = Long.MAX_VALUE,
            tier = LocalTrustPolicy.currentTierOr(TrustTier.LOCAL_TRUSTED),
        )
    }
}

/**
 * Dispatches a single agent prompt against the user's chosen
 * LLM endpoint. Koin-injected collabourators do the actual
 * HTTP round-trip; the runner only owns the lifecycle (suspend
 * collection + event forwarding).
 */
class AgentPromptRunner(
    private val appScope: CoroutineScope,
    private val settings: SettingsRepository,
    private val cloudCredentials: CloudCredentialStore,
    private val httpClient: OkHttpClient,
    private val cloudCoordinator: CloudMcpCoordinator,
) {
    fun run(providerId: String?, prompt: String) {
        val messages = listOf(OpenAIMessage(role = "user", content = prompt))
        val tools = cloudCoordinator.toolRegistry.ordered()
        appScope.launch {
            val endpoint = resolveLlmEndpoint()
            val client = endpoint.buildClient(httpClient = httpClient)
            client.chatCompletions(
                providerId = providerId ?: "user-llm",
                messages = messages,
                tools = tools,
            ).collect { chunk ->
                when (chunk) {
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Text ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Thought(
                                providerId = chunk.providerId,
                                text = chunk.delta,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.ToolCall ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.ToolCall(
                                providerId = chunk.providerId,
                                callId = chunk.callId,
                                name = chunk.name,
                                args = chunk.args,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Error ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Error(
                                providerId = chunk.providerId,
                                message = chunk.message,
                            ),
                        )
                    is com.meshlit.core.cloudmcp.llm.LlmChunk.Done ->
                        cloudCoordinator.tryEmit(
                            com.meshlit.core.cloudmcp.McpEvent.Done(providerId = chunk.providerId),
                        )
                }
            }
        }
    }

    private suspend fun resolveLlmEndpoint(): LlmEndpointConfig {
        val baseUrl = settings.llmEndpointFlow.first()
        val model = settings.llmModelFlow.first()
        val credentialProviderId = settings.llmApiKeyProviderIdFlow.first()
        val apiKey = cloudCredentials.get(credentialProviderId, "token") ?: ""
        return LlmEndpointConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            credentialProviderId = credentialProviderId,
        )
    }
}
