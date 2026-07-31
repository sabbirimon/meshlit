package com.meshlit.core.common

/**
 * A unit of work that can be dispatched to a cluster node. The router
 * picks a node matching `preferredRole` and dispatches the `payload`.
 *
 * Every job has a hard timeout. If the worker doesn't ack within
 * `deadlineMs`, the router marks the job failed and retries per the
 * store-and-forward policy (Phase 2).
 */
data class JobSpec(
    val id: String,
    val preferredRole: ClusterRole,
    val payload: JobPayload,
    val deadlineMs: Long,
    val maxRetries: Int = 2,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Job payload kinds. New kinds are added as the project grows; existing
 * ones must stay stable because they cross the network between nodes.
 */
sealed class JobPayload {
    /** A prompt for a Brain node. */
    data class Inference(
        val prompt: String,
        val maxTokens: Int = 256,
        val temperature: Float = 0.7f,
    ) : JobPayload()

    /** Embed a batch of documents for RAG. Phase 3. */
    data class Embed(
        val chunks: List<String>,
    ) : JobPayload()

    /** Run an MCP tool call. Phase 3. */
    data class ToolCall(
        val toolName: String,
        val arguments: Map<String, String>,
    ) : JobPayload()

    /** Cooperative training step (Phase 5). */
    data class TrainingStep(
        val modelId: String,
        val datasetId: String,
    ) : JobPayload()
}
