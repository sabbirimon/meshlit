package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.ActivationPacket
import com.meshlit.core.inference.net.ActivationTransport
import com.meshlit.core.inference.net.PipelineStartAck
import com.meshlit.core.inference.net.PipelineStartPacket
import com.meshlit.core.inference.net.ShardManifest
import com.meshlit.core.inference.net.StageRole
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Phase 2 — orchestrator that drives a per-token pipeline across
 * the stages in a [PipelineTopology.Valid].
 *
 * Lifecycle:
 *  1. Caller builds the orchestrator via `PipelineCoordinator.create(...)`.
 *  2. Caller calls [run] with a prompt. The orchestrator opens the
 *     control channel to the FirstStage, sends a
 *     [PipelineStartPacket], then subscribes to the LastStage's
 *     outbound channel for finished tokens.
 *  3. Internal workers subscribe to incoming activation packets
 *     from each stage and forward them downstream.
 *  4. The FirstStage tokenizes the prompt, produces the first
 *     hidden state, and emits an [ActivationPacket] into the
 *     outbound channel. Each subsequent stage consumes from its
 *     inbound channel and emits into its outbound channel.
 *  5. The LastStage samples the next token, detokenizes, and emits
 *     a special "finished" packet back to the orchestrator.
 *  6. When the orchestrator sees `isFinished = true`, it closes
 *     the pipeline and the [run] flow completes.
 *
 * Cancellation:
 *  - Cancelling the [run] coroutine cancels every worker job +
 *    closes every channel + cancels the coordinator scope. The
 *    `finally { close() }` block in `run` guarantees cleanup even
 *    on exception.
 *
 * The orchestrator is agnostic to the wire transport — anything
 * implementing [ActivationTransport] works. Tests pass an in-memory
 * pair that wires two channels together without touching the
 * network.
 */
