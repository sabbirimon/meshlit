package com.meshlit.core.training.config

import com.meshlit.core.common.ClusterRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Distributed-training configuration for a Meshlit cooperative LoRA job.
 *
 * This is the v1 wire schema for the multi-device training menu (Phase 11).
 * The class is intentionally narrow: it only carries the fields that
 * differ across strategies, the sharding hints, and the durability
 * knobs. Hyperparameters (loraRank, totalSteps, etc.) live on
 * [com.meshlit.core.training.ClusterTrainingConfig] — we do not duplicate
 * them here.
 *
 * Schema versioning: [configSchemaVersion] is bumped on any breaking
 * change. Loaders MUST reject unknown versions with a typed
 * [com.meshlit.core.common.MeshlitError.Invalid] so the user sees an
 * explicit "you need app update" instead of a silent miscompute.
 *
 * Defaults match the §0 pros/cons table in the plan file:
 *  - P2P for the strategy (matches the user's "default = peer-to-peer").
 *  - 5-minute checkpoint retention so the device can't fill up across
 *    long runs.
 *  - Outer LR ∈ [0.1, 1.0] (validated in [init]) because DiLoCo
 *    diverges outside this range.
 *
 * See `docs/cluster.md` (Phase 11.4) for the full user-facing docs.
 */
@Serializable
data class DistributedConfig(
    @SerialName("config_schema_version")
    val configSchemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val strategy: Strategy = Strategy.P2P,
    val sharding: Sharding = Sharding(),
    val diloco: DiLoCo = DiLoCo(),
    val p2p: P2P = P2P(),
    val logs: Log = Log(),
    val durability: Durability = Durability(),
    val thermalGuard: Boolean = true,
    val nanGuard: Boolean = true,
) {
    enum class Strategy {
        /** Default. Existing ring all-reduce over GradRingPacket. Reuses
         *  the live ClusterTrainer.runRing loop; no new wire surface. */
        P2P,

        /** Inner AdamW per peer, outer Nesterov averaging every innerSteps.
         *  Couples to the same GradRingPacket envelope but at a different
         *  cadence. */
        DILOCO,

        /** Desktop-class peer (laptop, server, workstation) hosts the
         *  real trainer via core-ssh. The Android phone becomes an
         *  OBSERVER and shows the same live stats. */
        ACCELERATE,
    }

    @Serializable
    data class Sharding(
        val mode: Mode = Mode.LAYER,
        val optimizerOffload: Offload = Offload.NONE,
        val activationOffload: Boolean = false,
        val checkpointEvery: Int = 100,
        val keepLastN: Int = 5,
    ) {
        enum class Mode { LAYER, TENSOR, PIPELINE, REPLICATED, AUTO }
        enum class Offload { NONE, CPU, DISK }
    }

    @Serializable
    data class DiLoCo(
        val innerSteps: Int = 500,
        /**
         * Outer Nesterov learning rate. Must be in `[0.1, 1.0]` —
         * enforcement happens in [init]. Defaults to 0.7 per the
         * DiLoCo paper's stable operating point.
         */
        val outerLr: Double = 0.7,
    )

    @Serializable
    data class P2P(
        /** Max step-staleness for a rejoined peer. Updates older than
         *  this are dropped (logged via MeshlitEvent). */
        val maxStaleness: Int = 8,
        /** Seconds between matchmaking attempts. Mirrors the
         *  ClusterCoordinator electionIntervalMs. */
        val minMatchmakingDelaySec: Double = 5.0,
    )

    @Serializable
    data class Log(
        val intervalSec: Double = 5.0,
        val webui: Boolean = true,
    )

    @Serializable
    data class Durability(
        /** Opaque resume token surfaced by the previous run. Used by
         *  TrainingResumeService to rehydrate after a process crash. */
        val resumeToken: String? = null,
        val onCrash: Behaviour = Behaviour.RESUME,
    ) {
        enum class Behaviour { RESUME, FAIL }
    }

    init {
        require(configSchemaVersion == CURRENT_SCHEMA_VERSION) {
            "configSchemaVersion $configSchemaVersion != current $CURRENT_SCHEMA_VERSION"
        }
        require(diloco.outerLr in OUTER_LR_MIN..OUTER_LR_MAX) {
            "outerLr must be in [$OUTER_LR_MIN, $OUTER_LR_MAX]; got ${diloco.outerLr}"
        }
        require(sharding.keepLastN in KEEP_LAST_N_MIN..KEEP_LAST_N_MAX) {
            "keepLastN must be in [$KEEP_LAST_N_MIN, $KEEP_LAST_N_MAX]; got ${sharding.keepLastN}"
        }
        require(sharding.checkpointEvery in CHECKPOINT_EVERY_MIN..CHECKPOINT_EVERY_MAX) {
            "checkpointEvery must be in [$CHECKPOINT_EVERY_MIN, $CHECKPOINT_EVERY_MAX]; got ${sharding.checkpointEvery}"
        }
        require(p2p.maxStaleness >= 0) { "maxStaleness must be >= 0; got ${p2p.maxStaleness}" }
        require(p2p.minMatchmakingDelaySec > 0) {
            "minMatchmakingDelaySec must be > 0; got ${p2p.minMatchmakingDelaySec}"
        }
    }

    /**
     * Cross-field validation for the device fleet. Called by the
     * loader after peers are known. Returns a failure list — empty
     * means OK.
     */
    fun validateForPeers(peers: List<ClusterRoleValidation>): List<String> {
        val issues = mutableListOf<String>()
        val hasPhone = peers.any { it.isPhoneClass }
        if (strategy == Strategy.ACCELERATE && hasPhone) {
            // Phones can STILL join an Accelerate run as OBSERVER — they
            // just can't host the trainer. We don't reject, we just warn.
            issues += "accelerate_run_will_demote_phones_to_observer"
        }
        if (peers.isEmpty()) {
            issues += "config_requires_at_least_one_peer"
        }
        return issues
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        const val OUTER_LR_MIN: Double = 0.1
        const val OUTER_LR_MAX: Double = 1.0
        const val KEEP_LAST_N_MIN: Int = 1
        const val KEEP_LAST_N_MAX: Int = 50
        const val CHECKPOINT_EVERY_MIN: Int = 1
        const val CHECKPOINT_EVERY_MAX: Int = 10_000
    }
}

/**
 * Minimal peer shape consumed by [DistributedConfig.validateForPeers].
 * Avoids a hard dependency on [com.meshlit.core.cluster.NodeSnapshot] so
 * the config layer can be unit-tested in isolation.
 */
data class ClusterRoleValidation(
    val peerId: String,
    val isPhoneClass: Boolean,
    val vramMb: Long,
    val freeRamMb: Long,
)
