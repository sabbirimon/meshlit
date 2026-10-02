package com.meshlit.ui.v2.screens

import com.meshlit.core.common.NetworkScope
import com.meshlit.core.common.RemoteEndpoint

/**
 * Sealed UiState for the v2 `DevicesScreen`. Replaces the v1
 * `DevicesScreen.kt:147-149` pattern that read three flows off
 * `SettingsRepository` inline in a composable.
 *
 * Loading is its own state (not a flag on `Ready`) so the
 * screen can render a deterministic skeleton during the first
 * emission without the "loading spinner inside an empty card"
 * smell that v1 has today.
 */
sealed interface DevicesUiState {

    object Loading : DevicesUiState

    data class Ready(
        val scope: NetworkScope,
        val endpoints: List<RemoteEndpoint>,
        val activeEndpointId: String?,
        val ownPayload: PairingPayload,
    ) : DevicesUiState

    data class Failure(val message: String) : DevicesUiState
}

/**
 * Pairing QR payload — wraps the v1 `LocalPeerDescriptor` so
 * the v2 screen can render the same fields without importing
 * any v1 UI types. The `(descriptor, qrString)` tuple holds the
 * structured descriptor (for the textual fallback) and the
 * pre-rendered QR string (for `QrPairingSheet`).
 */
data class PairingPayload(
    val descriptor: String,
    val qrString: String,
)