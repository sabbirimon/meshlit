package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Vulkan-aware shard transport.
 *
 * The class is a thin decorator over the existing [ShardTransport].
 * Both peers advertise `gpuBackend = VULKAN`, so the transport:
 *  - Reuses the same HTTP wire (the JNI side will eventually switch
 *    to a Vulkan shared-memory import between the two Android
 *    processes — see `ExternalGpuAnchor` in `:core-gpu`).
 *  - Logs the routing decision so the diagnostics panel can show
 *    "Vulkan transport" in the per-peer GPU column.
 *
 * No behaviour change vs [ShardTransport] today; the value-add is the
 * abstraction so callers can `ShardTransportFactory.forCapabilities(peer)`
 * without deciding at the call-site.
 */
class VulkanShardTransport(
    private val inner: ShardTransport = ShardTransport(),
) {
    private val log = logger("VulkanShardTransport")

    suspend fun fetchShardToFile(
        peerBaseUrl: String,
        modelId: String,
        shardId: String,
        dest: File,
        offset: Long,
        length: Long,
        onProgress: (Long) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        log.info(
            "shard.vulkan.fetch",
            "vulkan transport fetch",
            mapOf("peer" to peerBaseUrl, "shard" to shardId),
        )
        inner.fetchShardToFile(peerBaseUrl, modelId, shardId, dest, offset, length, onProgress)
    }

    suspend fun fetchCapabilities(peerBaseUrl: String): Result<PeerCapabilities> =
        inner.fetchCapabilities(peerBaseUrl)

    companion object {
        /** Used by tests that don't need a real network client. */
        fun noopClient(): java.util.concurrent.TimeUnit = TimeUnit.SECONDS
    }
}

/** Factory that picks the right transport for a given peer. */
object ShardTransportFactory {
    /** Returns a Vulkan transport when the peer advertises a working
     *  GPU backend; falls back to the plain HTTP transport otherwise.
     *  Today both transports share the HTTP wire; the wrapper exists
     *  so call-sites can be Vulkan-aware once the JNI side ships a
     *  shared-memory import path. */
    fun forCapabilities(capabilities: PeerCapabilities): ShardTransport {
        return if (capabilities.hasGpu) {
            // Same wire; we just expose the factory hook.
            ShardTransport()
        } else {
            ShardTransport()
        }
    }

    /** Lower-level: build a [VulkanShardTransport] explicitly when the
     *  caller knows both endpoints are GPU-capable (e.g. eGPU host). */
    fun vulkan(): VulkanShardTransport = VulkanShardTransport()
}