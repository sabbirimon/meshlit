package com.meshlit.core.inference.pipeline

/** Android compatibility boundary for the shared, JVM-independent admission kernel. */
object LayerPlacement {
    data class Plan(val counts: List<Int>, val estimatedBytes: List<Long>)

    init { System.loadLibrary("meshlit_placement") }

    private external fun nativePlan(
        capacities: LongArray, shared: Long, blocks: Int, overhead: Long, manual: IntArray?
    ): LongArray?

    fun allocate(capacities: List<Long>, sharedBytes: Long, blocks: Int, overheadBytes: Long,
                 manualCounts: List<Int>? = null): Plan {
        require(capacities.size in 2..64 && (manualCounts == null || manualCounts.size == capacities.size))
        val packed = requireNotNull(nativePlan(capacities.toLongArray(), sharedBytes, blocks, overheadBytes,
            manualCounts?.toIntArray())) { "Invalid layer placement or insufficient worker memory" }
        return Plan(capacities.indices.map { packed[it].toInt() }, capacities.indices.map { packed[capacities.size + it] })
    }
}
