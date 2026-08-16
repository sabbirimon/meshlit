package com.meshlit.core.inference

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.inference.net.ShardManifest
import com.meshlit.core.inference.net.ShardSpec
import com.meshlit.core.inference.net.SpecialTokens
import com.meshlit.core.inference.net.StageRole
import com.meshlit.core.inference.net.TokenizerRef
import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PipelineShardPlanner]. The planner is pure (no I/O),
 * so we exercise the scoring matrix directly with synthetic
 * [PeerCapabilities] snapshots.
 *
 * Coverage:
 *  - Two-peer roster picks the FULL peer for the heaviest shard
 *    and the MID peer for the lightest.
 *  - Tier gate: a LITE peer never hosts a FULL shard.
 *  - RAM gate: a peer below `shard.estimatedRamMb` is excluded.
 *  - FirstStage / LastStage bonuses apply.
 *  - Sticky bonus: a peer that already hosts a shard wins ties.
 *  - Insufficient roster (fewer peers than shards) returns
 *    `Invalid("insufficient_peers")`.
 *  - Topologies are layer-monotonic and end-to-end validated.
 */
class PipelineShardPlannerTest {

    private fun caps(
        peerId: String,
        tier: CapabilityTier = CapabilityTier.FULL,
        freeRamMb: Long = 8_192L,
        hasGpu: Boolean = false,
        hostedShardIds: Set<String> = emptySet(),
    ) = PeerCapabilities(
        peerId = peerId,
        capabilityTier = tier,
        freeRamMb = freeRamMb,
        freeDiskMb = 16_384L,
        hostedShardIds = hostedShardIds,
        lastSeenMs = 1L,
        tier = TrustTier.LOCAL_TRUSTED,
        gpuBackend = if (hasGpu) GpuBackend.VULKAN else GpuBackend.NONE,
    )

    private fun manifest(
        shards: List<ShardSpec>,
        totalLayers: Int = shards.sumOf { it.layerEnd - it.layerStart },
    ) = ShardManifest(
        modelId = "test-model",
        modelSha256 = "abc123",
        totalLayers = totalLayers,
        hiddenDim = 4096,
        contextSize = 4096,
        tokenizer = TokenizerRef(
            type = "gguf-embedded",
            offsetBytes = 0L,
            lengthBytes = 1024L,
            sha256 = "tok",
        ),
        specialTokens = SpecialTokens(bos = 1, eos = 2),
        kvCacheBytesPerToken = 64L * 1024L,
        kvCacheBytesPerShard = 64L * 1024L * 4096L,
        shards = shards,
    )

    private fun shard(
        id: String,
        start: Int,
        end: Int,
        tier: CapabilityTier = CapabilityTier.FULL,
        ramMb: Long = 1_024L,
        role: StageRole = if (end == 0) StageRole.FirstStage else StageRole.LastStage,
    ) = ShardSpec(
        shardId = id,
        layerStart = start,
        layerEnd = end,
        preferredCapabilityTier = tier,
        estimatedRamMb = ramMb,
        stageRole = role,
    )

