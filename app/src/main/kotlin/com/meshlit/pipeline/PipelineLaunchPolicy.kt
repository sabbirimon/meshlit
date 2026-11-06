package com.meshlit.pipeline

import com.meshlit.core.inference.pipeline.NodeOffer
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class PipelineOfferScan(
    val verified: List<Pair<PipelinePeer, NodeOffer>>,
    val unreachable: List<PipelinePeer>,
)

/** Probe only the saved group. Certificate, token and protocol failures always abort. */
internal suspend fun scanPipelineOffers(
    peers: List<PipelinePeer>,
    allowUnreachable: Boolean,
    query: suspend (PipelinePeer) -> NodeOffer,
): PipelineOfferScan {
    val results = peers.chunked(8).flatMap { batch ->
        coroutineScope {
            batch.map { peer ->
                async {
                    try {
                        peer to query(peer)
                    } catch (error: Exception) {
                        if (!allowUnreachable || !workerUnreachable(error)) throw error
                        peer to null
                    }
                }
            }.awaitAll()
        }
    }
    return PipelineOfferScan(
        results.mapNotNull { (peer, offer) -> offer?.let { peer to it } },
        results.filter { it.second == null }.map { it.first },
    )
}

private fun workerUnreachable(error: Exception): Boolean =
    error is ConnectException || error is NoRouteToHostException ||
        error is SocketTimeoutException || error is UnknownHostException

/** Snapshot for one launch, never shared with another negotiation. */
internal data class PipelineLaunchAdmission(
    val workers: List<PipelinePeer>,
    val approvedGroup: List<PipelinePeer>,
    val observedAtElapsedMs: Long,
    val coordinatorLayers: Int = 0,
    val totalLayers: Int? = null,
    val groupName: String = "default",
    val modelSha256: String = "",
) {
    fun requireCurrent(nowElapsedMs: Long, currentGroup: List<PipelinePeer>, workerLimit: Int, coordinatorParticipates: Boolean = false) {
        require(observedAtElapsedMs >= 0 && nowElapsedMs >= observedAtElapsedMs &&
            nowElapsedMs - observedAtElapsedMs <= 30_000) {
            "Cluster admission expired while waiting; negotiate and load again"
        }
        require((coordinatorLayers>0)==coordinatorParticipates){"Coordinator placement policy changed; negotiate again"}
        require(approvedGroup == currentGroup && workers.size in (if(coordinatorParticipates)1 else 2)..workerLimit) {
            "Cluster approval or capacity changed; negotiate and load again"
        }
    }
}

internal fun nativePlacementArgs(endpoints: List<String>, weights: List<Int>, coordinatorLayers: Int = 0, totalLayers: Int? = null): List<String> {
    require(endpoints.size == weights.size && weights.size <= 64 && weights.all { it in 1..1024 })
    require(coordinatorLayers in 0..1023)
    if(coordinatorLayers>0)require(totalLayers!=null && endpoints.size<64 && totalLayers in 2..1024 && weights.sum()+coordinatorLayers==totalLayers){"CPU and remote layer counts must cover the model exactly"}
    if (endpoints.isEmpty()) {require(coordinatorLayers==0);return listOf("--device", "none", "-ngl", "0")}
    require(endpoints.size >= if(coordinatorLayers>0)1 else 2)
    // Reviewed pin 4df29be4: common/arg.cpp accepts "all" (-2), and
    // llama_model::n_gpu_layers() resolves it to n_layer_all + output.
    // A numeric99 cap leaves >99-block models on unbudgeted coordinator RAM.
    return listOf("--rpc", endpoints.joinToString(","), "--device",
        endpoints.indices.joinToString(",") { "RPC$it" }, "--split-mode", "layer",
        "--tensor-split", weights.joinToString(","), "-ngl", if(coordinatorLayers>0)weights.sum().toString() else "all")
}
