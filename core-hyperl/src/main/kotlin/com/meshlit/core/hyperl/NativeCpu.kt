// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
package com.meshlit.core.hyperl

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Explicit bundled C99 ABI. No fallback, download, shell, GPU or LLM substitution. */
object NativeCpu {
    private val loaded by lazy {
        System.loadLibrary("meshlit_hyperl")
        check(NativeBridge.version() == HyperLContract.RUNTIME_REVISION) { "Native HyperL revision mismatch" }
        true
    }
    suspend fun execute(program: HyperLProgram, inputs: Map<String, FloatArray>, budget: Long): FloatArray {
        val context = currentCoroutineContext()
        context.ensureActive()
        val owned = program.copy(inputs = program.inputs.toSet(), instructions = program.instructions.map { it.copy(inputs = it.inputs.toList()) })
        val vectors = inputs.toMap()
        val plan = HyperLAdmission.plan(owned, vectors, budget)
        HyperLAdmission.requireAdmission(plan)
        val names = owned.inputs.sorted() + owned.instructions.map { it.output }
        val lengths = vectors.mapValues { it.value.size }.toMutableMap()
        owned.instructions.forEach { lengths[it.output] = if (it.operation == "sum") 1 else lengths.getValue(it.inputs.first()) }
        val outputLength = lengths.getValue(owned.output)
        require(plan.estimatedBytes + plan.inputBytes + outputLength.toLong() * 4 <= plan.headroomBytes) {
            "Native JNI snapshots exceed selected budget or observed heap headroom"
        }
        check(loaded)
        val result = NativeBridge.execute(
            owned.inputs.sorted().map { vectors.getValue(it) }.toTypedArray(),
            owned.instructions.map { when (it.operation) { "add" -> 1; "multiply" -> 2; "relu" -> 3; "sum" -> 4; else -> error("Unknown opcode") } }.toIntArray(),
            owned.instructions.map { names.indexOf(it.inputs[0]) }.toIntArray(),
            owned.instructions.map { if (it.inputs.size == 2) names.indexOf(it.inputs[1]) else 0 }.toIntArray(),
            names.indexOf(owned.output), NativeCancellation { !context.isActive })
        context.ensureActive()
        return result
    }
    suspend fun precise(values: FloatArray, budget: Long): Float {
        require(values.size in 1..HyperLContract.MAX_VECTOR_ELEMENTS && budget in 1..1024L * 1024 * 1024)
        val runtime = Runtime.getRuntime()
        val available = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) - 32L * 1024 * 1024).coerceAtLeast(0) / 2
        require(values.size.toLong() * 8 + 65540 <= minOf(budget, available)) { "Precise sum snapshot exceeds budget or heap headroom" }
        val context = currentCoroutineContext(); context.ensureActive(); check(loaded)
        val result = NativeBridge.precise(values, NativeCancellation { !context.isActive })
        context.ensureActive()
        return result
    }
}

fun interface NativeCancellation { fun cancelled(): Boolean }
internal object NativeBridge {
    @JvmStatic external fun version(): String
    @JvmStatic external fun execute(inputs: Array<FloatArray>, operations: IntArray, a: IntArray, b: IntArray,
                                   output: Int, cancellation: NativeCancellation): FloatArray
    @JvmStatic external fun precise(input: FloatArray, cancellation: NativeCancellation): Float
}