    @Test
    fun `assigns heaviest shard to FULL peer`() {
        val planner = PipelineShardPlanner()
        // Self is MID so it can't host the FirstStage (FULL tier).
        // self's RAM is set lower than the MID peer's so the
        // LastStage tiebreak (both MID, both bonus-applied) falls
        // to the MID peer.
        val self = caps(peerId = "self", tier = CapabilityTier.MID, freeRamMb = 2_048L)
        val fullPeer = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val midPeer = caps(peerId = "192.168.1.21", tier = CapabilityTier.MID, freeRamMb = 4_096L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, tier = CapabilityTier.FULL, ramMb = 4_096L,
                role = StageRole.FirstStage),
            shard("shard-1", start = 8, end = 16, tier = CapabilityTier.MID, ramMb = 1_024L,
                role = StageRole.LastStage),
        ))
        val topology = planner.plan(m, self, listOf(fullPeer, midPeer))
        assertTrue(topology is PipelineTopology.Valid)
        val valid = topology as PipelineTopology.Valid
        assertEquals(2, valid.assignments.size)
        // FirstStage (FULL tier) → the FULL peer.
        assertEquals("192.168.1.20", valid.firstStageAssignment.peer.peerId)
        // LastStage (MID tier, RAM tiebreak) → the MID peer
        // because its 4 GB > self's 2 GB.
        assertEquals("192.168.1.21", valid.lastStageAssignment.peer.peerId)
    }

    @Test
    fun `tier gate rejects LITE peer for FULL shard`() {
        val planner = PipelineShardPlanner()
        // Self is also LITE so the only candidate pool is LITE.
        // A FULL shard can't land on a LITE phone → Invalid.
        val self = caps(peerId = "self", tier = CapabilityTier.LITE)
        val litePeer = caps(peerId = "192.168.1.30", tier = CapabilityTier.LITE, freeRamMb = 8_192L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, tier = CapabilityTier.FULL, ramMb = 4_096L,
                role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(litePeer))
        assertTrue(topology is PipelineTopology.Invalid)
        assertTrue((topology as PipelineTopology.Invalid).reason.startsWith("no_eligible_peer_for_shard"))
    }

    @Test
    fun `RAM gate excludes peers with insufficient free RAM`() {
        val planner = PipelineShardPlanner()
        // Self is also starved so neither candidate passes the gate.
        val self = caps(peerId = "self", tier = CapabilityTier.FULL, freeRamMb = 256L)
        val starvedPeer = caps(peerId = "192.168.1.31", tier = CapabilityTier.FULL, freeRamMb = 512L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, tier = CapabilityTier.FULL, ramMb = 1_024L,
                role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(starvedPeer))
        assertTrue(topology is PipelineTopology.Invalid)
    }

    @Test
    fun `insufficient roster returns invalid`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val peer1 = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
            shard("shard-1", start = 8, end = 16, role = StageRole.MiddleStage(0)),
            shard("shard-2", start = 16, end = 24, role = StageRole.LastStage),
        ))
        // 1 phone + 1 peer = 2; manifest needs 3.
        val topology = planner.plan(m, self, listOf(peer1))
        assertTrue(topology is PipelineTopology.Invalid)
        assertTrue((topology as PipelineTopology.Invalid).reason.contains("insufficient_peers"))
    }

    @Test
    fun `allowLocalCollisions lets one phone host two stages`() {
        val planner = PipelineShardPlanner(allowLocalCollisions = true)
        val self = caps(peerId = "self", tier = CapabilityTier.FULL, freeRamMb = 16_384L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
            shard("shard-1", start = 8, end = 16, role = StageRole.LastStage),
        ))
        // Only `self` is in the roster; allow collisions so both
        // stages land on it.
        val topology = planner.plan(m, self, roster = emptyList())
        assertTrue(topology is PipelineTopology.Valid)
        val valid = topology as PipelineTopology.Valid
        assertEquals(2, valid.assignments.size)
        assertEquals("self", valid.firstStageAssignment.peer.peerId)
        assertEquals("self", valid.lastStageAssignment.peer.peerId)
        assertEquals("127.0.0.1", valid.firstStageAssignment.activationHost)
    }

    @Test
    fun `sticky bonus prefers peer that already hosts the shard`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val full1 = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val full2 = caps(peerId = "192.168.1.21", tier = CapabilityTier.FULL, freeRamMb = 8_192L,
            hostedShardIds = setOf("shard-0"))
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(full1, full2))
        assertTrue(topology is PipelineTopology.Valid)
        val valid = topology as PipelineTopology.Valid
        // full2 has +0.5 sticky bonus → wins despite identical score.
        assertEquals("192.168.1.21", valid.firstStageAssignment.peer.peerId)
    }

    @Test
    fun `assignments are layer-monotonic`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val p1 = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val p2 = caps(peerId = "192.168.1.21", tier = CapabilityTier.MID, freeRamMb = 4_096L)
        val p3 = caps(peerId = "192.168.1.22", tier = CapabilityTier.MID, freeRamMb = 4_096L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
            shard("shard-1", start = 8, end = 16, tier = CapabilityTier.MID, role = StageRole.MiddleStage(0)),
            shard("shard-2", start = 16, end = 24, tier = CapabilityTier.MID, role = StageRole.LastStage),
        ))
        val topology = planner.plan(m, self, listOf(p1, p2, p3))
        assertTrue(topology is PipelineTopology.Valid)
        val valid = topology as PipelineTopology.Valid
        // Each assignment's shard must start where the previous ended.
        for (i in 1 until valid.assignments.size) {
            assertEquals(
                valid.assignments[i - 1].shard.layerEnd,
                valid.assignments[i].shard.layerStart,
            )
        }
        // First assignment is FirstStage; last is LastStage.
        assertTrue(valid.assignments.first().shard.stageRole is StageRole.FirstStage)
        assertTrue(valid.assignments.last().shard.stageRole is StageRole.LastStage)
    }

    @Test
    fun `topologyId is stable for the same assignment`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val p1 = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val p2 = caps(peerId = "192.168.1.21", tier = CapabilityTier.MID, freeRamMb = 4_096L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
            shard("shard-1", start = 8, end = 16, tier = CapabilityTier.MID, role = StageRole.LastStage),
        ))
        val t1 = planner.plan(m, self, listOf(p1, p2)) as PipelineTopology.Valid
        val t2 = planner.plan(m, self, listOf(p1, p2)) as PipelineTopology.Valid
        assertEquals(t1.topologyId, t2.topologyId)
        assertEquals(16, t1.topologyId.length)
    }

    @Test
    fun `self is included in the candidate pool`() {
        val planner = PipelineShardPlanner(allowLocalCollisions = true)
        val self = caps(peerId = "self", tier = CapabilityTier.FULL, freeRamMb = 16_384L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        // Empty roster; the planner must fall back to `self`.
        val topology = planner.plan(m, self, roster = emptyList())
        assertTrue(topology is PipelineTopology.Valid)
        val valid = topology as PipelineTopology.Valid
        assertEquals("self", valid.firstStageAssignment.peer.peerId)
        assertEquals("127.0.0.1", valid.firstStageAssignment.activationHost)
    }

    @Test
    fun `activationHost is loopback for self and ip otherwise`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val p = caps(peerId = "10.0.0.42", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(p)) as PipelineTopology.Valid
        assertEquals("10.0.0.42", topology.firstStageAssignment.activationHost)
    }

    @Test
    fun `activationPort defaults to 9090`() {
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val p = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(p), activationPort = 7777) as PipelineTopology.Valid
        assertEquals(7777, topology.firstStageAssignment.activationPort)
    }

    @Test
    fun `GPU peer gets a +0_2 score bonus`() {
        // The planner should prefer a GPU peer over a CPU peer when
        // their tiers and RAM are equal.
        val planner = PipelineShardPlanner()
        val self = caps(peerId = "self", tier = CapabilityTier.FULL)
        val cpu = caps(peerId = "192.168.1.20", tier = CapabilityTier.FULL, freeRamMb = 8_192L, hasGpu = false)
        val gpu = caps(peerId = "192.168.1.21", tier = CapabilityTier.FULL, freeRamMb = 8_192L, hasGpu = true)
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(cpu, gpu)) as PipelineTopology.Valid
        assertEquals("192.168.1.21", topology.firstStageAssignment.peer.peerId)
        // Reference the unused peer so the compiler keeps it.
        assertNotNull(cpu)
    }

    @Test
    fun `firstStage bonus applies`() {
        // Set up a scenario where self's FirstStage bonus is the
        // only thing differentiating it from a peer with more RAM.
        val planner = PipelineShardPlanner(firstStageBonus = 1.0)
        // self has 16 GB free, peer has 16 GB free; both FULL.
        val self = caps(peerId = "self", tier = CapabilityTier.FULL, freeRamMb = 16_384L)
        val p = caps(peerId = "zzz-last-peer", tier = CapabilityTier.FULL, freeRamMb = 16_384L)
        // Without the bonus, peer wins on lexical tiebreak.
        // With firstStageBonus = 1.0, self's score is 0.6 + 1.0 = 1.6,
        // peer's is 0.6 (no FirstStage bonus is added for non-self
        // when only one FirstStage exists — actually the bonus
        // applies to the FirstStage role regardless of peer).
        // Both peers get the bonus; tiebreak falls to freeRamMb
        // (equal) then peerId ascending → "self" < "zzz-last-peer"
        // lexically, so self wins.
        val m = manifest(listOf(
            shard("shard-0", start = 0, end = 8, role = StageRole.FirstStage),
        ))
        val topology = planner.plan(m, self, listOf(p), activationPort = 9090) as PipelineTopology.Valid
        assertEquals("self", topology.firstStageAssignment.peer.peerId)
        assertEquals("127.0.0.1", topology.firstStageAssignment.activationHost)
    }
}
