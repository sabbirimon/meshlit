package com.meshlit.core.training

import kotlinx.serialization.Serializable

/**
 * Training events emitted to the existing `core-observability` audit
 * trail (R-22). The class is intentionally narrow: every event has a
 * stable `tag` for telemetry + logging, and a small set of typed
 * fields that the autoPilot audit trail persists.
 *
 * The events match the §3.6 table in the plan file. Subscribers (the
 * existing `LogScreen`, the new `TrainingDetailScreen`) iterate via
 * `when (event)` over the subtypes — adding a new event means
 * extending the sealed hierarchy + adding the matching `when` arm
 * at every consumer.
 *
 * Severity: [Info] / [Warn] / [Error] — used by the UI to colour
 * log entries and by the AutoPilot repair executor to decide which
 * `RepairAction` to run.
 */
@Serializable
sealed class TrainingEvent {
    abstract val tag: String
    abstract val severity: Severity
    abstract val jobId: String?
    abstract val step: Long?
    abstract val peerId: String?

    enum class Severity { INFO, WARN, ERROR }

    @Serializable
    data class Launched(
        val strategy: String,
        val peerCount: Int,
        override val jobId: String,
        override val peerId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.launched"
        override val severity: Severity get() = Severity.INFO
        override val step: Long? get() = null
    }

    @Serializable
    data class StrategySelected(
        val strategy: String,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.strategy_selected"
        override val severity: Severity get() = Severity.INFO
        override val step: Long? get() = null
        override val peerId: String? = null
    }

    @Serializable
    data class ShardsReassigned(
        val from: String,
        val to: String,
        val reason: String,
        override val jobId: String,
        override val step: Long? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.shards_reassigned"
        override val severity: Severity get() = Severity.INFO
        override val peerId: String? = null
    }

    @Serializable
    data class HostReelected(
        val prev: String,
        val next: String,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.host_reelected"
        override val severity: Severity get() = Severity.INFO
        override val step: Long? = null
        override val peerId: String? = null
    }

    @Serializable
    data class NaNDropped(
        val droppedRatioPct: Int,
        override val jobId: String,
        override val step: Long,
        override val peerId: String,
    ) : TrainingEvent() {
        override val tag: String get() = "training.nan_dropped"
        override val severity: Severity get() = Severity.WARN
    }

    @Serializable
    data class Diverged(
        val ratioPct: Int,
        val reason: String,
        override val jobId: String,
        override val step: Long,
    ) : TrainingEvent() {
        override val tag: String get() = "training.diverged"
        override val severity: Severity get() = Severity.ERROR
        override val peerId: String? = null
    }

    @Serializable
    data class ThermalThrottled(
        val currentC: Float,
        val maxC: Float,
        override val peerId: String,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.thermal_throttled"
        override val severity: Severity get() = Severity.WARN
        override val step: Long? = null
    }

    @Serializable
    data class CoordinatorLowBattery(
        val batteryPct: Int,
        override val peerId: String,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.coordinator_low_battery"
        override val severity: Severity get() = Severity.WARN
        override val step: Long? = null
    }

    @Serializable
    data class AcceleratePeerOffline(
        val desktopPeerId: String?,
        override val jobId: String,
        override val step: Long,
    ) : TrainingEvent() {
        override val tag: String get() = "training.accelerate_peer_offline"
        override val severity: Severity get() = Severity.ERROR
        override val peerId: String? = desktopPeerId
    }

    @Serializable
    data class DiskFull(
        val checkpoint: String,
        override val jobId: String,
        override val step: Long,
    ) : TrainingEvent() {
        override val tag: String get() = "training.disk_full"
        override val severity: Severity get() = Severity.ERROR
        override val peerId: String? = null
    }

    @Serializable
    data class Resumed(
        override val jobId: String,
        override val step: Long,
    ) : TrainingEvent() {
        override val tag: String get() = "training.resumed"
        override val severity: Severity get() = Severity.INFO
        override val peerId: String? = null
    }

    @Serializable
    data class WireVersionRejected(
        val got: Int,
        val expected: Int,
        override val peerId: String,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.wire_version_rejected"
        override val severity: Severity get() = Severity.WARN
        override val step: Long? = null
    }

    @Serializable
    data class ConfigVersionRejected(
        val got: Int,
        val expected: Int,
        override val jobId: String? = null,
    ) : TrainingEvent() {
        override val tag: String get() = "training.config_version_rejected"
        override val severity: Severity get() = Severity.ERROR
        override val step: Long? = null
        override val peerId: String? = null
    }

    @Serializable
    data class Completed(
        val totalSteps: Int,
        override val jobId: String,
    ) : TrainingEvent() {
        override val tag: String get() = "training.completed"
        override val severity: Severity get() = Severity.INFO
        override val step: Long get() = totalSteps.toLong()
        override val peerId: String? = null
    }
}