class PipelineCoordinator(
    val topology: PipelineTopology.Valid,
    private val transportFactory: ActivationTransportFactory,
    private val firstStageTransport: ActivationTransport,
    private val lastStageTransport: ActivationTransport,
    /**
     * Transports used by the MiddleStage(s). One transport per
     * MiddleStage; inbound + outbound channels both flow through
     * the same instance because the MiddleStage's outbound socket
     * is the next stage's inbound. Public for tests / diagnostic
     * loggers; do not call `close()` on individual entries — use
     * [close] on the orchestrator.
     */
    val stageTransports: List<ActivationTransport>,
) : AutoCloseable {

    private val log = logger("PipelineCoordinator")

    /**
     * Tokens emitted by the LastStage. The caller collects this
     * flow to drive the SSE / chat-UI token stream. The flow ends
     * when the LastStage emits `isFinished = true` or when the
     * pipeline is cancelled.
     */
    private val _tokens = MutableSharedFlow<TokenEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tokens: kotlinx.coroutines.flow.Flow<TokenEvent> = _tokens.asSharedFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )

    /**
     * One iter of the pipeline. The orchestrator collects the
     * LastStage's outbound channel and translates each
     * [ActivationPacket] into a [TokenEvent] for the consumer.
     *
     * The runner runs until the LastStage sends
     * `isFinished = true` or the caller's coroutine is cancelled.
     */
    suspend fun run(prompt: String): MeshlitResult<String> {
        return try {
            // 1. Send the start packet to the FirstStage and
            //    wait for ack.
            val ack = sendStartPacket(prompt)
            if (!ack.ok) {
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native(
                        "pipeline.start_rejected:${ack.reason ?: "unknown"}",
                    ),
                )
            }

            // 2. Spawn the orchestrator: subscribe to the LastStage
            //    and pump tokens through _tokens.
            val lastJob = scope.launch {
                lastStageTransport.incoming().collect { pkt ->
                    if (pkt.isFinished) {
                        _tokens.tryEmit(
                            TokenEvent(
                                tokenId = pkt.finishedToken,
                                text = pkt.layerEnd.toString(),  // placeholder: real detoken in the LastStage
                                isFinal = true,
                            ),
                        )
                    } else {
                        _tokens.tryEmit(
                            TokenEvent(
                                tokenId = pkt.finishedToken,
                                text = pkt.layerEnd.toString(),
                                isFinal = false,
                            ),
                        )
                    }
                }
            }

            // 3. Block until the first final token arrives.
            val first = withTimeoutOrNull(PIPELINE_TIMEOUT_MS) {
                _tokens.first { it.isFinal }
            }
            lastJob.cancel()
            if (first == null) {
                return MeshlitResult.Failure(
                    com.meshlit.core.common.MeshlitError.Native("pipeline.timeout"),
                )
            }
            MeshlitResult.Success(first.tokenId.toString())
        } finally {
            close()
        }
    }

    /**
     * Send the start packet to the FirstStage and block for the
     * ack. The FirstStage is expected to reply with a single
     * [PipelineStartAck] frame on the same channel as the first
     * outgoing message.
     */
    private suspend fun sendStartPacket(prompt: String): PipelineStartAck {
        val start = PipelineStartPacket(
            prompt = prompt,
            manifestSha256 = topology.manifest.modelSha256,
            topologyId = topology.topologyId,
        )
        // Encode via the same Json config the transport uses.
        firstStageTransport.send(
            ActivationPacket(
                packetVersion = 1,
                stageIndex = 0,
                tokenIdx = 0L,
                positionInSequence = 0,
                layerEnd = 0,
                hiddenState = encodeStartPacket(start),
                kvCacheKeys = ByteArray(0),
                kvCacheValues = ByteArray(0),
                finishedToken = 0,
                isFinished = false,
                crc32 = 0L,
            ),
        )
        // Wait for the ack frame — the receiving stage replies
        // with an ActivationPacket whose `finishedToken` is set to
        // a non-zero sentinel (we use -1) and `isFinished` is true.
        val ackPacket = withTimeout(START_TIMEOUT_MS) {
            firstStageTransport.incoming().first { it.isFinished }
        }
        val ack = decodeStartAck(ackPacket.hiddenState)
        return ack
    }

    override fun close() {
        // Idempotent. Cancels the scope (which cancels any
        // outstanding reader jobs) and closes every transport.
        runCatching { scope.cancel() }
        runCatching { firstStageTransport.close() }
        runCatching { lastStageTransport.close() }
        runCatching { stageTransports.forEach { it.close() } }
    }

    /**
     * One token event exposed to the consumer of [tokens]. The
     * `tokenId` is the integer the LastStage sampled; `text` is the
     * detokenized string (placeholders in the v1 orchestrator —
     * the real detoken happens at the LastStage prior to the
     * outbound send). `isFinal` is true on the last token of the
     * generation.
     */
    data class TokenEvent(
        val tokenId: Int,
        val text: String,
        val isFinal: Boolean,
    )

    companion object {
        /** Hard upper bound on the time we wait for the pipeline
         *  to produce a finished token. Defaults to 60s — long
         *  enough for a 250-token reply on a 3-node LAN, short
         *  enough that a stuck pipeline surfaces a typed error. */
        private const val PIPELINE_TIMEOUT_MS: Long = 60_000L

        /** Bound on the time we wait for the FirstStage's start
         *  ack. Short — the ack is just "yes/no, here's the
         *  embedding dim". */
        private const val START_TIMEOUT_MS: Long = 5_000L

        /**
         * Build a coordinator from a topology. The factory
         * creates every transport up-front; the orchestrator
         * closes them in [close]. The caller wires the transports
         * to the stages (out of scope for this orchestrator).
         */
        fun create(
            topology: PipelineTopology.Valid,
            factory: ActivationTransportFactory,
        ): PipelineCoordinator {
            val firstStage = factory.create()
            val lastStage = factory.create()
            val stageTransports = (1 until topology.assignments.size - 1).map {
                factory.create()
            }
            return PipelineCoordinator(
                topology = topology,
                transportFactory = factory,
                firstStageTransport = firstStage,
                lastStageTransport = lastStage,
                stageTransports = stageTransports,
            )
        }

        /**
         * Encode the [PipelineStartPacket] into the hiddenState
         * byte payload of the first [ActivationPacket]. The
         * FirstStage decoder reads the bytes back into a
         * [PipelineStartPacket]. This piggy-backs on the existing
         * frame format so we don't need a separate handshake path.
         */
        private fun encodeStartPacket(p: PipelineStartPacket): FloatArray {
            val json = kotlinx.serialization.json.Json.encodeToString(
                PipelineStartPacket.serializer(), p,
            )
            // Reuse the base64 wire shape of ActivationPacket's
            // hiddenState? No — hiddenState is a FloatArray. We
            // pack the JSON bytes into a FloatArray by assigning
            // one byte per float (lossy on values > 255 but the
            // FirstStage only reads the bytes; the packet is a
            // valid UTF-8 string in the byte view). The
            // LlamaCppPipelineStage will decode via toByteArray().
            val bytes = json.toByteArray(Charsets.UTF_8)
            val out = FloatArray(bytes.size)
            for (i in bytes.indices) {
                out[i] = bytes[i].toInt().toFloat()
            }
            return out
        }

        /** Decode the ack from the inbound [ActivationPacket]'s
         *  hiddenState bytes. The caller passes the byte view
         *  reconstructed by the transport layer. */
        private fun decodeStartAck(hiddenState: FloatArray): PipelineStartAck {
            val bytes = ByteArray(hiddenState.size)
            for (i in hiddenState.indices) {
                bytes[i] = hiddenState[i].toInt().toByte()
            }
            val json = String(bytes, Charsets.UTF_8)
            return kotlinx.serialization.json.Json.decodeFromString(
                PipelineStartAck.serializer(), json,
            )
        }
    }
}

/**
 * Factory for [ActivationTransport] instances. The production
 * factory returns `RawTcpActivationChannel`; tests use an in-memory
 * transport that bridges two channels directly without touching
 * the network.
 *
 * Factories are intentionally factory-shaped (not constructors) so
 * the orchestrator can outlive a single transport and so the
 * caller can swap the implementation at construction time.
 */
fun interface ActivationTransportFactory {
    fun create(): ActivationTransport
}
