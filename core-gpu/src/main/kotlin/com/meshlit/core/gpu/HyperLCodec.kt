package com.meshlit.core.gpu

import kotlinx.serialization.json.*

/** Portable strict JSON entry point; no file, network or code execution. */
object HyperLCodec {
    const val MAX_PROGRAM_CHARS=65536
    const val MAX_INPUT_CHARS=16*1024*1024
    val json=Json{prettyPrint=true;encodeDefaults=true;ignoreUnknownKeys=false}
    fun program(text:String):HyperLProgram {
        require(text.length<=MAX_PROGRAM_CHARS){"Program JSON limit"}
        val root=json.parseToJsonElement(text).jsonObject
        require(root.keys.all{it in setOf("format","inputs","instructions","output")}){"Unknown program field"}
        val declared=root.getValue("inputs").jsonArray
        require(declared.size in 1..8 && declared.all{it.jsonPrimitive.isString}){"Input declaration limit or type"}
        require(declared.map{it.jsonPrimitive.content}.toSet().size==declared.size){"Duplicate input declaration"}
        require(root.getValue("instructions").jsonArray.size in 1..64){"Instruction count limit"}
        return json.decodeFromString<HyperLProgram>(text).also{it.validate()}
    }
    fun inputs(text:String):Map<String,FloatArray> {
        require(text.length<=MAX_INPUT_CHARS){"Input JSON limit"}
        val runtime=Runtime.getRuntime()
        val headroom=(runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())-32L*1024*1024).coerceAtLeast(0)/2
        require(text.length.toLong()*16+65536<=headroom){"Input JSON exceeds observed parsing headroom"}
        val root=json.parseToJsonElement(text).jsonObject
        require(root.size in 1..8);var total=0L
        return root.mapValues{(_,value)->
            val items=value.jsonArray;require(items.size in 1..262144)
            total+=items.size;require(total<=1048576){"Input exceeds retained vector budget"}
            FloatArray(items.size){i->val value=items[i].jsonPrimitive;require(!value.isString){"Numbers must not be strings"};value.float.also{require(it.isFinite()){"Nonfinite input"}}}
        }
    }
}
