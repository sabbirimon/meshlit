package com.meshlit.core.cloudmcp.android

/**
 * Current state of [MeshlitAccessibilityService] on the device.
 * The Cloud Hub and the Agent Terminal use this to decide
 * whether to render the "Tap to enable" card or the active
 * automation tools.
 *
 *  - [Disabled] — service is in the manifest but the user has
 *    not enabled it in `Settings → Accessibility`.
 *  - [Missing] — the binary doesn't include the service (older
 *    build, or the service class failed to load).
 *  - [Enabled] — service is bound, [serviceName] is the
 *    Android-resolved service component name (e.g.
 *    `com.meshlit/.core.cloudmcp.android.MeshlitAccessibilityService`).
 */
sealed class AccessibilityServiceStatus {
    object Disabled : AccessibilityServiceStatus()
    object Missing : AccessibilityServiceStatus()
    data class Enabled(val serviceName: String) : AccessibilityServiceStatus()
}