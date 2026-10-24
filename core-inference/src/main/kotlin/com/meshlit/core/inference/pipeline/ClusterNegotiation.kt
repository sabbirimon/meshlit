package com.meshlit.core.inference.pipeline

import kotlinx.serialization.Serializable

@Serializable data class NodeOffer(
    val nodeId:String,val abi:String,val freeMemoryBytes:Long,val freeDiskBytes:Long,
    val runtimeRevision:String,val workerAllowed:Boolean,val coordinatorAllowed:Boolean=false,
    val coordinatorModelSha256:String?=null,val thermalStatus:Int=0,val observedAtMs:Long=System.currentTimeMillis(),
    val deviceClass:String="phone",val backend:String="CPU",val cpuThreads:Int=2,
    val workerStartedAtMs:Long?=null,
)
data class NegotiatedCluster(val coordinatorId:String,val workers:List<NodeOffer>,val weights:List<Int>,val memoryBudgetBytes:Long,val estimatedWorkerBytes:List<Long> = emptyList(),val estimatedKvBytes:Long=0,val estimatedCoordinatorBytes:Long=0)

/** Deterministic capability election. Device labels never imply computation.
 * Capability snapshots are admission estimates, not memory reservations. */
object ClusterNegotiation {
    const val REVISION="4df29be4f4c3673f428170fda944a5b19f743bb8"
    fun budget(offer:NodeOffer):Long=(offer.freeMemoryBytes*0.65).toLong().minus(256L*1024*1024).coerceAtLeast(0)
    fun plan(offers:List<NodeOffer>,modelSha256:String,modelBytes:Long,nowMs:Long=System.currentTimeMillis(),kvBytes:Long=0,blocks:Int?=null,manualLayers:Map<String,Int>?=null):NegotiatedCluster {
        require(nowMs in 0..Long.MAX_VALUE-5000) {"Invalid capability observation time"}
        require(manualLayers==null || manualLayers.size in 2..64) {"Manual placement needs 2–64 workers"}
        require(offers.size<=64){"Too many cluster offers"}
        require(offers.all{it.freeMemoryBytes in 0..(1L shl 50) && it.freeDiskBytes>=0 && it.nodeId.matches(Regex("[A-Za-z0-9_-]{1,80}"))}){"Invalid node capability bounds"}
        require(modelSha256.matches(Regex("[a-f0-9]{64}")) && modelBytes>0 && modelBytes<=Long.MAX_VALUE/4 && kvBytes>=0 && kvBytes<=Long.MAX_VALUE/4)
        require(blocks==null || blocks in 2..1024)
        require(manualLayers==null || blocks!=null) {"Manual placement requires model block metadata"}
        require(offers.all { it.thermalStatus>=0 && it.cpuThreads in 1..1024 && it.abi.length in 1..80 }) {"Invalid runtime capability bounds"}
        require(offers.map{it.nodeId}.distinct().size==offers.size){"Duplicate node identities"}
        val compatible=offers.filter { it.runtimeRevision==REVISION && it.backend=="CPU" && it.observedAtMs>=0 && it.observedAtMs<=nowMs+5000 &&
            nowMs-it.observedAtMs<=30000 && it.thermalStatus<3 && it.freeMemoryBytes>0 }
        val master=compatible.filter{it.coordinatorAllowed && it.coordinatorModelSha256==modelSha256}
            .sortedWith(compareByDescending<NodeOffer>{budget(it)}.thenBy{it.nodeId}).firstOrNull()
            ?: error("No approved coordinator has the selected model")
        var workers=compatible.filter{it.workerAllowed && it.nodeId!=master.nodeId && budget(it)>0}
            .sortedBy{it.nodeId}
        require(workers.size>=2){"Need at least two fresh, compatible, approved workers"}
        // Rounded integer arithmetic keeps estimates conservative and avoids
        // floating-point loss on large models/KV caches.
        fun scaled(value:Long,numerator:Long):Long = Math.addExact(
            Math.multiplyExact(value/10,numerator), (value%10*numerator+9)/10)
        val shared=Math.addExact(scaled(modelBytes,13),scaled(kvBytes,12))
        require(shared<=Long.MAX_VALUE/4) {"Memory estimate exceeds supported bounds"}
        val coordinatorEstimate=Math.addExact(Math.addExact(modelBytes/10,kvBytes/20),256L*1024*1024)
        require(budget(master)>=coordinatorEstimate){"Coordinator lacks estimated embedding/scheduling memory"}
        if(blocks!=null) {
            val layerBytes=shared/blocks + if(shared%blocks!=0L) 1 else 0
            val reserve=256L*1024*1024+layerBytes
            if(manualLayers==null) {
                // Weak optional members no longer make an otherwise usable
                // cluster fail. Keep at most one stage per model block.
                workers=workers.filter { budget(it)>=reserve+layerBytes }
                    .sortedWith(compareByDescending<NodeOffer>{budget(it)}.thenBy{it.nodeId})
                    .take(blocks).sortedBy{it.nodeId}
            } else {
                require(manualLayers.keys.all { id -> workers.any { it.nodeId==id } }) {"Manual placement names an unapproved, stale or incompatible worker"}
                workers=workers.filter { it.nodeId in manualLayers }
            }
            require(workers.size>=2){"Need at least two workers with room for a layer and overhead"}
            val placement=LayerPlacement.allocate(workers.map(::budget),shared,blocks,256L*1024*1024,
                manualLayers?.let { manual -> workers.map { manual.getValue(it.nodeId) } })
            return NegotiatedCluster(master.nodeId,workers,placement.counts,workers.sumOf(::budget),placement.estimatedBytes,kvBytes,coordinatorEstimate)
        }
        // Leave graph overhead and one layer of placement granularity on each worker.
        val padding=256L*1024*1024 + shared/8
        val effective=workers.map{(budget(it)-padding).coerceAtLeast(0)}
        require(effective.all{it>0}){"A worker lacks room for graph and layer-placement overhead"}
        val capacity=workers.sumOf{budget(it)}
        val usable=effective.sum()
        require(usable>=shared){"Cluster memory estimate is too small for weights, context/KV and graph overhead"}
        val weights=effective.map{((it.toDouble()/usable)*1000).toInt().coerceAtLeast(1)}
        val sumWeights=weights.sum().toDouble()
        val estimates=weights.map{ kotlin.math.ceil(shared*(it/sumWeights)).toLong()+padding }
        require(estimates.zip(workers).all{(estimate,node)->estimate<=budget(node)}){"Rounded layer placement exceeds a worker budget"}
        return NegotiatedCluster(master.nodeId,workers,weights,capacity,estimates,kvBytes,coordinatorEstimate)
    }
}
