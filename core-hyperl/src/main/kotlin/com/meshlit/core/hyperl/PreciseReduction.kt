// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Explicit precise-sum/1 utility; hyperl/1 graphs keep their ordered Float sum. */
object PreciseReduction {
    suspend fun sum(values: FloatArray, budgetBytes: Long = HyperLAdmission.DEFAULT_BUDGET): Float {
        require(values.size in 1..HyperLContract.MAX_VECTOR_ELEMENTS)
        require(budgetBytes in 1..1024L * 1024 * 1024)
        val runtime = Runtime.getRuntime()
        val headroom = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) -
            32L * 1024 * 1024).coerceAtLeast(0) / 2
        require(values.size.toLong() * 8 + 65540 <= minOf(budgetBytes, headroom)) {
            "Precise sum snapshot exceeds selected budget or observed heap headroom"
        }
        currentCoroutineContext().ensureActive()
        val owned = values.copyOf()
        var sum = 0.0
        var correction = 0.0
        for (i in owned.indices) {
            if (i % HyperLContract.CANCELLATION_INTERVAL == 0) {
                currentCoroutineContext().ensureActive()
            }
            val value = owned[i].toDouble()
            require(value.isFinite()) { "Nonfinite precise sum input" }
            val next = sum + value
            correction += if (abs(sum) >= abs(value)) (sum - next) + value else (value - next) + sum
            sum = next
        }
        val total = sum + correction
        require(total.isFinite() && abs(total) <= Float.MAX_VALUE.toDouble()) {
            "Nonfinite precise sum result"
        }
        val result = total.toFloat()
        require(result.isFinite())
        currentCoroutineContext().ensureActive()
        return result
    }
}
