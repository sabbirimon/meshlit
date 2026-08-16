package com.meshlit.core.inference

import com.meshlit.core.inference.net.MicroBatch
import com.meshlit.core.inference.net.MicroBatchEntry
import com.meshlit.core.inference.net.MicroBatchReply
import com.meshlit.core.inference.net.MicroBatchReplyEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [BatchScheduler] — Phase 3 async token batching.
 *
 * We deliberately avoid `runTest` here: the
 * `kotlinx-coroutines-test:1.10.0` artifact is built against
 * `kotlin-stdlib:2.4.0`, but the project's `kotlin-stdlib:2.1.20`
 * produces a "Debug metadata version mismatch" at the dispatcher
 * level. Plain `runBlocking` with a real `Dispatchers.Default`
 * scope avoids that incompatibility — the supervisor runs in
 * wall-clock time and `tearDown` cancels the scope explicitly.
 *
 * Coverage:
 *  - Flushes when the queue reaches `batchSize`.
 *  - Flushes when `batchTimeoutMs` elapses with a partial batch.
 *  - Demux: a [MicroBatchReply] with the matching `batchId`
 *    resolves the per-entry `reply` deferred.
 *  - `close()` rejects every pending entry with a typed failure.
 *  - `enqueue` throws [BackpressureError] when the queue is full.
 *  - Constructor rejects out-of-range args.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchSchedulerTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun makeScheduler(
        batchSize: Int = 4,
        batchTimeoutMs: Long = 50L,
        maxQueueDepth: Int = 64,
    ): BatchScheduler = BatchScheduler(
        scope = scope,
        batchSize = batchSize,
        batchTimeoutMs = batchTimeoutMs,
        maxQueueDepth = maxQueueDepth,
    ).also { it.start() }

    @Test
    fun scheduler_flushes_when_queue_reaches_batchSize() = runBlocking {
        val scheduler = makeScheduler(batchSize = 3, batchTimeoutMs = 5_000L)
        val batchDeferred = async { scheduler.flushes.first() }
        // Let the subscription register before triggering.
        delay(20L)

        scheduler.enqueue("a", "h1", "k1", "v1", 0)
        scheduler.enqueue("b", "h2", "k2", "v2", 0)
        // The third enqueue triggers the count-based flush via
        // scope.launch. Give it time to run.
        scheduler.enqueue("c", "h3", "k3", "v3", 0)
        delay(50L)

        val batch = withTimeout(1_000L) { batchDeferred.await() }
        assertEquals(1L, batch.batchId)
        assertEquals(3, batch.entries.size)
    }

    @Test
    fun scheduler_flushes_partial_batch_on_timeout() = runBlocking {
        val scheduler = makeScheduler(batchSize = 8, batchTimeoutMs = 50L)
        val batchDeferred = async { scheduler.flushes.first() }

        scheduler.enqueue("a", "h1", "k1", "v1", 0)
        scheduler.enqueue("b", "h2", "k2", "v2", 0)

        // Wait past the 50ms timeout so the supervisor loop wakes
        // up and flushes.
        delay(100L)

        val batch = withTimeout(1_000L) { batchDeferred.await() }
        assertEquals(2, batch.entries.size)
        assertEquals("a", batch.entries[0].prompt)
        assertEquals("b", batch.entries[1].prompt)
    }

    @Test
    fun scheduler_demuxes_reply_to_correct_entries() = runBlocking {
        val scheduler = makeScheduler(batchSize = 2, batchTimeoutMs = 5_000L)
        val batchDeferred = async { scheduler.flushes.first() }
        delay(20L)

        val e1 = scheduler.enqueue("a", "h1", "k1", "v1", 0)
        val e2 = scheduler.enqueue("b", "h2", "k2", "v2", 0)
        delay(50L)

        val batch = withTimeout(1_000L) { batchDeferred.await() }
        assertEquals(1L, batch.batchId)

        val reply = MicroBatchReply(
            batchId = batch.batchId,
            entries = listOf(
                MicroBatchReplyEntry(
                    requestId = e1.requestId,
                    finishedToken = 101,
                    isFinished = false,
                    hiddenStateBase64 = "hs1",
                    kvCacheKeysBase64 = "ks1",
                    kvCacheValuesBase64 = "vs1",
                ),
                MicroBatchReplyEntry(
                    requestId = e2.requestId,
                    finishedToken = 102,
                    isFinished = false,
                    hiddenStateBase64 = "hs2",
                    kvCacheKeysBase64 = "ks2",
                    kvCacheValuesBase64 = "vs2",
                ),
            ),
            isLastReply = false,
        )
        scheduler.complete(reply)

        val r1 = scheduler.waitForReply(e1, 1_000L)
        val r2 = scheduler.waitForReply(e2, 1_000L)
        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(101, r1!!.finishedToken)
        assertEquals(102, r2!!.finishedToken)
    }

    @Test
    fun scheduler_drops_reply_for_unknown_batchId_silently() {
        val scheduler = makeScheduler()
        scheduler.complete(
            MicroBatchReply(
                batchId = 99L,
                entries = emptyList(),
                isLastReply = false,
            ),
        )
        // Sanity: the scheduler is still operational.
        assertEquals(0, scheduler.queueDepth.value)
    }

    @Test
    fun scheduler_close_rejects_pending_entries() = runBlocking {
        val scheduler = makeScheduler(batchSize = 4, batchTimeoutMs = 5_000L)
        val e1 = scheduler.enqueue("a", "h1", "k1", "v1", 0)
        val e2 = scheduler.enqueue("b", "h2", "k2", "v2", 0)

        scheduler.close()
        // Let rejectAll run before checking the deferreds.
        delay(50L)

        val r1 = scheduler.waitForReply(e1, 100L)
        val r2 = scheduler.waitForReply(e2, 100L)
        assertNull("e1 timed out instead of being rejected", r1)
        assertNull("e2 timed out instead of being rejected", r2)
    }

    @Test
    fun scheduler_throws_BackpressureError_when_queue_full() {
        // batchSize=16 with maxQueueDepth=16 means no flush
        // triggers on a 4-enqueue test (we'd need 16 entries).
        val scheduler = makeScheduler(
            batchSize = 16,
            batchTimeoutMs = 5_000L,
            maxQueueDepth = 16,
        )
        scheduler.enqueue("a", "h", "k", "v", 0)
        scheduler.enqueue("b", "h", "k", "v", 0)
        scheduler.enqueue("c", "h", "k", "v", 0)
        scheduler.enqueue("d", "h", "k", "v", 0)
        scheduler.enqueue("e", "h", "k", "v", 0)
        scheduler.enqueue("f", "h", "k", "v", 0)
        scheduler.enqueue("g", "h", "k", "v", 0)
        scheduler.enqueue("h", "h", "k", "v", 0)
        scheduler.enqueue("i", "h", "k", "v", 0)
        scheduler.enqueue("j", "h", "k", "v", 0)
        scheduler.enqueue("k", "h", "k", "v", 0)
        scheduler.enqueue("l", "h", "k", "v", 0)
        scheduler.enqueue("m", "h", "k", "v", 0)
        scheduler.enqueue("n", "h", "k", "v", 0)
        scheduler.enqueue("o", "h", "k", "v", 0)
        scheduler.enqueue("p", "h", "k", "v", 0)
        // Queue is now at capacity (16 entries). The 17th must throw.
        assertThrows(BatchScheduler.BackpressureError::class.java) {
            scheduler.enqueue("q", "h", "k", "v", 0)
        }
    }

    @Test
    fun scheduler_rejects_constructor_args_out_of_range() {
        // batchSize must be in [1, MAX_BATCH_SIZE].
        assertThrows(IllegalArgumentException::class.java) {
            BatchScheduler(scope, batchSize = 0, batchTimeoutMs = 50L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BatchScheduler(scope, batchSize = 100, batchTimeoutMs = 50L)
        }
        // batchTimeoutMs must be > 0.
        assertThrows(IllegalArgumentException::class.java) {
            BatchScheduler(scope, batchSize = 4, batchTimeoutMs = 0L)
        }
        // maxQueueDepth must be ≥ batchSize.
        assertThrows(IllegalArgumentException::class.java) {
            BatchScheduler(scope, batchSize = 8, maxQueueDepth = 4)
        }
    }

    @Test
    fun scheduler_queueDepth_reflects_in_flight_count() = runBlocking {
        val scheduler = makeScheduler(batchSize = 16, batchTimeoutMs = 5_000L)
        scheduler.enqueue("a", "h", "k", "v", 0)
        scheduler.enqueue("b", "h", "k", "v", 0)
        delay(20L)
        assertEquals(2, scheduler.queueDepth.value)

        scheduler.close()
        delay(50L)
        assertEquals(0, scheduler.queueDepth.value)
    }

    @Test
    fun scheduler_start_is_idempotent() {
        val scheduler = makeScheduler()
        // A second start() should be a no-op (no crash, no double
        // supervisor).
        scheduler.start()
        val entry = scheduler.enqueue("a", "h", "k", "v", 0)
        assertNotNull(entry)
    }

    @Test
    fun scheduler_emits_isLastBatch_when_final_entry_is_finished() = runBlocking {
        val scheduler = makeScheduler(batchSize = 2, batchTimeoutMs = 5_000L)
        val batchDeferred = async { scheduler.flushes.first() }
        delay(20L)

        scheduler.enqueue("a", "h", "k", "v", 0, isFinished = true, finishedToken = 99)
        scheduler.enqueue("b", "h", "k", "v", 0)
        delay(50L)

        val batch = withTimeout(1_000L) { batchDeferred.await() }
        assertTrue("isLastBatch should be true", batch.isLastBatch)
    }

    @Test
    fun scheduler_promotes_ordinal_requestIds_across_batches() = runBlocking {
        val scheduler = makeScheduler(batchSize = 2, batchTimeoutMs = 5_000L)
        val batch1Deferred = async { scheduler.flushes.first() }
        delay(20L)

        val e1 = scheduler.enqueue("a", "h", "k", "v", 0)
        val e2 = scheduler.enqueue("b", "h", "k", "v", 0)
        delay(50L)

        val batch1 = withTimeout(1_000L) { batch1Deferred.await() }
        assertEquals(1L, batch1.batchId)
        assertEquals(2, batch1.entries.size)

        // The queue is empty now. New enqueue should resume
        // requestIds from 3 (1 and 2 were used).
        val e3 = scheduler.enqueue("c", "h", "k", "v", 0)
        assertEquals(3, e3.requestId)
    }
}