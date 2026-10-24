// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable

/** HyperL v1: bounded declarative f32 vector kernels. No shell, pointers, host I/O,
 * recursive calls, arbitrary code or automatic backend substitution. */
@Serializable data class HyperLInstruction(val output:String,val operation:String,val inputs:List<String>)
@Serializable data class HyperLProgram(val format:String=HyperLContract.LANGUAGE_FORMAT,val inputs:Set<String>,val instructions:List<HyperLInstruction>,val output:String) {
    fun validate(){
        require(format==HyperLContract.LANGUAGE_FORMAT) {"Unsupported HyperL language version"}
        require(inputs.size in 1..HyperLContract.MAX_INPUTS && instructions.size in 1..HyperLContract.MAX_STEPS)
        val defined=inputs.toMutableSet();require(defined.all{it.matches(NAME)})
        instructions.forEach { step ->
            require(step.output.matches(NAME) && step.output !in defined && step.inputs.all{it in defined}) {"Invalid HyperL dependency or duplicate output"}
            require(step.inputs.size == (HyperLContract.operandCounts[step.operation] ?: error("Unsupported HyperL operation")))
            defined.add(step.output)
        }
        require(output in defined)
    }
}
@Serializable enum class HyperLTarget { CPU_REFERENCE, LLVM_CPU, METAL, CUDA, ROCM_HIP, VULKAN_SPIRV, OPENCL_SPIRV, SYCL, NPU_STABLEHLO, FPGA_VENDOR }
interface HyperLBackend {
    val target:HyperLTarget
    val runtimeRevision:String
    suspend fun execute(program:HyperLProgram,inputs:Map<String,FloatArray>):FloatArray
}
/** Genuine Kotlin/Android/JVM CPU backend. It is a correctness reference, not an LLM
 * inference engine or a CUDA-compatible compiler. Accelerated targets require adapters. */
class HyperLCpuBackend(private val memoryBudgetBytes:Long=HyperLAdmission.DEFAULT_BUDGET):HyperLBackend {
    override val target=HyperLTarget.CPU_REFERENCE
    override val runtimeRevision=HyperLContract.RUNTIME_REVISION
    override suspend fun execute(program:HyperLProgram,inputs:Map<String,FloatArray>):FloatArray {
        currentCoroutineContext().ensureActive()
        program.validate()
        val ownedProgram=program.copy(inputs=program.inputs.toSet(),instructions=program.instructions.map{it.copy(inputs=it.inputs.toList())})
        ownedProgram.validate()
        val vectors=inputs.toMap();require(vectors.keys==ownedProgram.inputs)
        require(vectors.values.all{it.size in 1..HyperLContract.MAX_VECTOR_ELEMENTS})
        var retained=vectors.values.sumOf{it.size.toLong()}
        require(retained<=HyperLContract.MAX_RETAINED_ELEMENTS){"HyperL input vectors exceed retained memory budget"}
        // Validate the complete shape/retention estimate before allocating input copies.
        HyperLAdmission.requireAdmission(HyperLAdmission.plan(ownedProgram,vectors,memoryBudgetBytes))
        val live=vectors.mapValues{(_,source)->FloatArray(source.size).also{copy->
            for(i in source.indices){if(i%HyperLContract.CANCELLATION_INTERVAL==0)currentCoroutineContext().ensureActive();val value=source[i];require(value.isFinite()){"HyperL nonfinite input"};copy[i]=value}
        }}.toMutableMap()
        for(step in ownedProgram.instructions){
            currentCoroutineContext().ensureActive()
            val args=step.inputs.map{live.getValue(it)};val length=if(step.operation=="sum") 1 else args[0].size
            require(step.operation !in setOf("add","multiply") || args[0].size==args[1].size){"HyperL shape mismatch; no implicit broadcasting"}
            retained+=length;require(retained<=HyperLContract.MAX_RETAINED_ELEMENTS){"HyperL working memory exceeds 4 MiB vector budget"}
            val result=FloatArray(length)
            if(step.operation=="sum"){
                var sum=0f;args[0].forEachIndexed{i,value->if(i%HyperLContract.CANCELLATION_INTERVAL==0) currentCoroutineContext().ensureActive();sum+=value;require(sum.isFinite()){"HyperL nonfinite ordered sum at ${step.output}"}};result[0]=sum
            }else for(i in 0 until length){
                if(i%HyperLContract.CANCELLATION_INTERVAL==0) currentCoroutineContext().ensureActive()
                val value=when(step.operation){"add"->args[0][i]+args[1][i];"multiply"->args[0][i]*args[1][i];"relu"->maxOf(0f,args[0][i]);else->error("Unsupported operation")}
                require(value.isFinite()){"HyperL nonfinite result at ${step.output}"};result[i]=value
            }
            require(result.all(Float::isFinite)){"HyperL nonfinite result at ${step.output}"};live[step.output]=result
        }
        currentCoroutineContext().ensureActive()
        return live.getValue(ownedProgram.output).copyOf()
    }
}
/** Only explicitly registered backends execute. A target name never creates a driver. */
class HyperLRuntime(backends:List<HyperLBackend> = listOf(HyperLCpuBackend()),maxConcurrentExecutions:Int=2,private val deadlineMs:Long=10000) {
    private val adapters=backends.associateBy{it.target}.also{require(it.size==backends.size)}
    private val admission=Semaphore(maxConcurrentExecutions.also{require(it in 1..32)})
    init{require(deadlineMs in 100..60000)}
    fun installedTargets()=adapters.keys
    suspend fun execute(target:HyperLTarget,program:HyperLProgram,inputs:Map<String,FloatArray>):FloatArray {
        val backend=adapters[target] ?: error("HyperL backend unavailable: $target")
        check(admission.tryAcquire()){"HyperL busy: execution capacity reached; no request queued"}
        try{return withTimeout(deadlineMs){backend.execute(program,inputs)}}finally{admission.release()}
    }
}
private val NAME=Regex(HyperLContract.IDENTIFIER_PATTERN)
