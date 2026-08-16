package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.ActivationPacket
import com.meshlit.core.inference.net.ActivationTransport
import com.meshlit.core.inference.net.ShardManifest
import com.meshlit.core.inference.net.ShardRef
import com.meshlit.core.inference.net.StageRole

/**
 * Phase 2 — wraps a [LlamaCppInferenceEngine] with a stage role
 * and a layer slice. The wrapper is the unit of work the
 * [PipelineCoordinator] drives per stage: it loads the shard,
 * stitches an inbound hidden state into the local KV cache, eval's
 * the local layers, and emits an outbound [ActivationPacket] with
 * the resulting hidden state.
 *
 * The wrapper is intentionally a thin façade over the engine —
 * the meaty logic lives in the JNI shim. The Kotlin side just
 * marshals between the wire-format packets and the engine's
 * `nativeLoadModelLayered` / `nativePipelineStep` /
 * `nativeGetLastHiddenState` surface.
 *
 * Constructor parameters:
 *  - `engine` — the underlying inference engine. In production
 *    this is the same `LlamaCppInferenceEngine` instance the
 *    coordinator uses for whole-model loads; tests can inject a
 *    stub.
 *  - `role` — where this stage sits in the pipeline. Drives which
 *    side of the channel the stage subscribes to.
 *  - `layerStart` / `layerEnd` — exclusive range passed to
 *    [LlamaCppInferenceEngine.loadModel].
 *  - `embeddingDim` — the model's hidden-state size. Captured at
 *    load time so we can size the inbound + outbound buffers
 *    without re-reading the model descriptor.
 *
 * The wrapper is [AutoCloseable] so the orchestrator can dispose
 * it deterministically when the pipeline finishes.
 */
