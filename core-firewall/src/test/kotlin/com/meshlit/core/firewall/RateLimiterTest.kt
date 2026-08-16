package com.meshlit.core.firewall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun first_acquires_under_budget_succeed() {
        val rl = RateLimiter(inferBudgetPerMin = 5)
        repeat(5) { assertTrue("acquire $it", rl.tryAcquire("1.2.3.4", "infer")) }
    }

    @Test
    fun sixth_infer_request_is_refused() {
        val rl = RateLimiter(inferBudgetPerMin = 5)
        repeat(5) { rl.tryAcquire("1.2.3.4", "infer") }
        assertFalse(rl.tryAcquire("1.2.3.4", "infer"))
    }

    @Test
    fun separate_ips_have_independent_buckets() {
        val rl = RateLimiter(inferBudgetPerMin = 2)
        assertTrue(rl.tryAcquire("1.1.1.1", "infer"))
        assertTrue(rl.tryAcquire("1.1.1.1", "infer"))
        assertFalse(rl.tryAcquire("1.1.1.1", "infer"))
        // Different IP — independent bucket.
        assertTrue(rl.tryAcquire("2.2.2.2", "infer"))
        assertTrue(rl.tryAcquire("2.2.2.2", "infer"))
    }

    @Test
    fun separate_endpoints_have_independent_buckets() {
        val rl = RateLimiter(inferBudgetPerMin = 1, otherBudgetPerMin = 1)
        assertTrue(rl.tryAcquire("9.9.9.9", "infer"))
        assertFalse(rl.tryAcquire("9.9.9.9", "infer"))
        // health endpoint has its own bucket
        assertTrue(rl.tryAcquire("9.9.9.9", "health"))
    }

    @Test
    fun eviction_drops_idle_buckets() {
        val rl = RateLimiter(idleEvictionMs = 100L)
        rl.tryAcquire("1.1.1.1", "infer")
        assertEquals(1, rl.size())
        Thread.sleep(150)
        val evicted = rl.evictIdle()
        assertEquals(1, evicted)
        assertEquals(0, rl.size())
    }
}
