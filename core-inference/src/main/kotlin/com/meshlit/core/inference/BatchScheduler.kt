package com.meshlit.core.inference

import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.MicroBatch
import com.meshlit.core.inference.net.MicroBatchEntry
import com.meshlit.core.inference.net.MicroBatchReply
import com.meshlit.core.inference.net.MicroBatchReplyEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 3 — async token batching (Petals-style) scheduler.
 *
 * Owns an in-flight queue of [BatchEntry] instances (one per
 * logical request). Each `enqueue(...)` adds a request to the
 * queue; the scheduler either:
 *
 *  - flushes immediately when the queue reaches [batchSize], OR
 *  - flushes once [batchTimeoutMs] has elapsed since the first
 *    un-flushed entry was enqueued (whichever comes first).
 *
 * The flush collects the queued entries into a single [MicroBatch]
 * envelope and emits it via [flushes]. The receiver replies via
 * [complete] with a [MicroBatchReply] that the scheduler demuxes
 * back to the originating request ids via [replies].
 *
 * Concurrency model:
 *  - `enqueue(...)` is non-blocking; the entry is parked in a
 *    priority queue keyed by enqueue time.
 *  - A single supervisor coroutine watches the queue and emits
 *    flushes. Multiple `enqueue` calls between flushes are
 *    batched.
 *  - `complete(batchId, reply)` resolves the inner
 *    [CompletableDeferred] on each entry's `reply` field so the
 *    caller awaiting `replies` continues with the right token.
 *
 * Backward compat: a Phase 2 caller that doesn't enable batching
 * simply doesn't construct a `BatchScheduler`. The
 * [PipelineCoordinator.run] path remains unchanged.
 */
