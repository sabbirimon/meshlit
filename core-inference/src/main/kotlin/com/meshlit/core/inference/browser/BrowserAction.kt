package com.meshlit.core.inference.browser

import kotlinx.serialization.json.*

/** Android action contract inspired by the RA Browser Use observe/act workflow. */
data class BrowserAction(val kind: String, val index: Int? = null, val text: String? = null, val delta: Int? = null) {
    fun json(): JsonObject = buildJsonObject {
        put("action", kind)
        index?.let { put("index", it) }; text?.let { put("text", it) }; delta?.let { put("delta", it) }
    }
    companion object {
        fun parse(raw: String, elementCount: Int): BrowserAction {
            require(raw.length <= 8192 && elementCount in 0..200)
            val value = Json.parseToJsonElement(raw) as? JsonObject ?: error("Action must be JSON")
            require(value.keys.all { it in setOf("action", "index", "text", "delta") }) { "Unknown action field" }
            val kind = (value["action"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: error("Missing action")
            fun index(): Int {
                val primitive = value["index"] as? JsonPrimitive
                val index = primitive?.takeIf { !it.isString }?.intOrNull ?: error("Missing element index")
                require(index in 0 until elementCount) { "Stale or invalid element index" }
                return index
            }
            return when (kind) {
                "click" -> { require(value.keys == setOf("action", "index")); BrowserAction(kind, index()) }
                "type" -> {
                    require(value.keys == setOf("action", "index", "text"))
                    val text = (value["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: error("Missing text")
                    require(text.length <= 2000) { "Input too long" }
                    BrowserAction(kind, index(), text)
                }
                "scroll" -> {
                    require(value.keys == setOf("action", "delta"))
                    val primitive = value["delta"] as? JsonPrimitive
                    val delta = primitive?.takeIf { !it.isString }?.intOrNull ?: error("Missing scroll distance")
                    require(delta in -800..800 && delta != 0)
                    BrowserAction(kind, delta = delta)
                }
                "done" -> { require(value.keys == setOf("action")); BrowserAction(kind) }
                else -> error("Unsupported browser action")
            }
        }
    }
}
