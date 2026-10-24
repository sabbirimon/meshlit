package com.meshlit.core.gpu

/** Mobile target source only; no vendor SDK, bytecode compilation or device dispatch. */
object PortableEmitter {
    fun emit(program:HyperLProgram,target:HyperLTarget):HyperLGeneratedSource {
        if(target !in setOf(HyperLTarget.METAL,HyperLTarget.VULKAN_SPIRV))return HyperLSourceEmitter.emit(program,target)
        program.validate();require(program.instructions.none{it.operation=="sum"}){"Reduction lowering unavailable"}
        val inputs=program.inputs.sorted();val names=(inputs+program.instructions.map{it.output}).associateWith{"v_$it"}
        val source=StringBuilder()
        if(target==HyperLTarget.METAL){
            source.append("#include <metal_stdlib>\nusing namespace metal;\n")
            val args=inputs.mapIndexed{i,_->"device const float* input_$i [[buffer($i)]]"}+
                "device float* result [[buffer(${inputs.size})]]"+"constant uint& n [[buffer(${inputs.size+1})]]"+"uint i [[thread_position_in_grid]]"
            source.append("kernel void hyperl_kernel(${args.joinToString(", ")}) {\n  if(i < n) {\n")
        } else {
            source.append("#version 450\nlayout(local_size_x=64) in;\n")
            inputs.forEachIndexed{i,_->source.append("layout(std430,binding=$i) readonly buffer In$i { float input_$i[]; };\n")}
            source.append("layout(std430,binding=${inputs.size}) writeonly buffer Out { float result[]; };\n")
            source.append("layout(push_constant) uniform Size { uint n; };\nvoid main() { uint i=gl_GlobalInvocationID.x; if(i<n) {\n")
        }
        inputs.forEachIndexed{i,n->source.append("float ${names.getValue(n)}=input_$i[i];\n")}
        program.instructions.forEach{step->
            val a=names.getValue(step.inputs[0]);val v=names.getValue(step.output)
            val expression=when(step.operation){"add"->"$a + ${names.getValue(step.inputs[1])}";"multiply"->"$a * ${names.getValue(step.inputs[1])}";"relu"->"($a>0.0f ? $a : 0.0f)";else->error("Unsupported operation")}
            source.append("float $v=$expression;\nif(!($v<=3.402823466e+38f && $v>=-3.402823466e+38f)){result[i]=$v;return;}\n")
        }
        source.append("result[i]=${names.getValue(program.output)};\n}}\n")
        return HyperLGeneratedSource(target,if(target==HyperLTarget.METAL)"Metal Shading Language source" else "Vulkan GLSL compute source (not SPIR-V binary)",source.toString())
    }
}
