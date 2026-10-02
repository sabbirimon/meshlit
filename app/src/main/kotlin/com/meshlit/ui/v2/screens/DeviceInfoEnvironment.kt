package com.meshlit.ui.v2.screens

import com.meshlit.DeviceInfo
import com.meshlit.capability.CapabilityTier

/**
 * Read-only environment accessor for the `DeviceInfoViewModel`.
 *
 * The ViewModel reads five fields off the host application —
 * node id, local IP, HTTP server port, capability tier, and the
 * auto-derived device display name. Pulling these out of the
 * `MeshlitApplication` lets the unit tests construct a fake
 * environment without instantiating an `android.app.Application`
 * (which would otherwise require Robolectric).
 *
 * The production implementation lives at the bottom of this file
 * and simply forwards the getters on `MeshlitApplication`.
 */
interface DeviceInfoEnvironment {
    val nodeIdHex: String
    val localIpAddress: String
    val httpServerPort: Int
    val capabilityTier: CapabilityTier
    val deviceInfo: DeviceInfo

    /** Side-effect setter so the VM can pin the trust policy when
     *  a fresh bootstrap re-publishes a new node id. */
    fun setStableNodeId(id: String)
}
