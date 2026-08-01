package com.meshlit.capability

import android.os.Build

/**
 * Runtime feature tier. Derived once per process from
 * [Build.VERSION.SDK_INT] and exposed as a single source of truth so
 * individual screens and the FGS don't have to scatter
 * `Build.VERSION.SDK_INT >= …` checks.
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
        /** Computed once per process. Cheap to call. */
        fun current(): CapabilityTier = when {
            Build.VERSION.SDK_INT >= 36 -> FULL
            Build.VERSION.SDK_INT >= 34 -> MID
            else -> LITE
        }
    }
}
