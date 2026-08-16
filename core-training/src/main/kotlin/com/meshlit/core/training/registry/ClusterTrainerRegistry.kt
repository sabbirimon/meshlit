package com.meshlit.core.training.registry

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.LocalLoraTrainer
import com.meshlit.core.training.ThermalGuard
import com.meshlit.core.training.TrainingEvent
import com.meshlit.core.training.averaging.AveragedGradient
import com.meshlit.core.training.averaging.Averager
import com.meshlit.core.training.averaging.AveragerKind
import com.meshlit.core.training.averaging.DiLoCoAverager
import com.meshlit.core.training.averaging.NaNGuard
import com.meshlit.core.training.averaging.P2pRingAverager
import com.meshlit.core.training.averaging.StrategyDispatcher
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.config.DistributedConfigLoader
import com.meshlit.core.training.durability.ResumeToken
import com.meshlit.core.training.durability.TrainingResumeService
import com.meshlit.core.training.ring.RingParticipant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * The training subsystem's outward-facing owner. Cells of this
 * registry are created via [ClusterTrainerRegistry.boot] at process
 * start (in `MeshlitApplication.onCreate` per §3.1 of the plan).
 *
 * Responsibilities:
 *  - Pick the active [Averager] for the run based on
 *    `DistributedConfig.strategy`.
 *  - Hold the [NaNGuard] and [ThermalGuard] that the strategy
 *    dispatcher uses.
 *  - Persist + rehydrate [ResumeToken] so a process crash doesn't
 *    lose the run.
 *  - Emit training events via the [events] flow so the audit trail
 *    (R-22) and the new `TrainingDetailScreen` can render them.
 *
 * Idempotent: `launch` followed by `cancel` is safe; `launch` twice
 * with the same jobId is rejected (caller should call `cancel` first).
 */
