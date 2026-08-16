package com.meshlit.di

import com.meshlit.MeshlitApplication
import com.meshlit.core.trust.DeviceTrustPolicy
import com.meshlit.core.trust.TrustTier
import com.meshlit.inference.PeerHealthCache
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin bindings for process-wide app-only singletons (the things
 * that lived on `MeshlitApplication` itself — `appScope`, the
 * `PeerHealthCache` setter, the FGS-shared mutable refs).
 *
 * Most `:core-*` singletons live in `coreModule`. This module
 * contains the cross-package bindings and the application-singleton
 * reference.
 *
 * Phase 0.3 — consumers resolve the application instance through
 * Koin instead of casting `applicationContext as MeshlitApplication`:
 *
 * ```
 * val app: MeshlitApplication = koinInject()
 * val coordinator = koinInject<InferenceCoordinator>()
 * ```
 *
 * The single cast that remains lives here in the DI definition site.
 */

val appModule = module {

    // -----------------------------------------------------------------
    // The Application instance itself — bound so call sites can
    // resolve `MeshlitApplication` via `koinInject<MeshlitApplication>()`
    // without the `applicationContext as MeshlitApplication` cast.
    // The cast lives here (the DI definition site) rather than at
    // every consumer.
    // -----------------------------------------------------------------
    single { androidContext() as MeshlitApplication }

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
