// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl

import kotlinx.serialization.json.*

/** Portable strict JSON entry point; no file, network or code execution. */
object HyperLCodec {
    const val MAX_PROGRAM_CHARS=HyperLContract.MAX_PROGRAM_CHARS
    const val MAX_INPUT_CHARS=HyperLContract.MAX_INPUT_CHARS
    val json=Json{prettyPrint=true;encodeDefaults=true;ignoreUnknownKeys=false}
    fun program(text:String):HyperLProgram {
        require(text.length<=MAX_PROGRAM_CHARS){"Program JSON limit"}
        val root=json.parseToJsonElement(text).jsonObject
        require(root.keys.all{it in setOf("format","inputs","instructions","output")}){"Unknown program field"}
        val declared=root.getValue("inputs").jsonArray
        require(declared.size in 1..HyperLContract.MAX_INPUTS && declared.all{it.jsonPrimitive.isString}){"Input declaration limit or type"}
        require(declared.map{it.jsonPrimitive.content}.toSet().size==declared.size){"Duplicate input declaration"}
        require(root.getValue("instructions").jsonArray.size in 1..HyperLContract.MAX_STEPS){"Instruction count limit"}
        return json.decodeFromString<HyperLProgram>(text).also{it.validate()}
    }
    fun inputs(text:String):Map<String,FloatArray> {
        require(text.length<=MAX_INPUT_CHARS){"Input JSON limit"}
        val runtime=Runtime.getRuntime()
        val headroom=(runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory())-32L*1024*1024).coerceAtLeast(0)/2
        require(text.length.toLong()*16+65536<=headroom){"Input JSON exceeds observed parsing headroom"}
        val root=json.parseToJsonElement(text).jsonObject
        require(root.size in 1..HyperLContract.MAX_INPUTS);var total=0L
        return root.mapValues{(_,value)->
            val items=value.jsonArray;require(items.size in 1..HyperLContract.MAX_VECTOR_ELEMENTS)
            total+=items.size;require(total<=HyperLContract.MAX_RETAINED_ELEMENTS){"Input exceeds retained vector budget"}
            FloatArray(items.size){i->val value=items[i].jsonPrimitive;require(!value.isString){"Numbers must not be strings"};value.float.also{require(it.isFinite()){"Nonfinite input"}}}
        }
    }
}
