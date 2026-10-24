package com.meshlit.core.inference.pipeline

/** Integer layer admission for the existing native layer-RPC path. This estimates
 * uniform transformer block costs; it does not promise native tensor placement,
 * reserve memory or turn a host API into an enrolled worker. */
object LayerPlacement {
    data class Plan(val counts: List<Int>, val estimatedBytes: List<Long>)
    fun allocate(capacities: List<Long>, sharedBytes: Long, blocks: Int, overheadBytes: Long,
                 manualCounts: List<Int>? = null): Plan {
        require(blocks in 2..1024 && capacities.size in 2..minOf(64, blocks))
        require(sharedBytes > 0 && sharedBytes <= Long.MAX_VALUE / 4 && overheadBytes in 0..Long.MAX_VALUE / 4)
        require(capacities.all { it in 1..(1L shl 50) })
        val perLayer = sharedBytes / blocks + if (sharedBytes % blocks != 0L) 1 else 0
        // One spare layer allows for placement rounding/nonuniform blocks. It is
        // deliberately retained even though the requested counts are integers.
        val reserve = Math.addExact(overheadBytes, perLayer)
        val maximum = capacities.map { ((it - reserve).coerceAtLeast(0) / perLayer).coerceAtMost(blocks.toLong()).toInt() }
        require(maximum.all { it > 0 } && maximum.sum() >= blocks) { "Workers cannot admit all layers with KV/graph headroom" }
        val counts = if (manualCounts != null) {
            require(manualCounts.size == capacities.size && manualCounts.all { it in 1..blocks } && manualCounts.sum() == blocks) { "Manual layer counts must cover every layer exactly once" }
            require(manualCounts.indices.all { manualCounts[it] <= maximum[it] }) { "Manual placement exceeds a worker memory budget" }
            manualCounts.toMutableList()
        } else {
            val assigned = MutableList(capacities.size) { 1 }
            repeat(blocks - capacities.size) {
                val chosen = assigned.indices.filter { assigned[it] < maximum[it] }
                    .minWithOrNull(compareBy<Int> { (assigned[it] + 1).toDouble() / maximum[it] }.thenBy { it })
                    ?: error("No worker capacity remains")
                assigned[chosen]++
            }
            assigned
        }
        val estimates = counts.map { Math.addExact(Math.multiplyExact(perLayer, it.toLong()), reserve) }
        check(estimates.indices.all { estimates[it] <= capacities[it] })
        return Plan(counts, estimates)
    }
}
