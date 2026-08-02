package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier

/**
 * Optional seam the embedded [InferenceHttpServer] consults when
 * building a `/v1/health` reply. The default implementation returns
 * `null` for every field, so nodes that don't wire an enricher still
 * answer health checks in the original shape — old clients remain
 * happy.
 *
 * `:app` provides a richer implementation (in
 * `app/.../inference/HealthEnricherImpl.kt`) that pulls the live
 * capability tier, the engine tag, the loaded shard list, and the
 * `MetricsSnapshot` from the `MetricsRegistry`.
 *
 * This interface is in `:core-inference` so the server module
 * compiles without `:app` (same reasoning as [RouterRef] and
 * [Forwarder]).
 */
fun interface HealthEnricher {

    /**
     * Snapshot of node-local health. Return `null` for any field to
     * omit it from the JSON reply (preserving wire compatibility).
     */
    fun snapshot(): HealthSnapshot

    /**
     * Bundle of enricher fields. Each property is independently
     * nullable so the call site can forward only what it has
     * measured.
     */
    data class HealthSnapshot(
        val capabilityTier: CapabilityTier? = null,
        val engineTag: String? = null,
        val loadedShards: List<String> = emptyList(),
        val metrics: MetricsSnapshot? = null,
    )

    companion object {
        /** No-op enricher; defaults preserve the original `/v1/health` shape. */
        val NONE: HealthEnricher = HealthEnricher { HealthSnapshot() }
    }
}
