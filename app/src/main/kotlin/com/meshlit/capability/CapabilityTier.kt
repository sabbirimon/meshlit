package com.meshlit.capability

import android.os.Build
import com.meshlit.core.common.CapabilityTier as CoreCapabilityTier

/**
 * Type alias for the canonical [CoreCapabilityTier] (defined in
 * `:core-common` so wire types like `HealthResponse` can carry it
 * without pulling in `:app`). The Android-specific factory lives
 * here because it's the only place that knows about
 * [Build.VERSION.SDK_INT].
 *
 * Use the alias in place of the original enum so existing screens
 * and the FGS compile without edits. Calls go through
 * [currentCapabilityTier] for the Android-specific factory.
 */
typealias CapabilityTier = CoreCapabilityTier

/** Process-wide current tier — Android SDK_INT → tier table. */
fun currentCapabilityTier(): CapabilityTier =
    CapabilityTier.fromSdkInt(Build.VERSION.SDK_INT)
