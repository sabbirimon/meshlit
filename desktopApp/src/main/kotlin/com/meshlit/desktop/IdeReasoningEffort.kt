package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.net.URI

/** Owner configuration and SDK output are separate: an SDK cannot raise effort. */
internal fun ideCompletionBody(body: JsonObject, route: JsonObject?, effort: String, model: String, maxOutput: Int, streaming: Boolean): JsonObject {
    require(effort in setOf("auto", "low", "medium", "high")) { "Unknown reasoning effort" }
    val local = route == null || route["kind"]?.jsonPrimitive?.content == "local"
    val support = route?.get("effortSupport") as? JsonObject
    val bound = support != null && route != null && support["endpoint"] == route["endpoint"] && support["model"] == route["model"]
    val configuredAdapter = support?.get("adapter")?.jsonPrimitive?.content
    val adapter = configuredAdapter?.takeIf {
        !local && bound && it in setOf("reasoning_effort", "reasoning.effort") &&
            (it != "reasoning.effort" || URI(route!!.getValue("endpoint").jsonPrimitive.content).host == "openrouter.ai")
    }
    if (effort != "auto") {
        require(route != null && !local && support != null) { "Reasoning effort is unavailable for this backend" }
        require(support["endpoint"] == route["endpoint"] && support["model"] == route["model"]) { "Effort support belongs to another connection/model" }
        require(adapter != null) { "Unsupported effort adapter" }
        require((support["values"] as? JsonArray)?.any { it.jsonPrimitive.content == effort } == true) { "Effort level is not configured for this model" }
        if (adapter == "reasoning.effort") require(URI(route.getValue("endpoint").jsonPrimitive.content).host == "openrouter.ai") { "OpenRouter effort requires its configured API" }
    }
    return JsonObject(body.toMutableMap().apply {
        // Ignore parameters from the SDK/model. Auto never sends an effort field.
        for (field in listOf("reasoning_effort", "reasoning", "think", "thinking", "enable_thinking", "max_completion_tokens", "max_tokens", "stream_options")) remove(field)
        put("model", JsonPrimitive(model)); put("stream", JsonPrimitive(streaming))
        if (streaming) put("stream_options", buildJsonObject { put("include_usage", true) })
        put(if (adapter == "reasoning_effort") "max_completion_tokens" else "max_tokens", JsonPrimitive(maxOutput))
        if (adapter != null) {
            // Some reasoning models reject sampling controls rather than ignoring them.
            remove("temperature"); remove("top_p")
        }
        if (effort != "auto") {
            if (adapter == "reasoning_effort") put("reasoning_effort", JsonPrimitive(effort))
            else put("reasoning", buildJsonObject { put("effort", effort) })
        }
    })
}
