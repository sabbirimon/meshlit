package com.meshlit.ui.v2.di

import com.meshlit.MeshlitApplication
import com.meshlit.ui.v2.screens.DeviceInfoEnvironment
import org.koin.dsl.module

/**
 * Koin bindings for v2-only types. The four v2 ViewModels
 * (`DevicesViewModel`, `ClusterViewModel`, `AgentViewModel`,
 * `SettingsViewModel`) each carry their own static
 * `factory(): ViewModelProvider.Factory` method that uses
 * `koinInject()` to resolve their dependencies, so this module
 * stays empty for build no. 1.
 *
 * Reserved for follow-up work — e.g. when a v2-only repository
 * lands (per the plan §4 wiring for `DeviceProfileRepository`),
 * the binding will live here so the v1 build never accidentally
 * resolves it.
 *
 * This module is registered by `MeshlitApplication.startKoin`
 * alongside `coreModule` and `appModule`. Removing the v2 build
 * (delete the `meshlitV2` flavor) leaves the v1 build intact —
 * the v2 module just stops being referenced.
 */
val v2CoreModule = module {
    // The DeviceInfoViewModel pulls five fields + a single setter
    // off the application instance. Resolving them through a
    // small interface lets unit tests construct a fake without
    // booting Robolectric (MeshlitApplication extends android.app.Application).
    single<DeviceInfoEnvironment> { ApplicationDeviceInfoEnvironment(get()) }
}

/**
 * Production [DeviceInfoEnvironment] that simply forwards the
 * reads to the live [MeshlitApplication]. `AppModule.kt` already
 * binds the application singleton, so we just inject it here.
 */
private class ApplicationDeviceInfoEnvironment(
    private val app: MeshlitApplication,
) : DeviceInfoEnvironment {
    override val nodeIdHex: String get() = app.nodeIdHex
    override val localIpAddress: String get() = app.localIpAddress
    override val httpServerPort: Int get() = app.httpServerPort
    override val capabilityTier: com.meshlit.capability.CapabilityTier get() = app.capabilityTier
    override val deviceInfo: com.meshlit.DeviceInfo get() = app.deviceInfo
    override fun setStableNodeId(id: String) { app.setStableNodeId(id) }
}
