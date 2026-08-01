package com.meshlit.inference

import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.Forwarder
import com.meshlit.core.inference.net.InferDoneEvent
import com.meshlit.core.inference.net.InferErrorEvent
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.core.inference.net.InferTokenEvent
import com.meshlit.core.inference.net.RequestHints

/**
 * Opens a [RemoteInferenceClient] to a chosen peer, runs the SSE
 * stream, and pipes events back to the original caller's outgoing
 * channel. Used by [InferenceHttpServer] when [com.meshlit.core.inference.net.RouterDecision.where]
 * is FORWARD.
 *
 * Implementation note: the caller is the embedded Ktor server, which
 * is calling us from inside a `respondTextWriter` block. The
 * forwarded tokens / done / error events are pushed directly into
 * the outgoing stream, so the original client cannot tell whether
 * the response was served locally or forwarded.
 *
 * Failure handling:
 *  - Network errors → return `Result.failure(...)` so the server can
 *    emit an `event: error` and close the stream.
 *  - Non-OK status → ditto.
 */
class ForwardingProxy(
    private val factory: RemoteInferenceClientFactory,
) : Forwarder {

    private val log = logger("ForwardingProxy")

    override suspend fun forwardAndStream(
        peerBaseUrl: String,
        request: InferRequest,
        hints: RequestHints?,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ): Result<Unit> {
        val client = factory.build(peerBaseUrl)
        return try {
            when (val r = client.streamInfer(
                request = request,
                hints = hints,
                onToken = { ev -> onToken(ev) },
                onDone = { ev -> onDone(ev) },
                onError = { ev -> onError(ev) },
            )) {
                is com.meshlit.core.common.MeshlitResult.Success -> {
                    log.info("forward.ok", "peer responded", mapOf("peer" to peerBaseUrl))
                    Result.success(Unit)
                }
                is com.meshlit.core.common.MeshlitResult.Failure -> {
                    log.warn("forward.fail", "peer failed", mapOf("peer" to peerBaseUrl, "err" to r.error.tag))
                    // Push an error event so the caller's client
                    // knows the stream ended badly.
                    onError(InferErrorEvent(tag = r.error.tag, message = r.error.message ?: ""))
                    Result.failure(RuntimeException("peer failed: ${r.error.tag}"))
                }
            }
        } catch (t: Throwable) {
            log.warn("forward.exception", "forward threw", mapOf("peer" to peerBaseUrl, "err" to (t.message ?: "")))
            onError(InferErrorEvent(tag = "forward.exception", message = t.message ?: ""))
            Result.failure(t)
        }
    }
}