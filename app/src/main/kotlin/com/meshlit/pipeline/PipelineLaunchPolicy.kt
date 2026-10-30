package com.meshlit.pipeline

/** Snapshot for one launch, never shared with another negotiation. */
internal data class PipelineLaunchAdmission(
    val workers: List<PipelinePeer>,
    val approvedGroup: List<PipelinePeer>,
    val observedAtElapsedMs: Long,
) {
    fun requireCurrent(nowElapsedMs: Long, currentGroup: List<PipelinePeer>, workerLimit: Int) {
        require(observedAtElapsedMs >= 0 && nowElapsedMs >= observedAtElapsedMs &&
            nowElapsedMs - observedAtElapsedMs <= 30_000) {
            "Cluster admission expired while waiting; negotiate and load again"
        }
        require(approvedGroup == currentGroup && workers.size in 2..workerLimit) {
            "Cluster approval or capacity changed; negotiate and load again"
        }
    }
}

internal fun nativePlacementArgs(endpoints: List<String>, weights: List<Int>): List<String> {
    require(endpoints.size == weights.size && weights.size <= 64 && weights.all { it in 1..1024 })
    if (endpoints.isEmpty()) return listOf("--device", "none", "-ngl", "0")
    require(endpoints.size >= 2)
    // Reviewed pin 4df29be4: common/arg.cpp accepts "all" (-2), and
    // llama_model::n_gpu_layers() resolves it to n_layer_all + output.
    // A numeric99 cap leaves >99-block models on unbudgeted coordinator RAM.
    return listOf("--rpc", endpoints.joinToString(","), "--device",
        endpoints.indices.joinToString(",") { "RPC$it" }, "--split-mode", "layer",
        "--tensor-split", weights.joinToString(","), "-ngl", "all")
}
