package com.meshlit.core.inference.models

import kotlinx.serialization.Serializable
import java.io.File
import java.io.DataInputStream

@Serializable enum class LocalModelBackend { RUNANYWHERE, NATIVE_LOCAL }
@Serializable data class ModelRuntimeOptions(val backend:LocalModelBackend=LocalModelBackend.RUNANYWHERE,
    val contextSize:Int=2048,val keyCacheType:String="f16") {
    fun validate(maxContext:Int?=null) {
        require(contextSize in 256..8192) { "Context must be 256–8192 tokens" }
        require(backend!=LocalModelBackend.NATIVE_LOCAL || maxContext==null || contextSize<=maxContext) { "Context exceeds the GGUF training context; RoPE scaling is not enabled" }
        require(keyCacheType in setOf("f16","q8_0","q4_0")) { "Unsupported key cache type" }
        require(backend==LocalModelBackend.NATIVE_LOCAL || keyCacheType=="f16") { "RunAnywhere does not expose KV cache precision controls" }
    }
}
@Serializable data class GgufModelMetadata(val architecture:String?,val quantization:String?,val maxContext:Int?,
    val blocks:Int?,val kvHeads:Int?,val keyLength:Int?,val valueLength:Int?) {
    /** Transformer KV allocation estimate only; excludes weights, compute buffers, padding and native overhead. */
    fun kvBytes(context:Int,keyType:String):Long? {
        if(architecture !in setOf("llama","qwen2","qwen3","phi3","gemma","gemma2","mistral")) return null
        val l=blocks ?: return null;val h=kvHeads ?: return null
        val k=keyLength ?: return null;val v=valueLength ?: return null
        if(l !in 1..1024 || h !in 1..1024 || k !in 1..16384 || v !in 1..16384) return null
        val keyBytes=when(keyType){"f16"->k*2.0;"q8_0"->k*34.0/32;"q4_0"->k*18.0/32;else->return null}
        return (context.toDouble()*l*h*(keyBytes+v*2.0)).toLong()
    }
}
/** Bounded GGUF metadata reader: no tensor data or unbounded allocations. Unknown fields stay unknown. */
object GgufMetadata {
    fun read(file:File):GgufModelMetadata {
        ModelFiles.validateGguf(file)
        // Buffered sequential reads avoid hundreds of thousands of tiny file
        // syscalls while scanning tokenizer strings on slower Android storage.
        DataInputStream(file.inputStream().buffered(64 * 1024)).use { input ->
            var position=0L
            val limit=minOf(file.length(),64L*1024*1024)
            fun reserve(n:Long){
                require(n>=0 && n<=limit-position) { "GGUF metadata exceeds limit or is truncated" }
                position+=n
            }
            fun u32():Long {reserve(4);return Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL}
            fun u64():Long {reserve(8);return java.lang.Long.reverseBytes(input.readLong()).also{require(it>=0)}}
            fun skip(n:Long){
                reserve(n)
                var remaining=n
                while(remaining>0){
                    val skipped=input.skip(remaining)
                    if(skipped>0) remaining-=skipped
                    else {require(input.read()!=-1) { "Truncated GGUF metadata" };remaining--}
                }
            }
            fun string(retain:Boolean):String? {val n=u64();require(n<=16L*1024*1024);if(!retain){skip(n);return null};require(n<=4096);return ByteArray(n.toInt()).also{reserve(n);input.readFully(it)}.toString(Charsets.UTF_8)}
            fun value(type:Int,retain:Boolean,depth:Int=0):Any? {
                require(depth<=2)
                val width=when(type){0,1,7->1;2,3->2;4,5,6->4;10,11,12->8;else->0}
                if(width>0){
                    if(!retain){skip(width.toLong());return null}
                    return when(type){4,5->u32();10,11->u64();else->{skip(width.toLong());null}}
                }
                if(type==8) return string(retain)
                require(type==9) { "Unknown GGUF metadata type" }
                val element=u32().toInt();val count=u64();require(count<=1_000_000)
                val elementWidth=when(element){0,1,7->1;2,3->2;4,5,6->4;10,11,12->8;else->0}
                if(elementWidth>0) skip(Math.multiplyExact(count,elementWidth.toLong()))
                else repeat(count.toInt()){value(element,false,depth+1)}
                return null
            }
            skip(16);val count=u64();require(count<=100_000)
            val values=mutableMapOf<String,Any>()
            repeat(count.toInt()) {
                val key=string(true)!!;val type=u32().toInt()
                val wanted=key=="general.architecture" || key=="general.file_type" ||
                    key.endsWith(".context_length") || key.endsWith(".block_count") || key.endsWith(".embedding_length") ||
                    key.endsWith(".attention.head_count") || key.endsWith(".attention.head_count_kv") ||
                    key.endsWith(".attention.key_length") || key.endsWith(".attention.value_length")
                value(type,wanted)?.let{if(wanted) values[key]=it}
            }
            val arch=values["general.architecture"] as? String
            fun number(suffix:String)=(values["$arch.$suffix"] as? Long)?.takeIf{it in 1..Int.MAX_VALUE.toLong()}?.toInt()
            val heads=number("attention.head_count")
            val headLength=if(heads!=null) number("embedding_length")?.div(heads) else null
            val quant=when(values["general.file_type"] as? Long){0L->"F32";1L->"F16";2L->"Q4_0";3L->"Q4_1";7L->"Q8_0";8L->"Q5_0";9L->"Q5_1";10L->"Q2_K";11L->"Q3_K_S";12L->"Q3_K_M";13L->"Q3_K_L";14L->"Q4_K_S";15L->"Q4_K_M";16L->"Q5_K_S";17L->"Q5_K_M";18L->"Q6_K";else->null}
            return GgufModelMetadata(arch,quant,number("context_length"),number("block_count"),
                number("attention.head_count_kv") ?: heads,number("attention.key_length") ?: headLength,number("attention.value_length") ?: headLength)
        }
    }
}
