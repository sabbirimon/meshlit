package com.meshlit.core.firewall

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-IP token-bucket rate limiter. Phase 3 default:
 *  - `/v1/infer`: 60 requests/minute/IP
 *  - other endpoints: 600 requests/minute/IP
 *
 * Buckets are stored in a [ConcurrentHashMap]; an idle eviction sweep
 * runs every 10 minutes (cheap — a single pass over the map). Buckets
 * that haven't been touched in 10 minutes are dropped to keep memory
 * bounded under scan-style attacks.
 *
 * The limiter is intentionally in-process: there's no IPC to a
 * separate daemon. With Phase 1's single-port NanoHTTPD server that
 * maps cleanly to a single source of truth.
 */
class RateLimiter(
    private val inferBudgetPerMin: Int = 60,
    private val otherBudgetPerMin: Int = 600,
    private val idleEvictionMs: Long = 10 * 60_000L,
) {

    private data class Bucket(
        var tokens: Double,
        var lastRefillMs: Long,
        var lastTouchMs: Long,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /** Try to consume one token for [endpointKey] from [remoteAddr]'s bucket. */
    fun tryAcquire(remoteAddr: String, endpointKey: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "$remoteAddr|$endpointKey"
        val budget = if (endpointKey == "infer") inferBudgetPerMin.toDouble() else otherBudgetPerMin.toDouble()
        val bucket = buckets.computeIfAbsent(key) {
            Bucket(tokens = budget, lastRefillMs = now, lastTouchMs = now)
        }
        synchronized(bucket) {
            // Refill based on elapsed time.
            val elapsedMs = (now - bucket.lastRefillMs).coerceAtLeast(0)
            val refill = (elapsedMs / 60_000.0) * budget
            bucket.tokens = (bucket.tokens + refill).coerceAtMost(budget)
            bucket.lastRefillMs = now
            bucket.lastTouchMs = now
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                return true
            }
            return false
        }
    }

    /**
     * Drop buckets that haven't been touched for [idleEvictionMs].
     * Cheap, called from a worker. Returns the number evicted.
     */
    fun evictIdle(now: Long = System.currentTimeMillis()): Int {
        var evicted = 0
        for ((key, bucket) in buckets.entries) {
            if (now - bucket.lastTouchMs > idleEvictionMs) {
                if (buckets.remove(key, bucket)) evicted++
            }
        }
        return evicted
    }

    fun size(): Int = buckets.size
}
