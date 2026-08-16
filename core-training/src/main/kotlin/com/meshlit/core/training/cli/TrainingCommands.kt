package com.meshlit.core.training.cli

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.config.DistributedConfigLoader
import com.meshlit.core.training.durability.ResumeToken
import com.meshlit.core.training.registry.ClusterTrainerRegistry

/**
 * Phase 11.2 — thin CLI surface that exposes the training subsystem
 * over `core-ssh` (or any future shell host). The functions here are
 * stateless; all persistent state lives on [ClusterTrainerRegistry].
 *
 * Contract:
 *  - All methods return `MeshlitResult<Unit>` so the calling shell
 *    can surface typed errors without re-parsing.
 *  - Idempotent: `join("x")` followed by `join("x")` returns the
 *    same final state.
 *  - Side effects: [join] / [leave] / [run] mutate the registry;
 *    [status] / [plan] / [logs] / [benchmark] are read-only.
 *
 * Wire mirror:
 *  The HTTP `/v1/cluster/...` routes (Phase 11.2) and the CLI calls
 *  here both delegate to the same registry, so a desktop peer driving
 *  the hive via SSH sees the same effect as a phone driving it via
 *  the HTTP surface.
 */
class TrainingCommands(
    private val registryProvider: () -> ClusterTrainerRegistry?,
    private val nodeIdProvider: () -> String,
    private val localPeerIdProvider: () -> String,
) {

    private val log = logger("TrainingCommands")

    /** `training join` — admit this node into the run. */
    fun join(jobId: String): MeshlitResult<Unit> {
        val reg = registryProvider() ?: return noRegistry()
        val cfg = DistributedConfigLoader.defaultConfig()
        return when (val res = reg.selectStrategy(
            cfg = cfg,
            jobId = jobId,
            localPeerId = localPeerIdProvider().ifBlank { "self" },
        )) {
            is MeshlitResult.Success -> {
                log.info("cli.join", "joined", mapOf("jobId" to jobId))
                MeshlitResult.Success(Unit)
            }
            is MeshlitResult.Failure -> res
        }
    }

    /** `training leave` — exit the run. Idempotent. */
    fun leave(jobId: String): MeshlitResult<Unit> {
        // v0 — leaving is a no-op on the registry; the FGS owns the
        // actual lifecycle. We only clear any local resume token.
        val reg = registryProvider() ?: return noRegistry()
        runCatching { reg.clearResume(jobId) }
        log.info("cli.leave", "left", mapOf("jobId" to jobId))
        return MeshlitResult.Success(Unit)
    }

    /** `training status` — surface the current registry state. */
    fun status(): MeshlitResult<String> {
        val reg = registryProvider() ?: return noRegistry()
        val state = reg.state.value
        return MeshlitResult.Success(
            when (state) {
                is ClusterTrainerRegistry.RegistryState.Idle -> "idle"
                is ClusterTrainerRegistry.RegistryState.StrategySelected -> {
                    val s = state
                    "running strategy=${s.strategy.name} jobId=${s.jobId} " +
                        "averager=${s.averagerKind.name} peer=${nodeIdProvider()}"
                }
            }
        )
    }

    /** `training run <jobId> --strategy p2p|diloco|accelerate`. */
    fun run(jobId: String, strategy: String): MeshlitResult<Unit> {
        val strat = parseStrategy(strategy)
            ?: return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid(
                    "cluster.cli.unknown_strategy:$strategy"
                )
            )
        val reg = registryProvider() ?: return noRegistry()
        val cfg = DistributedConfigLoader.defaultConfig().copy(strategy = strat)
        return when (val res = reg.selectStrategy(
            cfg = cfg,
            jobId = jobId,
            localPeerId = localPeerIdProvider().ifBlank { "self" },
        )) {
            is MeshlitResult.Success -> {
                log.info(
                    "cli.run",
                    "run admitted",
                    mapOf("jobId" to jobId, "strategy" to strat.name),
                )
                MeshlitResult.Success(Unit)
            }
            is MeshlitResult.Failure -> res
        }
    }

    /** `training plan` — print the active jobId's sharding plan. v0
     *  reads from the registry state; a follow-up wires
     *  ShardingPlanner.compute(...) into the run-admit path. */
    fun plan(): MeshlitResult<String> {
        val reg = registryProvider() ?: return noRegistry()
        val state = reg.state.value
        return when (state) {
            is ClusterTrainerRegistry.RegistryState.Idle ->
                MeshlitResult.Success("no active plan")
            is ClusterTrainerRegistry.RegistryState.StrategySelected ->
                MeshlitResult.Success(
                    "plan jobId=${state.jobId} strategy=${state.strategy.name} " +
                        "averager=${state.averagerKind.name}\n" +
                        "(per-shard assignments computed on first step — see /v1/cluster/plan/{runId})"
                )
        }
    }

    /** `training logs <jobId>` — surface the resume token (cheap). */
    fun logs(jobId: String): MeshlitResult<String> {
        val reg = registryProvider() ?: return noRegistry()
        return when (val res = reg.readResume(jobId)) {
            is MeshlitResult.Success -> MeshlitResult.Success(
                "resume token: ${res.value.signature.take(16)}…\n" +
                    "step=${res.value.step} peerId=${res.value.peerId}"
            )
            is MeshlitResult.Failure -> res
        }
    }

    /** `training benchmark` — synthetic 1-step run on the local peer. */
    fun benchmark(): MeshlitResult<String> {
        val reg = registryProvider() ?: return noRegistry()
        // v0 — no real benchmark path; we report the active averager
        // kind and the registry's idle/running state. A follow-up
        // wires `LocalLoraTrainer.computeLocalGradient` through the
        // dispatcher.
        val state = reg.state.value
        val kind = reg.activeAveragerKind().name
        val stateName = when (state) {
            is ClusterTrainerRegistry.RegistryState.Idle -> "idle"
            is ClusterTrainerRegistry.RegistryState.StrategySelected -> "selected"
        }
        return MeshlitResult.Success(
            "benchmark averager=$kind state=$stateName (synthetic — see Phase 11.3)"
        )
    }

    /** Optional helper: validate a resume token's signature. */
    fun verifyResume(token: ResumeToken): Boolean = token.isValid()

    private fun parseStrategy(raw: String): DistributedConfig.Strategy? =
        when (raw.trim().uppercase()) {
            "P2P" -> DistributedConfig.Strategy.P2P
            "DILOCO" -> DistributedConfig.Strategy.DILOCO
            "ACCELERATE" -> DistributedConfig.Strategy.ACCELERATE
            else -> null
        }

    private fun noRegistry(): MeshlitResult<Nothing> =
        MeshlitResult.Failure(
            com.meshlit.core.common.MeshlitError.Resource(
                "cluster.cli.registry_not_initialized"
            )
        )

    companion object {
        /**
         * Factory used by the future `core-ssh` shell host. Kept as
         * a free function so unit tests can wire a fake registry
         * without touching [MeshlitApplication].
         */
        fun from(
            registryProvider: () -> ClusterTrainerRegistry?,
            nodeIdProvider: () -> String,
            localPeerIdProvider: () -> String,
        ): TrainingCommands = TrainingCommands(
            registryProvider = registryProvider,
            nodeIdProvider = nodeIdProvider,
            localPeerIdProvider = localPeerIdProvider,
        )
    }
}