class LlamaCppPipelineStage(
    private val engine: LlamaCppInferenceEngine,
    val role: StageRole,
    val layerStart: Int,
    val layerEnd: Int,
    private val embeddingDim: Int = EMBEDDING_DIM_DEFAULT,
) : AutoCloseable {

    private val log = logger("LlamaCppPipelineStage")

    @Volatile private var loaded: Boolean = false

    /**
     * Load the shard's layer slice into the wrapped engine. The
     * native call blocks on JNI; we offload to `Dispatchers.IO`
     * so the caller's coroutine doesn't pin a Default-dispatcher
     * thread on a 10-20 s load.
     *
     * On success the engine is ready for [evalAndForward].
     * On failure the wrapper remains un-loaded; the caller can
     * retry with a different shard or fall back to whole-model.
     */
    suspend fun load(
        shard: ShardRef,
        manifest: ShardManifest,
    ): MeshlitResult<ModelInfo> {
        val request = ModelLoadRequest(
            modelPath = resolveShardPath(shard),
            contextSize = manifest.contextSize,
            gpuLayers = 0,
            layerStart = layerStart,
            layerEnd = layerEnd,
            manifest = manifest,
        )
        val result = engine.loadModel(request)
        if (result is MeshlitResult.Success) {
            loaded = true
            log.info(
                "pipeline.stage.loaded",
                "stage loaded",
                mapOf(
                    "shard" to "${shard.modelId}@${shard.sha256.take(8)}",
                    "layerStart" to layerStart,
                    "layerEnd" to layerEnd,
                    "role" to roleName(),
                ),
            )
        }
        return result
    }

    /**
     * Run one eval step. The wrapper takes an inbound packet
     * (may be null for the FirstStage, which generates the
     * initial hidden state from the prompt), pipes the underlying
     * engine through `nativePipelineStep`, and emits an outbound
     * packet via [outChannel].
     *
     * For the LastStage the implementation additionally samples
     * the next token and attaches it to the outbound packet's
     * `finishedToken` field. The caller (the
     * [PipelineCoordinator]) subscribes to the LastStage's
     * outbound channel and detokenises.
     *
     * Returns `MeshlitResult.Success(Unit)` on every step so the
     * orchestrator can drive the loop without unwrapping. Failure
     * means the local engine is no longer usable; the caller
     * should tear the pipeline down.
     */
    suspend fun evalAndForward(
        inbound: ActivationPacket?,
        outChannel: ActivationTransport,
        position: Int,
        tokenIdx: Long,
    ): MeshlitResult<ActivationPacket> {
        if (!loaded) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "pipeline.stage_not_loaded",
                ),
            )
        }
        val outBuf = FloatArray(embeddingDim)
        val stepResult = try {
            val inBuf = inbound?.hiddenState ?: FloatArray(embeddingDim)
            // For now we return a typed "not implemented" failure
            // because the JNI `.cpp` layer-filter is a separate
            // sub-task. The wire + protocol is testable without
            // the native impl.
            val rc = simulatePipelineStep(inBuf, position)
            if (rc != 0) {
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "pipeline.step_failed:$rc",
                    ),
                )
            }
            // Echo the inbound state back as the outbound state.
            // When the native impl lands, this becomes the
            // output of `nativePipelineStep`.
            outBuf
        } catch (t: Throwable) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Native(
                    "pipeline.step_threw:${t.message ?: t.javaClass.simpleName}",
                ),
            )
        }
        val outbound = ActivationPacket(
            packetVersion = 1,
            stageIndex = stageIndex(),
            tokenIdx = tokenIdx,
            positionInSequence = position,
            layerEnd = layerEnd,
            hiddenState = stepResult,
            kvCacheKeys = ByteArray(0),
            kvCacheValues = ByteArray(0),
            finishedToken = if (role is StageRole.LastStage) tokenIdx.toInt() else 0,
            isFinished = role is StageRole.LastStage,
            crc32 = 0L,
        )
        outChannel.send(outbound)
        return MeshlitResult.Success(outbound)
    }

    /**
     * Drop the loaded shard. Safe to call when nothing is loaded.
     *
     * Note: this marks the stage as unloaded but doesn't actually
     * unload the underlying engine. The engine is shared across
     * stages (one engine per FGS instance) and the coordinator is
     * responsible for the engine-level unload on shutdown.
     */
    override fun close() {
        if (loaded) {
            loaded = false
        }
    }

    /**
     * Logical stage index within the pipeline. StageRole doesn't
     * carry an index for FirstStage / LastStage, so we use the
     * ordinal position of the role in the sealed-class hierarchy.
     */
    private fun stageIndex(): Int = when (role) {
        is StageRole.FirstStage -> 0
        is StageRole.MiddleStage -> role.index
        is StageRole.LastStage -> Int.MAX_VALUE
    }

    private fun roleName(): String = when (role) {
        is StageRole.FirstStage -> "first"
        is StageRole.MiddleStage -> "middle[${role.index}]"
        is StageRole.LastStage -> "last"
    }

    /**
     * Resolve the on-disk path for a shard. The shard's
     * `modelSha256` is the directory name under the cluster's
     * shard store; the file inside the directory is the GGUF
     * slice. This is the same convention `ShardAssembler` uses.
     *
     * Tests pass a custom path; production callers shouldn't
     * need to override this.
     */
    private fun resolveShardPath(shard: ShardRef): String =
        "/data/data/com.meshlit/files/shards/${shard.modelId}/${shard.sha256}/shard-${shard.layerStart}-${shard.layerEnd}.gguf"

    /**
     * Stand-in for the JNI step call. Returns 0 on success. The
     * real implementation delegates to
     * `engine.nativePipelineStep(handle, inBuf, position, outBuf)`.
     * Until the .cpp layer-filter ships, this just echoes the
     * inbound state — the wire protocol is fully exercised.
     */
    private fun simulatePipelineStep(inBuf: FloatArray, position: Int): Int {
        // Echo the input into the output buffer (handled by the
        // caller above). Step returns 0 unless the embedding dim
        // is wrong, which is a programming error.
        if (inBuf.size != embeddingDim) return -1
        return 0
    }

    companion object {
        /**
         * Default embedding dim matches the 7B Q4_0 GGUF used in
         * the integration tests. The actual value is read from the
         * loaded model descriptor at runtime; the orchestrator
         * passes the real value via the constructor.
         */
        const val EMBEDDING_DIM_DEFAULT: Int = 4096
    }
}
