// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
package com.meshlit.core.hyperl

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreciseReductionTest {
    @Test fun compensatedPrimitiveRecoversCancellationWithoutChangingV1() = runBlocking {
        val input = floatArrayOf(16777216f, 1f, -16777216f)
        val program = HyperLProgram(inputs=setOf("x"), instructions=listOf(HyperLInstruction("p","sum",listOf("x"))),output="p")
        assertEquals(0f, HyperLCpuBackend().execute(program, mapOf("x" to input))[0], 0f)
        assertEquals(1f, PreciseReduction.sum(input), 0f)
        assertEquals(Float.MAX_VALUE, PreciseReduction.sum(floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE)), 0f)
    }
    @Test fun invalidNonfiniteAndBudgetInputsReject() = runBlocking {
        for (input in listOf(floatArrayOf(Float.NaN), floatArrayOf(Float.MAX_VALUE,Float.MAX_VALUE), floatArrayOf())) {
            try { PreciseReduction.sum(input); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        }
        try { PreciseReduction.sum(floatArrayOf(1f),1);fail("Budget") } catch (_: IllegalArgumentException) { }
    }
}
