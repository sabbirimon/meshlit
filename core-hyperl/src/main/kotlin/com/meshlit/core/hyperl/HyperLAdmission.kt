// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl

/** Portable JVM/Android array admission. Estimates are not a memory reservation. */
data class HyperLArrayPlan(val inputBytes:Long,val retainedBytes:Long,val estimatedBytes:Long,val budgetBytes:Long,val headroomBytes:Long,val admitted:Boolean)
object HyperLAdmission {
    const val DEFAULT_BUDGET=16L*1024*1024
    fun plan(program:HyperLProgram,inputs:Map<String,FloatArray>,budgetBytes:Long=DEFAULT_BUDGET,
             heapLimitBytes:Long=Runtime.getRuntime().maxMemory(),
             heapUsedBytes:Long=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()):HyperLArrayPlan {
        program.validate();require(inputs.keys==program.inputs){"Input names do not match program"}
        require(budgetBytes in 1..1024L*1024*1024 && heapLimitBytes>0 && heapUsedBytes in 0..heapLimitBytes)
        require(inputs.values.all{it.size in 1..HyperLContract.MAX_VECTOR_ELEMENTS}){"Vector length limit"}
        val lengths=inputs.mapValues{it.value.size}.toMutableMap()
        val inputElements=inputs.values.sumOf{it.size.toLong()};var retained=inputElements
        program.instructions.forEach {step->
            val a=lengths.getValue(step.inputs[0])
            require(step.operation !in setOf("add","multiply") || a==lengths.getValue(step.inputs[1])){"HyperL shape mismatch; no implicit broadcasting"}
            val n=if(step.operation=="sum")1 else a;lengths[step.output]=n;retained+=n
        }
        val estimate=(inputElements+retained+lengths.getValue(program.output))*4+32L*(inputs.size*2+program.instructions.size+1)+65536
        val headroom=minOf(budgetBytes,(heapLimitBytes-heapUsedBytes-32L*1024*1024).coerceAtLeast(0)/2)
        return HyperLArrayPlan(inputElements*4,retained*4,estimate,budgetBytes,headroom,retained<=HyperLContract.MAX_RETAINED_ELEMENTS && estimate<=headroom)
    }
    fun requireAdmission(plan:HyperLArrayPlan){require(plan.admitted){"HyperL memory admission rejected: estimated=${plan.estimatedBytes}, available=${plan.headroomBytes}"}}
}
