package com.meshlit.workspace

/** Portable UI settings only; language never changes prompts, tools or model weights. */
enum class WorkspaceLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"), SIMPLIFIED_CHINESE("zh-Hans", "简体中文");
    fun text(english: String, chinese: String): String = if (this == SIMPLIFIED_CHINESE) chinese else english
    companion object { fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: ENGLISH }
}
enum class WorkspaceLook(val label: String, val background: Long, val surface: Long, val foreground: Long, val accent: Long) {
    STUDIO("Studio", 0xFF101113, 0xFF242426, 0xFFF1F2F3, 0xFFFF965C),
    GRAPHITE("Graphite", 0xFF101113, 0xFF202226, 0xFFF1F2F3, 0xFFE5E7EB),
    PAPER("Paper", 0xFFF9FAFB, 0xFFEDF0F3, 0xFF17191D, 0xFF2563EB),
    AURORA("Aurora", 0xFF121523, 0xFF22283B, 0xFFF2F4FF, 0xFF8AB4FF)
}
data class ChatTurn(val role: String, val content: String) {
    init { require(role in setOf("user", "assistant")); require(content.length <= 131072) }
}
data class GenerationBudget(val maxOutputTokens: Int = 2048) {
    init { require(maxOutputTokens in 64..4096) }
}
/** Reported token counts stay unknown until the engine/provider actually reports them. */
data class GenerationUsage(val outputTokens: Long? = null, val generationNanos: Long? = null) {
    val tokensPerSecond: Double? get() = if (outputTokens != null && generationNanos != null &&
        outputTokens > 0 && generationNanos > 0) outputTokens * 1_000_000_000.0 / generationNanos else null
}
enum class RuntimeAvailability { UNAVAILABLE, HOST_CLIENT, LOCAL_RUNTIME }
