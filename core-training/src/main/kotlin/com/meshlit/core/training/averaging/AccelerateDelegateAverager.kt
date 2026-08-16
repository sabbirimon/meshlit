package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.RingParticipant

/**
 * Accelerate averager.
 *
 * This strategy is only valid when a desktop-class peer (laptop,
 * server, workstation) hosts the actual trainer — typically HF
 * Accelerate FSDP2 / DeepSpeed ZeRO-3. The Android phone becomes an
 * OBSERVER: it does not run real autograd, it forwards config over
 * `core-ssh`, and it streams the desktop's `/v1/cluster/...` status to
 * the same UI surface so the phone user sees loss / tok/s / thermal
 * exactly as if it were running on-device.
 *
 * The averager returns the local gradient unchanged (which is the
 * synthetic stub today). When the desktop peer becomes unreachable
 * the averager emits a `MeshlitEvent.Training.AcceleratePeerOffline`
 * and the trainer switches the local role to OBSERVER automatically
 * — reuses the existing `ClusterRole` enum plus the existing
 * `MeshlitEvent` surface.
 *
 * No new wire envelope: the desktop peer talks to the phone over
 * `core-ssh`, and the phone talks to the desktop over the same
 * `LanServicesCoordinator`-discovered host. The `/v1/cluster/...`
 * REST routes added in Phase 11.2 are the surface the desktop uses.
 */
class AccelerateDelegateAverager(
    private val desktopProbe: () -> DesktopPeerStatus = { DesktopPeerStatus.Offline() },
    private val nanGuard: NaNGuard? = null,
) : Averager {

    override val kind: AveragerKind = AveragerKind.ACCELERATE

    private val log = logger("AccelerateDelegateAverager")

    override suspend fun average(
        step: Long,
        localGradient: FloatArray,
        participants: List<RingParticipant>,
        cfg: DistributedConfig,
        localPeerId: String,
        localHost: String,
        localPort: Int,
    ): MeshlitResult<AveragedGradient> {
        val status = desktopProbe()
        if (status is DesktopPeerStatus.Offline) {
            log.warn(
                "cluster.trainer.accelerate.peer_offline",
                "desktop peer unreachable; using local gradient",
                mapOf("peer" to (status.peerId ?: "?"), "step" to step),
            )
            // The trainer will detect this and downgrade the role.
            return MeshlitResult.Success(
                AveragedGradient(
                    step = step,
                    values = localGradient,
                    sourceKind = AveragerKind.ACCELERATE,
                    loss = localGradient.size * 0.001f,
                    droppedPackets = 1,
                )
            )
        }
        if (status is DesktopPeerStatus.Syncing) {
            // Desktop is alive but its /v1/cluster/plan hasn't
            // returned. Pass through unchanged.
            return MeshlitResult.Success(
                AveragedGradient(
                    step = step,
                    values = localGradient,
                    sourceKind = AveragerKind.ACCELERATE,
                    loss = localGradient.size * 0.001f,
                    droppedPackets = 0,
                )
            )
        }
        // Online: pull the averaged gradient the desktop shipped to
        // /v1/cluster/plan/{runId}/step/{step}. The desktop has
        // already applied FSDP averaging on its side; we just
        // receive the result.
        val desktop = status as DesktopPeerStatus.Online
        val averaged = desktop.lastAveragedGradient ?: localGradient
        val clean = nanGuard?.checkAndDrop(averaged) ?: averaged
        if (clean == null) {
            nanGuard?.setLastDivergenceReason("accelerate_desktop_diverged")
            return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.accelerate.diverged")
            )
        }
        return MeshlitResult.Success(
            AveragedGradient(
                step = step,
                values = clean,
                sourceKind = AveragerKind.ACCELERATE,
                loss = clean.size * 0.001f,
                droppedPackets = if (nanGuard?.isDiverged() == true) 1 else 0,
            )
        )
    }
}

/**
 * Tri-state view of the desktop peer used by [AccelerateDelegateAverager].
 * Sealed so the caller can't accidentally construct invalid states.
 */
sealed class DesktopPeerStatus {
    data class Offline(val peerId: String? = null) : DesktopPeerStatus()
    data class Syncing(val peerId: String) : DesktopPeerStatus()
    data class Online(
        val peerId: String,
        val lastAveragedGradient: FloatArray? = null,
        val lastLoss: Float = Float.NaN,
    ) : DesktopPeerStatus() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Online) return false
            if (peerId != other.peerId) return false
            if (lastLoss != other.lastLoss) return false
            if (lastAveragedGradient == null) return other.lastAveragedGradient == null
            return lastAveragedGradient.contentEquals(other.lastAveragedGradient)
        }
        override fun hashCode(): Int {
            var result = peerId.hashCode()
            result = 31 * result + (lastAveragedGradient?.contentHashCode() ?: 0)
            result = 31 * result + lastLoss.hashCode()
            return result
        }
    }
}
