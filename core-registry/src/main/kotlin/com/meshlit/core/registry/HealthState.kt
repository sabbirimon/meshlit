package com.meshlit.core.registry

/**
 * Liveness of a registered service. Updated by the lifecycle
 * controller's health-check loop and emitted on the
 * [ServiceDescriptor.health] field.
 *
 * - [Healthy] — the most recent health check returned ok.
 * - [Degraded] — running but with reduced capacity (slow, dropping
 *   requests, etc.). The router may still dispatch to it but with a
 *   heavier retry cost.
 * - [Unreachable] — the most recent health check failed outright.
 *   Router should re-route around this node.
 * - [Unknown] — never health-checked (e.g. just registered). The
 *   router should treat this as Degraded until proven otherwise.
 */
sealed interface HealthState {
    data object Healthy : HealthState
    data class Degraded(val reason: String) : HealthState
    data class Unreachable(val reason: String) : HealthState
    data object Unknown : HealthState
}
