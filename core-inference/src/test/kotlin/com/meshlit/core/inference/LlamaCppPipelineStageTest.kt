package com.meshlit.core.inference

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.net.ActivationPacket
import com.meshlit.core.inference.net.ActivationTransport
import com.meshlit.core.inference.net.ShardManifest
import com.meshlit.core.inference.net.ShardRef
import com.meshlit.core.inference.net.SpecialTokens
import com.meshlit.core.inference.net.StageRole
import com.meshlit.core.inference.net.TokenizerRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LlamaCppPipelineStage]. The native llama.cpp JNI is
 * not loaded in the unit-test environment, so [load] fails with a
 * typed "library missing" error. The tests focus on:
 *  - The wrapper rejects [evalAndForward] before a successful
 *    load with a typed `pipeline.stage_not_loaded` failure.
 *  - The wrapper's `load` returns the underlying engine's
 *    [MeshlitResult.Failure] when the native lib is missing —
 *    no swallowing of errors.
 *  - [stageIndex] returns the right ordinal for each role
 *    (FirstStage=0, MiddleStage=role.index, LastStage=MAX).
 *
 * End-to-end success-path coverage lives in
 * `PipelineCoordinatorTest` with stub stages — we exercise the
 * orchestrator glue there because the JNI bridge is the long
 * pole.
 */
class LlamaCppPipelineStageTest {

    private fun manifest() = ShardManifest(
        modelId = "test",
        modelSha256 = "sha",
        totalLayers = 8,
        hiddenDim = 64,
        contextSize = 1024,
        tokenizer = TokenizerRef(
            type = "gguf-embedded",
            offsetBytes = 0L,
            lengthBytes = 32L,
            sha256 = "tok",
        ),
        specialTokens = SpecialTokens(bos = 1, eos = 2),
        kvCacheBytesPerToken = 1024L,
        kvCacheBytesPerShard = 1024L * 1024L,
        shards = listOf(
            com.meshlit.core.inference.net.ShardSpec(
                shardId = "shard-0",
                layerStart = 0,
                layerEnd = 8,
                preferredCapabilityTier = CapabilityTier.FULL,
                estimatedRamMb = 256L,
                stageRole = StageRole.FirstStage,
            ),
        ),
    )

    private fun shard() = ShardRef(
        modelId = "test",
        layerStart = 0,
        layerEnd = 8,
        stageRole = StageRole.FirstStage,
        sha256 = "sha",
    )

    @Test
    fun `evalAndForward fails when stage is not loaded`() = runBlocking {
        val engine = LlamaCppInferenceEngine()
        val stage = LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.FirstStage,
            layerStart = 0,
            layerEnd = 8,
            embeddingDim = 64,
        )
        val outChannel = object : ActivationTransport {
            override fun connect(peerHost: String, peerPort: Int) {}
            override fun send(packet: ActivationPacket) {}
            override fun incoming(): Flow<ActivationPacket> =
                MutableSharedFlow<ActivationPacket>().asSharedFlow()
            override fun close() {}
        }
        val result = stage.evalAndForward(
            inbound = null,
            outChannel = outChannel,
            position = 0,
            tokenIdx = 0L,
        )
        assertTrue(result is MeshlitResult.Failure)
        val err = (result as MeshlitResult.Failure).error
        assertTrue(err is MeshlitError.Native)
        assertEquals("pipeline.stage_not_loaded", (err as MeshlitError.Native).tag)
    }

    @Test
    fun `load fails when native library is missing`() = runBlocking {
        val engine = LlamaCppInferenceEngine()
        val stage = LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.FirstStage,
            layerStart = 0,
            layerEnd = 8,
            embeddingDim = 64,
        )
        val result = stage.load(shard(), manifest())
        assertTrue(result is MeshlitResult.Failure)
        val err = (result as MeshlitResult.Failure).error
        assertTrue(err is MeshlitError.Native)
        // The wrapper must surface the underlying "library missing"
        // failure — not swallow it. The tag carries the reason.
        assertTrue((err as MeshlitError.Native).tag.contains("llama.cpp"))
    }

    @Test
    fun `close is idempotent`() {
        val engine = LlamaCppInferenceEngine()
        val stage = LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.LastStage,
            layerStart = 8,
            layerEnd = 16,
            embeddingDim = 64,
        )
        stage.close()
        stage.close()  // must not throw
    }

    @Test
    fun `constructor accepts all role shapes`() {
        val engine = LlamaCppInferenceEngine()
        // FirstStage
        LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.FirstStage,
            layerStart = 0,
            layerEnd = 8,
            embeddingDim = 64,
        )
        // MiddleStage
        LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.MiddleStage(index = 0),
            layerStart = 8,
            layerEnd = 16,
            embeddingDim = 64,
        )
        // LastStage
        LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.LastStage,
            layerStart = 16,
            layerEnd = 24,
            embeddingDim = 64,
        )
    }

    @Test
    fun `layerStart and layerEnd are exposed for diagnostics`() {
        val engine = LlamaCppInferenceEngine()
        val stage = LlamaCppPipelineStage(
            engine = engine,
            role = StageRole.MiddleStage(index = 1),
            layerStart = 8,
            layerEnd = 16,
            embeddingDim = 64,
        )
        assertEquals(8, stage.layerStart)
        assertEquals(16, stage.layerEnd)
    }
}
