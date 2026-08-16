package com.meshlit.core.inference.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Phase 3 — async token batching (Petals-style). A [MicroBatch]
 * envelopes N concurrent [ActivationPacket] entries that should be
 * processed atomically by the next pipeline stage. The orchestrator
 * builds a [MicroBatch] when either the in-flight queue hits the
 * caller's `batchSize` threshold OR a `batchTimeoutMs` deadline
 * elapses — whichever comes first.
 *
 * Wire shape (v1):
 *  - `batchId` is a monotonic counter. All entries in this batch
 *    share the same `batchId`. Reusing a `batchId` across batches
 *    is illegal and will be rejected by the receiving stage.
 *  - `entries` is the list of [MicroBatchEntry]. The order is
 *    preserved through the pipeline so callers can address entries
 *    by their position. Per-request ordering is preserved;
 *    inter-request ordering is best-effort.
 *  - `isLastBatch` is `true` when the orchestrator is shutting down
 *    the pipeline and this is the final flush. The LastStage uses
 *    this to emit a `finish_reason = "batch_drained"` frame so the
 *    caller knows no more tokens are coming for any request in the
 *    batch.
 *
 * The envelope is a **separate wire type** from
 * [ActivationPacket] — they share the same transport
 * ([RawTcpActivationChannel]) but use different serializer routes.
 * A receiver discriminates by reading the `kind` discriminator on
 * the first JSON field of each frame.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class MicroBatch(
    /** Monotonic batch counter. All entries in this batch share the
     *  same id; ids are unique per pipeline. */
    val batchId: Long,
    /** Per-request entries. Order matters; addressable by index. */
    val entries: List<MicroBatchEntry>,
    /** `true` when this is the final flush before the orchestrator
     *  shuts down. */
    val isLastBatch: Boolean,
    /** Wall-clock deadline this batch was flushed at. Used by
     *  observability to compute "time from enqueue to flush". */
    val flushedAtMs: Long = 0L,
)

/**
 * One request's worth of state inside a [MicroBatch]. The entry
 * carries the hidden state produced by the previous stage + the KV
 * cache slice deltas the next stage needs to splice in.
 *
 * Wire shape:
 *  - `hiddenStateBase64` / `kvCacheKeysBase64` /
 *    `kvCacheValuesBase64` use the same little-endian base64
 *    encoding as [ActivationPacket].
 *  - `prompt` is only populated on the first batch for a request
 *    (subsequent batches drop it). The receiver reuses the prompt
 *    it saw on the first batch entry for tokenisation continuity.
 *  - `position` is the KV-cache position this entry should be
 *    spliced at. Used by the engine to slot the inbound hidden
 *    state into the correct row of the per-request KV buffer.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class MicroBatchEntry(
    /** Stable per-request id within the batch. Same `requestId`
     *  across batches for the same logical request. */
    val requestId: Int,
    /** The prompt for this request. Empty string on subsequent
     *  batches — receivers reuse the first batch's prompt. */
    val prompt: String = "",
    /** KV-cache position to splice the inbound hidden state into. */
    val position: Int,
    /** Little-endian base64 of the inbound hidden state. */
    val hiddenStateBase64: String,
    /** Little-endian base64 of the KV cache keys slice. */
    val kvCacheKeysBase64: String,
    /** Little-endian base64 of the KV cache values slice. */
    val kvCacheValuesBase64: String,
    /** When `true`, the request is finished and the LastStage
     *  should emit a `finish_reason` for it on this entry's
     *  outbound packet. */
    val isFinished: Boolean = false,
    /** The finished token id (set by the LastStage when
     *  `isFinished = true`). Default `-1` means "no token". */
    val finishedToken: Int = -1,
)

/**
 * Phase 3 — the LastStage's per-request reply to a [MicroBatch].
 * One [MicroBatchReply] is emitted per batch (not per entry) but
 * carries one [MicroBatchReplyEntry] per entry so the orchestrator
 * can demux and route each reply to its request id.
 *
 * Wire shape:
 *  - `batchId` echoes the batch the reply corresponds to.
 *  - `entries` is ordered to match the [MicroBatch.entries] it
 *    responds to. The orchestrator correlates by index.
 *  - `isLastReply` is `true` when the LastStage is shutting down
 *    and no more replies will arrive for this pipeline.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class MicroBatchReply(
    val batchId: Long,
    val entries: List<MicroBatchReplyEntry>,
    val isLastReply: Boolean,
    val flushedAtMs: Long = 0L,
)

/**
 * One request's worth of reply inside a [MicroBatchReply].
 * The LastStage samples a token per entry and attaches it here.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class MicroBatchReplyEntry(
    val requestId: Int,
    /** Sampled token id. `-1` means "no token" (request not yet
     *  finished at this position). */
    val finishedToken: Int,
    /** When `true`, this is the final reply for this request. */
    val isFinished: Boolean,
    /** Little-endian base64 of the outbound hidden state the next
     *  stage (or the orchestrator) needs to consume. */
    val hiddenStateBase64: String,
    /** Little-endian base64 of the KV cache keys slice produced by
     *  this stage. */
    val kvCacheKeysBase64: String,
    /** Little-endian base64 of the KV cache values slice produced
     *  by this stage. */
    val kvCacheValuesBase64: String,
)

/**
 * The discriminator a [RawTcpActivationChannel] uses to route a
 * frame to either [ActivationPacket], [MicroBatch], or another
 * envelope type. Phase 2 sent only [ActivationPacket] frames;
 * Phase 3 introduced [MicroBatch] for batched traffic. A receiver
 * peeks at the first JSON field of each frame and routes
 * accordingly.
 *
 * Backward compat: a Phase 2 receiver that doesn't know about
 * [MICRO_BATCH_KIND] will try to decode a MicroBatch frame as an
 * ActivationPacket and fail at the `stageIndex` field — but the
 * channel catches that, emits a `kind_mismatch` error, and closes.
 * Phase 2 callers send only [ACTIVATION_PACKET_KIND] frames so the
 * mismatch path is never triggered on a healthy pair.
 */
object WireFrameKind {
    const val ACTIVATION_PACKET_KIND: String = "activation_packet"
    const val MICRO_BATCH_KIND: String = "micro_batch"
    const val PIPELINE_START_KIND: String = "pipeline_start"
    const val PIPELINE_ACK_KIND: String = "pipeline_ack"
    const val MICRO_BATCH_REPLY_KIND: String = "micro_batch_reply"
}
