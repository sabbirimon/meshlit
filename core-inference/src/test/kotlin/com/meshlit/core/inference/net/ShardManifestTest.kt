package com.meshlit.core.inference.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Invariant tests for [ShardManifest] — covers the `init` requirement
 * that shard ranges cover [0, totalLayers) without overlap.
 */
class ShardManifestTest {

    @Test
    fun manifest_threeWaySplit_isValid() {
        val manifest = sampleManifest(totalLayers = 30, splits = listOf(10, 10, 10))
        assertEquals(3, manifest.shards.size)
        // Sum equals total.
        val covered = manifest.shards.sumOf { it.layerEnd - it.layerStart }
        assertEquals(30, covered)
        assertEquals(StageRole.FirstStage, manifest.shards.first().stageRole)
        assertEquals(StageRole.LastStage, manifest.shards.last().stageRole)
        // Middle stages carry their index.
        assertEquals(StageRole.MiddleStage(1), manifest.shards[1].stageRole)
    }

    @Test
    fun manifest_overlappingRanges_throws() {
        try {
            sampleManifest(totalLayers = 10, splits = listOf(7, 5)) // overlap on layer 7
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("shard ranges must cover") == true)
        }
    }

    @Test
    fun manifest_undercovered_throws() {
        try {
            sampleManifest(totalLayers = 10, splits = listOf(3, 3))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    private fun sampleManifest(totalLayers: Int, splits: List<Int>): ShardManifest {
        var cursor = 0
        val specs = splits.mapIndexed { idx, len ->
            val start = cursor
            val end = cursor + len
            cursor = end
            val role = when (idx) {
                0 -> StageRole.FirstStage
                splits.lastIndex -> StageRole.LastStage
                else -> StageRole.MiddleStage(idx)
            }
            ShardSpec(
                shardId = "shard-$start-$end",
                layerStart = start,
                layerEnd = end,
                preferredCapabilityTier = com.meshlit.core.common.CapabilityTier.MID,
                estimatedRamMb = 256L,
                stageRole = role,
            )
        }
        return ShardManifest(
            schemaVersion = 1,
            modelId = "test-model",
            modelSha256 = "deadbeef",
            totalLayers = totalLayers,
            hiddenDim = 64,
            contextSize = 512,
            tokenizer = TokenizerRef("gguf-embedded", 0L, 0L, ""),
            specialTokens = SpecialTokens(bos = 1, eos = 2),
            kvCacheBytesPerToken = 64L * 2L * 4L,
            kvCacheBytesPerShard = 64L * 2L * 4L * 512L,
            shards = specs,
        )
    }
}
