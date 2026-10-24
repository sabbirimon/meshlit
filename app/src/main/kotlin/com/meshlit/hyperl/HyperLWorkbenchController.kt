package com.meshlit.hyperl

import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.common.control.OperationGate
import com.meshlit.core.hyperl.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class HyperLCpuChoice { REFERENCE, NATIVE }
data class HyperLWorkbenchResult(val values:FloatArray,val plan:HyperLArrayPlan,val wallMs:Double,val backend:String="CPU_REFERENCE")
data class HyperLBenchmarkResult(val backend:String,val samplesMs:List<Double>,val outputSha256:String,val outputCount:Int,val graphSha256:String) {
    val medianMs get()=samplesMs.sorted().let { if(it.size%2==0) (it[it.size/2-1]+it[it.size/2])/2 else it[it.size/2] }
    val p95Ms get()=samplesMs.sorted()[(kotlin.math.ceil(samplesMs.size*0.95).toInt()-1).coerceAtLeast(0)]
}
/** Human local preprocessing only; shares the saved function/global stop gate. */
class HyperLWorkbenchController(private val gate:OperationGate) {
    private val capacity=Semaphore(1)
    private data class Prepared(val program:HyperLProgram,val inputs:Map<String,FloatArray>,val plan:HyperLArrayPlan)
    private fun prepare(program:String,inputs:String,budgetBytes:Long):Prepared {
        require(program.length<=65536 && inputs.length<=65536){"Workbench text limit: 65,536 characters per editor"}
        val parsed=HyperLCodec.program(program);val values=HyperLCodec.inputs(inputs)
        return Prepared(parsed,values,HyperLAdmission.plan(parsed,values,budgetBytes))
    }
    fun plan(program:String,inputs:String,budgetBytes:Long,choice:HyperLCpuChoice=HyperLCpuChoice.REFERENCE):HyperLArrayPlan {
        val prepared=prepare(program,inputs,budgetBytes)
        return backendPlan(prepared,choice)
    }
    private fun backendPlan(prepared:Prepared,choice:HyperLCpuChoice):HyperLArrayPlan {
        if(choice==HyperLCpuChoice.REFERENCE)return prepared.plan
        val lengths=prepared.inputs.mapValues{it.value.size}.toMutableMap()
        prepared.program.instructions.forEach{lengths[it.output]=if(it.operation=="sum")1 else lengths.getValue(it.inputs.first())}
        val estimate=prepared.plan.estimatedBytes+prepared.plan.inputBytes+lengths.getValue(prepared.program.output)*4L
        return prepared.plan.copy(estimatedBytes=estimate,admitted=prepared.plan.admitted && estimate<=prepared.plan.headroomBytes)
    }
    private suspend fun <T> bounded(work:suspend()->T):T {
        check(capacity.tryAcquire()){"HyperL busy; no request queued"}
        try{return gate.run(ManagedFeature.HYPERL){withTimeout(10000){withContext(Dispatchers.Default){work()}}}}
        finally{capacity.release()}
    }
    suspend fun execute(program:String,inputs:String,budgetBytes:Long,choice:HyperLCpuChoice=HyperLCpuChoice.REFERENCE):HyperLWorkbenchResult=bounded {
        val start=System.nanoTime();val prepared=prepare(program,inputs,budgetBytes);val plan=backendPlan(prepared,choice);HyperLAdmission.requireAdmission(plan)
        val result=if(choice==HyperLCpuChoice.NATIVE) NativeCpu.execute(prepared.program,prepared.inputs,budgetBytes)
            else HyperLRuntime(listOf(HyperLCpuBackend(budgetBytes)),maxConcurrentExecutions=1)
                .execute(HyperLTarget.CPU_REFERENCE,prepared.program,prepared.inputs)
        HyperLWorkbenchResult(result,plan,(System.nanoTime()-start)/1_000_000.0,
            if(choice==HyperLCpuChoice.NATIVE) "NATIVE_C99 · ${HyperLContract.RUNTIME_REVISION}" else "CPU_REFERENCE")
    }
    /** One warm-up and bounded sequential measurements; no speedup or cluster-throughput inference. */
    suspend fun benchmark(program:String,inputs:String,budgetBytes:Long,choice:HyperLCpuChoice,repetitions:Int=5):HyperLBenchmarkResult=bounded {
        require(repetitions in 3..10){"Choose 3–10 measured runs"}
        val prepared=prepare(program,inputs,budgetBytes)
        HyperLAdmission.requireAdmission(backendPlan(prepared,choice))
        suspend fun run():FloatArray {
            currentCoroutineContext().ensureActive()
            return if(choice==HyperLCpuChoice.NATIVE) NativeCpu.execute(prepared.program,prepared.inputs,budgetBytes)
                else HyperLRuntime(listOf(HyperLCpuBackend(budgetBytes)),maxConcurrentExecutions=1).execute(HyperLTarget.CPU_REFERENCE,prepared.program,prepared.inputs)
        }
        fun hash(values:FloatArray):String {
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            val bytes=java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            values.forEach { bytes.clear();bytes.putInt(it.toRawBits());digest.update(bytes.array()) }
            return digest.digest().joinToString(""){"%02x".format(it)}
        }
        val warmup=run();val expected=hash(warmup)
        val samples=List(repetitions){
            val start=System.nanoTime();val values=run();val elapsed=(System.nanoTime()-start)/1_000_000.0
            check(hash(values)==expected){"HyperL output changed across repeated runs"}
            elapsed
        }
        val graphHash=java.security.MessageDigest.getInstance("SHA-256").digest(HyperLCodec.json.encodeToString(prepared.program).toByteArray()).joinToString(""){"%02x".format(it)}
        HyperLBenchmarkResult(if(choice==HyperLCpuChoice.NATIVE) "NATIVE_C99 · ${HyperLContract.RUNTIME_REVISION}" else "CPU_REFERENCE",samples,expected,warmup.size,graphHash)
    }
    suspend fun precise(inputs:String,budgetBytes:Long,choice:HyperLCpuChoice):Float=bounded {
        require(inputs.length<=65536){"Workbench input limit"}
        val vectors=HyperLCodec.inputs(inputs)
        require(vectors.size==1){"Precise sum requires exactly one input vector; it does not change graph semantics"}
        val values=vectors.values.single()
        if(choice==HyperLCpuChoice.NATIVE) NativeCpu.precise(values,budgetBytes)
        else PreciseReduction.sum(values,budgetBytes)
    }
    suspend fun emit(program:String,target:HyperLTarget):String=bounded {
        require(program.length<=65536){"Workbench program limit"}
        PortableEmitter.emit(HyperLCodec.program(program),target).source
    }
}
