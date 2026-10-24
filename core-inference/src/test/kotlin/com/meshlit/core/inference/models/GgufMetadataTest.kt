package com.meshlit.core.inference.models

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GgufMetadataTest {
    private fun fixture(truncate:Boolean=false, oversized:Boolean=false):File {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun u32(n:Int)=out.writeInt(Integer.reverseBytes(n))
            fun u64(n:Long)=out.writeLong(java.lang.Long.reverseBytes(n))
            fun str(s:String){val b=s.toByteArray();u64(b.size.toLong());out.write(b)}
            fun scalar(key:String,n:Int){str(key);u32(4);u32(n)}
            out.writeBytes("GGUF");u32(3);u64(1);u64(8)
            str("general.architecture");u32(8);str("llama")
            // A real tokenizer-sized array must be skipped before later keys.
            str("tokenizer.ggml.tokens");u32(9);u32(8);u64(if(oversized) 1_000_001 else 50_000)
            if(!oversized) repeat(50_000){str("token-$it")}
            scalar("general.file_type",15);scalar("llama.context_length",2048)
            scalar("llama.block_count",30);scalar("llama.embedding_length",576)
            scalar("llama.attention.head_count",9);scalar("llama.attention.head_count_kv",3)
        }
        return File.createTempFile("gguf-metadata-",".gguf").apply {
            val payload=bytes.toByteArray();writeBytes(if(truncate) payload.copyOf(payload.size-2) else payload)
        }
    }
    @Test fun tokenizerArrayDoesNotHideFollowingRuntimeMetadata(){
        val f=fixture()
        try {
            val m=GgufMetadata.read(f)
            assertEquals("llama",m.architecture);assertEquals("Q4_K_M",m.quantization)
            assertEquals(2048,m.maxContext);assertEquals(30,m.blocks)
            assertEquals(3,m.kvHeads);assertEquals(64,m.keyLength)
            assertEquals(47185920L,m.kvBytes(2048,"f16"))
        } finally {f.delete()}
    }
    @Test fun truncatedScalarCannotBecomeValidMetadata(){
        val f=fixture(truncate=true)
        try {assertThrows(IllegalArgumentException::class.java){GgufMetadata.read(f)}} finally {f.delete()}
    }
    @Test fun oversizedTokenizerArrayIsRejectedBeforeIteration(){
        val f=fixture(oversized=true)
        try {assertThrows(IllegalArgumentException::class.java){GgufMetadata.read(f)}} finally {f.delete()}
    }
}
