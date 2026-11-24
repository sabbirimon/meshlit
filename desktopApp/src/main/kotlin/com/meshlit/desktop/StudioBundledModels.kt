package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Studio-only catalog. Compose/mobile starter selection and imported files are unchanged. */
internal data class StudioBundledModel(val id: String, val label: String, val filename: String,
    val sizeBytes: Long, val sha256: String, val thinkingSupported: Boolean, val context: Int = 4096) {
    fun path(resources: Path): Path = resources.resolve("models").resolve(filename)
    fun verifyPresence(resources: Path): Boolean = path(resources).let { Files.isRegularFile(it) && Files.size(it) == sizeBytes }
}
internal object StudioBundledModels {
    const val defaultModelId = "qwen25-1.5b"
    val starter = StudioBundledModel(defaultModelId, DesktopStarter.label, DesktopStarter.filename, DesktopStarter.size, DesktopStarter.sha256, false)
    val reasoning = StudioBundledModel("qwen3-4b", "Qwen3 4B · Q4_K_M", "Qwen3-4B-Q4_K_M.gguf", 2497280256L,
        "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5", true)
    val models = listOf(starter, reasoning)
    fun resolve(id: String? = null): StudioBundledModel = models.singleOrNull { it.id == (id ?: defaultModelId) }
        ?: throw IllegalArgumentException("Unknown bundled Studio model")
    fun catalog(resources: Path = desktopResourceDirectory()): JsonObject = buildJsonObject {
        put("defaultModelId", defaultModelId)
        put("models", buildJsonArray { models.forEach { model -> add(buildJsonObject {
            put("id", model.id); put("label", model.label); put("filename", model.filename)
            put("sizeBytes", model.sizeBytes); put("context", model.context); put("thinkingSupported", model.thinkingSupported)
            put("present", runCatching { model.verifyPresence(resources) }.getOrDefault(false))
            put("verification", "Full SHA-256 verification occurs before loading")
        }) } })
    }
}
internal fun studioThinking(model: StudioBundledModel?, requested: String): Boolean {
    require(requested in setOf("auto", "off", "on")) { "Invalid thinking mode" }
    require(model != null || requested == "auto") { "Local thinking mode does not apply to hosted models" }
    require(requested != "on" || model?.thinkingSupported == true) { "This bundled model does not support thinking" }
    return model?.thinkingSupported == true && requested != "off"
}
internal class StudioNoFinalAnswer : IllegalStateException("The local model reached its thinking or output limit without a final answer. Try Thinking Off, a shorter question, or a larger reply limit within the configured context.")
internal fun studioValidToolCalls(value: JsonElement?, allowedNames: Set<String>): Boolean {
    val calls = value as? JsonArray ?: return false
    return calls.size in 1..32 && calls.all { item ->
        val call = item as? JsonObject ?: return@all false
        val function = call["function"] as? JsonObject ?: return@all false
        val id = (call["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name = (function["name"] as? JsonPrimitive)?.contentOrNull
        val arguments = (function["arguments"] as? JsonPrimitive)?.contentOrNull ?: return@all false
        call["type"]?.jsonPrimitive?.contentOrNull == "function" && id.length in 1..128 && id.none(Char::isISOControl) &&
            name in allowedNames && arguments.length <= 65536 && runCatching { boundedJson(arguments, 65536) }.isSuccess
    }
}
internal fun requireStudioFinalAnswer(response: JsonObject, allowedToolNames: Set<String> = emptySet()) {
    val content = if (response["streamed"]?.jsonPrimitive?.booleanOrNull == true)
        response["hasFinalContent"]?.jsonPrimitive?.booleanOrNull == true
    else response["choices"]?.jsonArray?.firstOrNull()?.let { choice ->
        choice.jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    } == true
    val tools = if (response["streamed"]?.jsonPrimitive?.booleanOrNull == true)
        response["hasValidToolCalls"]?.jsonPrimitive?.booleanOrNull == true && allowedToolNames.isNotEmpty()
    else studioValidToolCalls(response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("tool_calls"), allowedToolNames)
    if (!content && !tools) throw StudioNoFinalAnswer()
}
