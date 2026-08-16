package com.meshlit.inference

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LocalLoadTracker]. The tracker is a thin
 * MutableStateFlow wrapper around four counters, so the tests
 * focus on:
 *
 *  - start() increments activeInferences
 *  - finish() decrements + increments successCount
 *  - fail(reason) decrements + increments failureCount
 *  - counters clamp at zero (a missed finish() cannot lead to a
 *    negative counter)
 *  - enqueue() / dequeue() mirror for queueDepth
 */
class LocalLoadTrackerTest {

    @Test
    fun `start increments active inferences`() {
        val t = LocalLoadTracker()
        t.start()
        t.start()
        assertEquals(2, t.state.value.activeInferences)
    }

    @Test
    fun `finish decrements and bumps successCount`() {
        val t = LocalLoadTracker()
        t.start()
        t.finish()
        assertEquals(0, t.state.value.activeInferences)
        assertEquals(1L, t.state.value.successCount)
    }

    @Test
    fun `fail decrements and bumps failureCount`() {
        val t = LocalLoadTracker()
        t.start()
        t.fail("some-reason")
        assertEquals(0, t.state.value.activeInferences)
        assertEquals(1L, t.state.value.failureCount)
    }

    @Test
    fun `counters clamp at zero`() {
        val t = LocalLoadTracker()
        t.finish()
        t.fail("reason")
        t.dequeue()
        // No start/enqueue, but the counters should clamp at 0
        // instead of going negative.
        assertEquals(0, t.state.value.activeInferences)
        assertEquals(0, t.state.value.queueDepth)
    }

    @Test
    fun `enqueue and dequeue mirror for queueDepth`() {
        val t = LocalLoadTracker()
        t.enqueue()
        t.enqueue()
        assertEquals(2, t.state.value.queueDepth)
        t.dequeue()
        assertEquals(1, t.state.value.queueDepth)
        t.dequeue()
        assertEquals(0, t.state.value.queueDepth)
    }

    @Test
    fun `mixed sequence preserves counters`() {
        val t = LocalLoadTracker()
        t.start()
        t.start()
        t.enqueue()
        assertEquals(2, t.state.value.activeInferences)
        assertEquals(1, t.state.value.queueDepth)
        t.finish()
        t.fail("nope")
        t.dequeue()
        assertEquals(0, t.state.value.activeInferences)
        assertEquals(0, t.state.value.queueDepth)
        assertEquals(1L, t.state.value.successCount)
        assertEquals(1L, t.state.value.failureCount)
    }
}
