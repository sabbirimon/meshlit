package com.meshlit.di

import com.meshlit.agent.AgentCapabilityRegistrar
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.TrustTier
import com.meshlit.inference.PeerHealthCache
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin bindings for process-wide app-only singletons (the things
 * that lived on `MeshlitApplication` itself — `appScope`, the
 * `PeerHealthCache` setter, the FGS-shared mutable refs).
 *
 * Most `:core-*` singletons live in `coreModule`. This module
 * contains the cross-package bindings and the `KoinComponent`
 * mixin that the existing UI / FGS callsites hang off of.
 *
 * The conventional access pattern from consumers (e.g.
 * `MainActivity`, `InferenceForegroundService`) is:
 *
 * ```
 * private val app: MeshlitApplication = applicationContext as MeshlitApplication
 * val coordinator = app.inferenceCoordinator
 * ```
 *
 * To preserve the existing call signature without forcing every
 * consumer onto `getKoin().get()`, `MeshlitApplication` is now a
 * `KoinComponent`: every `by lazy { ... }` field was replaced
 * with a `getKoin().get<...>()`-backed property. The compiled
 * attribute names (`inferenceCoordinator`, `peerRegistry`,
 * `cloudCoordinator`, etc.) are unchanged, so the call sites
 * do not need to move.
 */

val appModule = module {

    // -----------------------------------------------------------------
    // FGS-bound volatile singletons — initially null, populated by
    // InferenceForegroundService.onCreate once the FGS has wired up
    // its peer-health-cache and stable-node-id state.
    //
    // `coreModule` declares the authoritative singletons; the entries
    // here are no-op forwarders kept for clarity at the boundary.
    // -----------------------------------------------------------------
    single { RefHolder<PeerHealthCache?>(initial = null) } // mirrors `activePeerHealthCache()`
    single { RefHolder<String>(initial = "") } // mirrors `stableNodeId`

    // -----------------------------------------------------------------
    // Default trust policy used at the LocalTrustPolicy.default()
    // lookup table. The real policy is set when a stable node id is
    // assigned. This binding only affects the *fallback* path — see
    // MeshlitApplication.setStableNodeId().
    // -----------------------------------------------------------------
    single(named("defaultTrustPolicy")) {
        DeviceTrustPolicy(
            nodeId = "",
            trustTier = TrustTier.LOCAL_TRUSTED,
            allowedRoles = setOf("brain", "tool", "monitor"),
            tokenExpiryMs = null,
            publicKeyFingerprint = null,
        )
    }
}

/**
 * Resolves a Koin singleton by reified type, isolating the
 * `GlobalContext.get()` boilerplate. Useful for callers that
 * cannot use the Kotlin KoinComponent extension (e.g. objects).
 */
inline fun <reified T : Any> koinInject(): T = GlobalContext.get().get<T>()
