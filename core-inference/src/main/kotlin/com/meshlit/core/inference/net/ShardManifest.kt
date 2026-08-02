package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format descriptor for a sharded model. Produced by the planner
 * (the device that owns the full GGUF) and broadcast to every peer
 * expected to host one or more shards. Each peer caches the manifest
 * under its [ShardRef] so a replacement can be planned without a
 * round-trip to the original owner.
 *
 * Stability:
 *  - `schemaVersion` is bumped on any breaking change. Today it's
 *    `1`. Unknown fields are tolerated (the wire consumer sets
 *    `ignoreUnknownKeys = true`).
 *
 * Shape (matches the Phase 2 design from
 * `~/.puku-cli/plans/shimmering-stargazing-reddy.md` §2.1.1):
 *
 * ```
 * shards sum to totalLayers (no overlap, no gap).
 * tokenizer lives on the FirstStage phone only.
 * KV cache slice is per-shard; the bytes-per-token figure lets
 *     each phone reserve the right amount of RAM for its slice.
 * ```
 */
@Serializable
data class ShardManifest(
    val schemaVersion: Int = 1,
    val modelId: String,
    val modelSha256: String,
    val totalLayers: Int,
    val hiddenDim: Int,
    val contextSize: Int,
    val tokenizer: TokenizerRef,
    val specialTokens: SpecialTokens,
    val kvCacheBytesPerToken: Long,
    val kvCacheBytesPerShard: Long,
    val shards: List<ShardSpec>,
) {
    init {
        require(shards.sumOf { it.layerEnd - it.layerStart } == totalLayers) {
            "shard ranges must cover all layers exactly once"
        }
        require(shards.sumOf { it.estimatedRamMb } > 0) {
            "estimatedRamMb must be positive across shards"
        }
    }
}

/**
 * Where the tokenizer lives inside the GGUF. **For v1 we always
 * embed the tokenizer in the manifest** and the first phone (which
 * is also the one that holds the embed/layer 0 weights) detokenizes
 * the final token to text. The file-offset + sha256 lets the
 * recipient do an integrity check after download.
 */
@Serializable
data class TokenizerRef(
    val type: String = "gguf-embedded",
    val offsetBytes: Long,
    val lengthBytes: Long,
    val sha256: String,
)

/**
 * Special tokens the planner needs in order to start generation.
 * Keep this minimal — only the tokens the prompt path needs to know
 * about. Anything advanced (chat templates, function tokens) is
 * per-model and lives outside the manifest.
 */
@Serializable
data class SpecialTokens(
    val bos: Int,
    val eos: Int,
    val pad: Int = 0,
    val newline: Int = 0,
    val reserved: List<ReservedToken> = emptyList(),
)

@Serializable
data class ReservedToken(
    val id: Int,
    val name: String,
)

/**
 * One shard. The layer range is **inclusive** at `layerStart`,
 * **exclusive** at `layerEnd` (matches llama.cpp's `[start, end)`
 * convention).
 *
 * `preferredCapabilityTier` is a *preference*, not a hard requirement —
 * the planner may still assign a MID shard to a FULL phone when
 * the LITE roster is empty. The opposite direction (assigning a FULL
 * shard to a LITE phone) is rejected because LITE phones don't have
 * the RAM headroom for `parameterCount ≈ 7B` shards.
 */
@Serializable
data class ShardSpec(
    val shardId: String,
    val layerStart: Int,
    val layerEnd: Int,
    val preferredCapabilityTier: CapabilityTier,
    val estimatedRamMb: Long,
    val stageRole: StageRole,
)

/**
 * Where in the pipeline a shard sits. The planner builds the chain
 * in order — FirstStage → MiddleStage(0) → … → MiddleStage(N) →
 * LastStage. Only the LastStage shard runs the sampler and produces
 * the finished token.
 */
@Serializable
sealed class StageRole {
    @Serializable
    @SerialName("first")
    data object FirstStage : StageRole()

    @Serializable
    @SerialName("middle")
    data class MiddleStage(val index: Int) : StageRole()

    @Serializable
    @SerialName("last")
    data object LastStage : StageRole()
}

/**
 * Local view of a shard the peer is currently hosting. Surfaced via
 * `/v1/model.shards` (added in a later phase) so the planner can
 * re-route without an out-of-band query.
 */
@Serializable
data class ShardRef(
    val modelId: String,
    val layerStart: Int,
    val layerEnd: Int,
    val stageRole: StageRole,
    val sha256: String,
)