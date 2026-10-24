package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.RingParticipant

/** Receives actual owner-probed desktop gradients. Missing/offline/syncing data fails explicitly.
 * This contract does not install or execute a desktop trainer; use the optional Soup companion.
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
        if(status is DesktopPeerStatus.Offline) return MeshlitResult.Failure(
            MeshlitError.Resource("cluster.trainer.accelerate.peer_offline")
        )
        if(status is DesktopPeerStatus.Syncing) return MeshlitResult.Failure(
            MeshlitError.Resource("cluster.trainer.accelerate.peer_syncing")
        )
        // Online: pull the averaged gradient the desktop shipped to
        // /v1/cluster/plan/{runId}/step/{step}. The desktop has
        // already applied FSDP averaging on its side; we just
        // receive the result.
        val desktop = status as DesktopPeerStatus.Online
        val averaged = desktop.lastAveragedGradient ?: return MeshlitResult.Failure(MeshlitError.Invalid("cluster.trainer.accelerate.gradient_missing"))
        if(averaged.size!=localGradient.size || averaged.any{!it.isFinite()}) return MeshlitResult.Failure(MeshlitError.Invalid("cluster.trainer.accelerate.diverged"))
        val clean = if(nanGuard==null) averaged else nanGuard.checkAndDrop(averaged)
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
                loss = desktop.lastLoss.takeIf{it.isFinite()},
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
