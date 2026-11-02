// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
package com.meshlit.core.hyperl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class ParallelCpuTest {
    @Test fun parallelElementwiseGraphsMatchSerialAndKeepOrderedReduction()=runBlocking {
        val recipe=HyperLLibrary.recipe("weighted_relu")
        val inputs=mapOf("x" to FloatArray(32769){if(it%2==0)-1f else 2f},"w" to FloatArray(32769){3f})
        val expected=HyperLCpuBackend().execute(recipe.program,inputs)
        for(workers in listOf(2,4,8)) assertArrayEquals(expected,HyperLCpuBackend(workers=workers).execute(recipe.program,inputs),0f)
        val reduction=HyperLLibrary.recipe("sum")
        val values=mapOf("x" to floatArrayOf(16777216f,1f,-16777216f))
        assertArrayEquals(HyperLCpuBackend().execute(reduction.program,values),HyperLCpuBackend(workers=8).execute(reduction.program,values),0f)
    }
}
