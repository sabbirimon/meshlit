package com.meshlit.core.bootstrap

/**
 * The discrete phases a [BootstrapCoordinator] walks through on app
 * start. Each phase is reported in [BootstrapReport.entries] so the
 * UI / log can show "Config ✓ → Flags ✓ → Probe … → Role …".
 *
 * Phase 0.1 only ships the Config + Flags phases. Probe, Role,
 * Registry, and Services phases arrive in 0.2 / 0.3 — when each
 * lands, add it here rather than stubbing it ahead of time (the
 * project follows "don't write cross-cutting glue until the pieces
 * exist").
 */
enum class BootstrapPhase {
    /** Load / generate / persist the stable node id. */
    Config,

    /** Hot-load feature flag defaults from persistence. */
    Flags,

    /** Profile CPU/RAM/thermal/battery/network/NPU. (Phase 0.2) */
    Probe,

    /** Suggest a role from the latest capability snapshot. (0.3) */
    Role,

    /** Bring up the local service registry. (0.2) */
    Registry,

    /** Start the registered [ManagedService]s whose flags allow. (0.2) */
    Services,
}