class BatchScheduler(
    private val scope: CoroutineScope,
    val batchSize: Int = PipelineStartPacketBatchDefaults.DEFAULT_BATCH_SIZE,
    val batchTimeoutMs: Long = PipelineStartPacketBatchDefaults.DEFAULT_BATCH_TIMEOUT_MS,
    private val maxQueueDepth: Int = 256,
) {
    private val log = logger("BatchScheduler")

    init {
        require(batchSize in 1..PipelineStartPacketBatchDefaults.MAX_BATCH_SIZE) {
            "batchSize out of range: $batchSize (max ${PipelineStartPacketBatchDefaults.MAX_BATCH_SIZE})"
        }
        require(batchTimeoutMs > 0L) { "batchTimeoutMs must be positive" }
        require(maxQueueDepth >= batchSize) { "maxQueueDepth must be ≥ batchSize" }
    }

    /**
     * The in-flight queue. We use a `PriorityBlockingQueue` keyed
     * by `enqueuedAtMs` so the scheduler always flushes the oldest
     * entries first. Bounded by [maxQueueDepth] — additional
     * `enqueue` calls drop with a typed [BackpressureError] so the
     * caller can retry on a different stage.
     */
    private val queue = PriorityBlockingQueue<BatchEntry>(
        maxQueueDepth + 1,
        compareBy { it.enqueuedAtMs },
    )

    private val mutex = Mutex()
    private val nextBatchId = AtomicLong(1L)
    private val nextRequestId = AtomicLong(0L)

    /** Flushes emitted to the downstream stage. The orchestrator
     *  collects this flow and dispatches each flush through the
     *  pipeline. */
    private val _flushes = MutableSharedFlow<MicroBatch>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val flushes: SharedFlow<MicroBatch> = _flushes.asSharedFlow()

    /** Per-request replies. The caller collects the entry that
     *  matches its batchId and demuxes by requestId. */
    private val _replies = MutableSharedFlow<MicroBatchReply>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val replies: SharedFlow<MicroBatchReply> = _replies.asSharedFlow()

    /** Observable queue depth — exposed for `/v1/health` and the
     *  scheduler feedback loop. */
    private val _queueDepth = MutableStateFlow(0)
    val queueDepth: StateFlow<Int> = _queueDepth.asStateFlow()

    @Volatile
    private var supervisor: Job? = null

    /** Map `batchId → entries` so a [complete] call can resolve
     *  each recipient's deferred. */
    private val pendingBatches = java.util.concurrent.ConcurrentHashMap<Long, List<BatchEntry>>()

    /**
     * Start the supervisor coroutine that watches the queue and
     * emits flushes. Idempotent — a second `start` is a no-op.
     */
    fun start() {
        if (supervisor != null) return
        supervisor = scope.launch {
            // The scheduler loop wakes whenever the queue might
            // have changed. We use a deterministic sleep bounded
            // by the next deadline; no busy-wait.
            while (true) {
                val head = queue.peek()
                if (head == null) {
                    // Queue empty — sleep a small interval so
                    // we wake when the next enqueue lands.
                    // Polling is cheap because the queue's
                    // head is lock-free.
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                val deadline = head.enqueuedAtMs + batchTimeoutMs
                val now = System.currentTimeMillis()
                val wait = (deadline - now).coerceAtLeast(0L)
                if (wait > 0) {
                    delay(wait)
                }
                flushOnce(fillToBatchSize = false)
            }
        }
    }

    /**
     * Enqueue a request for the next batch. The returned
     * [BatchEntry] carries a `reply` deferred that the caller
     * awaits. Throws [BackpressureError] when the queue is full.
     */
    fun enqueue(
        prompt: String,
        hiddenStateBase64: String,
        kvCacheKeysBase64: String,
        kvCacheValuesBase64: String,
        position: Int,
        isFinished: Boolean = false,
        finishedToken: Int = -1,
    ): BatchEntry {
        val entry = BatchEntry(
            requestId = nextRequestId.incrementAndGet().toInt(),
            prompt = prompt,
            position = position,
            hiddenStateBase64 = hiddenStateBase64,
            kvCacheKeysBase64 = kvCacheKeysBase64,
            kvCacheValuesBase64 = kvCacheValuesBase64,
            isFinished = isFinished,
            finishedToken = finishedToken,
            enqueuedAtMs = System.currentTimeMillis(),
            reply = CompletableDeferred(),
        )
        // Bounded check: PriorityBlockingQueue is unbounded, so
        // we must reject the entry manually when the queue is at
        // capacity. We use a `synchronized` block around the
        // size check + offer so a concurrent enqueue can't race
        // past the limit.
        synchronized(queue) {
            if (queue.size >= maxQueueDepth) {
                throw BackpressureError(
                    "BatchScheduler queue is full ($maxQueueDepth); " +
                        "retry on a different stage",
                )
            }
            queue.offer(entry)
        }
        _queueDepth.value = queue.size
        // If the queue hit batchSize, flush immediately.
        if (queue.size >= batchSize) {
            scope.launch { flushOnce(fillToBatchSize = true) }
        }
        return entry
    }

    /**
     * Drain up to `batchSize` entries from the queue and emit
     * them as a [MicroBatch]. Called concurrently from the
     * supervisor loop and the count-trigger path. Idempotent —
     * a concurrent flush sees an empty queue and returns early.
     */
    private suspend fun flushOnce(fillToBatchSize: Boolean) {
        val drained = mutableListOf<BatchEntry>()
        mutex.withLock {
            if (queue.isEmpty()) return
            val target = if (fillToBatchSize) {
                // Drain exactly batchSize entries; the supervisor
                // loop handles the partial-batch case.
                batchSize
            } else {
                minOf(batchSize, queue.size)
            }
            repeat(target) {
                val head = queue.poll() ?: return@repeat
                drained.add(head)
            }
            _queueDepth.value = queue.size
        }
        if (drained.isEmpty()) return
        val batchId = nextBatchId.getAndIncrement()
        val isLast = drained.any { it.isFinished } && queue.isEmpty()
        val envelope = MicroBatch(
            batchId = batchId,
            entries = drained.map { it.toMicroBatchEntry() },
            isLastBatch = isLast,
            flushedAtMs = System.currentTimeMillis(),
        )
        log.info("BatchScheduler.flushOnce", "batch=$batchId size=${drained.size} isLast=$isLast")
        // tryEmit because the count-triggered flushOnce (called
        // from enqueue) runs synchronously inside the caller's
        // coroutine. We don't want a stuck SharedFlow subscriber
        // to deadlock the producer. Subscribers are expected to
        // pick up the next batch within the next enqueue window.
        val emitted = _flushes.tryEmit(envelope)
        if (!emitted) {
            log.warn("BatchScheduler.flushOnce", "flushes buffer full; dropping batch=$batchId")
        }
        pendingBatches[batchId] = drained
    }

    /** Receive a [MicroBatchReply] from the downstream stage and
     *  resolve the per-entry `reply` deferred on every entry in
     *  the corresponding batch. */
    fun complete(reply: MicroBatchReply) {
        val entries = pendingBatches.remove(reply.batchId) ?: return
        log.info("BatchScheduler.complete", "batch=${reply.batchId} entries=${reply.entries.size}")
        // Demux by index. The reply's entries are ordered to
        // match the batch the receiver saw.
        reply.entries.forEachIndexed { idx, replyEntry ->
            val entry = entries.getOrNull(idx) ?: return@forEachIndexed
            entry.reply.complete(replyEntry)
        }
        _replies.tryEmit(reply)
    }

    /**
     * Await the reply for a specific request. The caller passes
     * the [BatchEntry] returned by [enqueue] and waits for the
     * matching [MicroBatchReplyEntry]. Returns null on timeout
     * OR when the reply deferred was completed exceptionally
     * (e.g. [close] rejecting all pending entries).
     */
    suspend fun waitForReply(entry: BatchEntry, timeoutMs: Long): MicroBatchReplyEntry? {
        return try {
            withTimeoutOrNull(timeoutMs) { entry.reply.await() }
        } catch (e: Throwable) {
            // The reply deferred completed exceptionally — surface
            // as a null reply rather than propagating the failure.
            // The caller can still inspect `entry.reply` to learn
            // why the rejection happened.
            null
        }
    }

    /**
     * Reject every pending entry with a typed failure. Called
     * from the orchestrator's `finally {}` block so an
     * unawaited request doesn't leak its reply deferred.
     */
    suspend fun rejectAll(reason: String) {
        val drained = mutableListOf<BatchEntry>()
        mutex.withLock {
            while (true) {
                val head = queue.poll() ?: break
                drained.add(head)
            }
            _queueDepth.value = queue.size
        }
        drained.forEach { it.reply.completeExceptionally(IllegalStateException(reason)) }
    }

    fun close() {
        supervisor?.cancel()
        supervisor = null
        // `rejectAll` is suspend; we spawn it on the scope so the
        // close path is non-blocking. If the scope is already
        // cancelled, the launch is a no-op and the pending entries
        // carry unresolved deferreds — which is fine because the
        // caller is the only one holding them.
        try {
            scope.launch { rejectAll("BatchScheduler closed") }
        } catch (_: Throwable) {
            // Scope already cancelled — pending deferreds are
            // abandoned. Callers must not enqueue after close.
        }
    }

    /**
     * One in-flight request. The `reply` field is awaited by the
     * caller; the scheduler completes it on receipt of the
     * matching [MicroBatchReplyEntry]. `enqueuedAtMs` is the
     * wall-clock time at which the entry joined the queue.
     */
    data class BatchEntry(
        val requestId: Int,
        val prompt: String,
        val position: Int,
        val hiddenStateBase64: String,
        val kvCacheKeysBase64: String,
        val kvCacheValuesBase64: String,
        val isFinished: Boolean,
        val finishedToken: Int,
        val enqueuedAtMs: Long,
        /** Resolved by the scheduler when the matching
         *  [MicroBatchReplyEntry] arrives. */
        val reply: CompletableDeferred<MicroBatchReplyEntry>,
    ) {
        /** Convert to the wire form. The orchestrator's [run]
         *  path uses `toMicroBatchEntry` to populate the
         *  [MicroBatch] envelope. */
        fun toMicroBatchEntry(): MicroBatchEntry = MicroBatchEntry(
            requestId = requestId,
            prompt = prompt,
            position = position,
            hiddenStateBase64 = hiddenStateBase64,
            kvCacheKeysBase64 = kvCacheKeysBase64,
            kvCacheValuesBase64 = kvCacheValuesBase64,
            isFinished = isFinished,
            finishedToken = finishedToken,
        )
    }

    /** Thrown when the queue is full and a new entry is rejected. */
    class BackpressureError(message: String) : RuntimeException(message)

    companion object {
        /** When the queue is empty, the supervisor sleeps this
         *  long before re-checking. Keeps the idle CPU cost at
         *  near-zero. */
        private const val POLL_INTERVAL_MS: Long = 5L

        /** Default values used by [com.meshlit.core.inference.net.PipelineStartPacket]
         *  when the client doesn't set batching knobs. Re-exported
         *  here so the scheduler and the wire DTO can share the
         *  same constants. */
        object PipelineStartPacketBatchDefaults {
            const val DEFAULT_BATCH_SIZE: Int = 1
            const val DEFAULT_BATCH_TIMEOUT_MS: Long = 50L
            const val MAX_BATCH_SIZE: Int = 16
        }
    }
}