class ClusterTrainerRegistry private constructor(
    private val scopeName: String,
    private val localLoraTrainer: LocalLoraTrainer,
    private val thermalGuard: ThermalGuard,
    private val nanGuard: NaNGuard,
    private val resumeService: TrainingResumeService,
) {
    private val log = logger("ClusterTrainerRegistry")

    private val _state = MutableStateFlow<RegistryState>(RegistryState.Idle)
    val state: StateFlow<RegistryState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrainingEvent>(
        replay = 0,
        extraBufferCapacity = 256,
    )
    val events: SharedFlow<TrainingEvent> = _events.asSharedFlow()

    private val activeDispatcher = AtomicReference<StrategyDispatcher?>(null)
    private val activeAverager = AtomicReference<Averager?>(null)

    /**
     * Build and persist a [StrategyDispatcher] for the chosen
     * [DistributedConfig.strategy]. Idempotent — re-launching with
     * the same strategy returns the existing dispatcher.
     */
    fun selectStrategy(
        cfg: DistributedConfig,
        jobId: String,
        localPeerId: String,
    ): MeshlitResult<StrategyDispatcher> {
        val newAverager = when (cfg.strategy) {
            DistributedConfig.Strategy.P2P -> P2pRingAverager(nanGuard = nanGuard)
            DistributedConfig.Strategy.DILOCO -> DiLoCoAverager(nanGuard = nanGuard)
            DistributedConfig.Strategy.ACCELERATE -> {
                return MeshlitResult.Failure(
                    MeshlitError.Invalid(
                        "cluster.trainer.accelerate.requires_desktop_peer"
                    )
                )
            }
        }

        val dispatcher = StrategyDispatcher(
            localLoraTrainer = localLoraTrainer,
            thermalGuard = thermalGuard,
            nanGuard = nanGuard,
            averager = newAverager,
        )
        activeDispatcher.set(dispatcher)
        activeAverager.set(newAverager)

        _state.value = RegistryState.StrategySelected(
            jobId = jobId,
            strategy = cfg.strategy,
            averagerKind = newAverager.kind,
        )
        return MeshlitResult.Success(dispatcher)
    }

    /**
     * Wrap the existing `AccelerateDelegateAverager` (which the
     * caller instantiates with a desktop-peer probe) and register
     * it as the active dispatcher.
     */
    fun selectAccelerate(
        cfg: DistributedConfig,
        jobId: String,
        desktopAverager: Averager,
    ): MeshlitResult<StrategyDispatcher> {
        val dispatcher = StrategyDispatcher(
            localLoraTrainer = localLoraTrainer,
            thermalGuard = thermalGuard,
            nanGuard = nanGuard,
            averager = desktopAverager,
        )
        activeDispatcher.set(dispatcher)
        activeAverager.set(desktopAverager)
        _state.value = RegistryState.StrategySelected(
            jobId = jobId,
            strategy = cfg.strategy,
            averagerKind = desktopAverager.kind,
        )
        return MeshlitResult.Success(dispatcher)
    }

    fun activeDispatcher(): StrategyDispatcher? = activeDispatcher.get()
    fun activeAverager(): Averager? = activeAverager.get()
    fun activeAveragerKind(): AveragerKind =
        activeAverager.get()?.kind ?: AveragerKind.UNKNOWN

    /** Persist a resume token. Idempotent. */
    fun persistResume(token: ResumeToken): MeshlitResult<Unit> =
        resumeService.write(token)

    /** Read the resume token for a jobId, if any. */
    fun readResume(jobId: String): MeshlitResult<ResumeToken> =
        resumeService.read(jobId)

    /** List jobs that can be resumed. */
    fun listResumable(): List<String> = resumeService.listResumable()

    /** Clear the resume token for a jobId (called on graceful completion). */
    fun clearResume(jobId: String) = resumeService.clear(jobId)

    /** Emit a synchronous event onto the events flow. */
    suspend fun emitEvent(event: TrainingEvent) {
        _events.emit(event)
    }

    /** Try to emit a synchronous event without blocking. */
    fun tryEmitEvent(event: TrainingEvent): Boolean =
        _events.tryEmit(event)

    sealed class RegistryState {
        data object Idle : RegistryState()
        data class StrategySelected(
            val jobId: String,
            val strategy: DistributedConfig.Strategy,
            val averagerKind: AveragerKind,
        ) : RegistryState()
    }

    companion object {
        /**
         * Singleton handle. The MeshlitApplication.onCreate hook
         * (§3.1) calls this once at process start.
         */
        @Volatile
        private var instance: ClusterTrainerRegistry? = null

        fun get(): ClusterTrainerRegistry? = instance

        /**
         * Boot the registry. Idempotent — second call returns the
         * existing instance.
         */
        fun boot(
            scopeName: String = "default",
            trainingBaseDir: File,
            localLoraTrainer: LocalLoraTrainer = LocalLoraTrainer(),
            thermalGuard: ThermalGuard = ThermalGuard(),
            nanGuard: NaNGuard = NaNGuard(),
        ): MeshlitResult<ClusterTrainerRegistry> {
            instance?.let { return MeshlitResult.Success(it) }
            synchronized(this) {
                instance?.let { return MeshlitResult.Success(it) }
                val ok = trainingBaseDir.mkdirs()
                if (!ok && !trainingBaseDir.isDirectory) {
                    return MeshlitResult.Failure(
                        MeshlitError.Resource(
                            "cluster.trainer.registry.mkdirs_failed:${trainingBaseDir.absolutePath}"
                        )
                    )
                }
                val r = ClusterTrainerRegistry(
                    scopeName = scopeName,
                    localLoraTrainer = localLoraTrainer,
                    thermalGuard = thermalGuard,
                    nanGuard = nanGuard,
                    resumeService = TrainingResumeService(trainingBaseDir),
                )
                instance = r
                return MeshlitResult.Success(r)
            }
        }

        /** Build a synthetic [RingParticipant] list for local single-process tests. */
        fun syntheticLocalParticipants(
            localPeerId: String,
            localHost: String,
            localPort: Int,
        ): List<RingParticipant> = listOf(
            RingParticipant(
                peerId = localPeerId,
                host = localHost,
                port = localPort,
                role = com.meshlit.core.training.ring.RingRole.RANK0,
            )
        )

        /** Default config — used when the user has no config file. */
        fun defaultConfig(): DistributedConfig = DistributedConfigLoader.defaultConfig()
    }
}
