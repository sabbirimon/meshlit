package com.meshlit.core.common

/**
 * Runtime feature tier. Computed from `Build.VERSION.SDK_INT` (Android)
 * or the equivalent host-OS probe (Linux/ChromeOS/Waydroid) — see
 * [CapabilityTier.fromSdkInt] for the canonical Android thresholds.
 *
 * The `:app` module exposes a `com.meshlit.capability.CapabilityTier`
 * that delegates to this enum for backward compatibility, but the
 * wire format (`HealthResponse`, `PeerCapabilities`, `ShardSpec`)
 * uses this core-common definition so `:core-inference` doesn't have
 * to depend on `:app`.
 *
 *  - [LITE]  : API ≤ 33 (Android 10–13). Bundled model + FGS + remote
 *              dispatch work. No `dataSync` FGS type, no
 *              `onTimeout()`. Some settings are hidden.
 *  - [MID]   : API 34–35 (Android 14–15). Adds `dataSync` FGS type,
 *              runtime post-notifications, FGS `onTimeout()` cap.
 *  - [FULL]  : API ≥ 36 (Android 16+). All features unlocked including
 *              hardware-backed key attestation hooks and the eGPU
 *              toggle.
 *
 * The thresholds match AGP / D8 / Android-version boundaries:
 *  - API 33 is the first level where D8 emits DEX 040 bytecode
 *    (required by Ktor 3.2.0).
 *  - API 34 is when `FOREGROUND_SERVICE_DATA_SYNC` became a
 *    mandatory permission.
 *  - API 36 (sdk 36 = Baklava preview at the time of writing) is the
 *    "FULL" tier — features behind `Build.VERSION_CODES.BAKLAVA` or
 *    higher are gated here.
 */
enum class CapabilityTier {
    LITE,
    MID,
    FULL;

    /** Convenience helper for screen-level feature gating. */
    val allowsDataSyncForegroundService: Boolean
        get() = this != LITE

    val allowsForegroundServiceTimeout: Boolean
        get() = this != LITE

    val allowsRuntimePostNotifications: Boolean
        get() = this != LITE

    val allowsHardwareBackedAttestation: Boolean
        get() = this == FULL

    val allowsEgpuToggle: Boolean
        get() = this == FULL

    companion object {
        /** Thresholds derived from the canonical Android SDK table. */
        const val SDK_FULL = 36
        const val SDK_MID = 34

        /**
         * Map an Android `Build.VERSION.SDK_INT` value to the
         * matching tier. The `:app` module wraps this in its own
         * `CapabilityTier.current()` helper.
         */
        fun fromSdkInt(sdkInt: Int): CapabilityTier = when {
            sdkInt >= SDK_FULL -> FULL
            sdkInt >= SDK_MID -> MID
            else -> LITE
        }
    }
}