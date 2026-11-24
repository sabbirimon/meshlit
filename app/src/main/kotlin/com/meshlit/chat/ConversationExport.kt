package com.meshlit.chat

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ConversationExport(val label: String, val suffix: String) {
    MARKDOWN("Conversation as Markdown", "md"),
    AGENT("Conversation for agents (JSON Lines)", "jsonl"),
    CODE("Reply code blocks", "md");

    fun render(messages: List<ChatMessage>): String? {
        val turns = messages.filter { it.role == "user" || it.role == "assistant" }
        if (turns.isEmpty()) return null
        return when (this) {
            MARKDOWN -> turns.joinToString("\n\n", "# Meshlit chat\n\n", "\n") {
                val speaker = if (it.role == "user") "You" else "Meshlit"
                "## $speaker\n\n" + it.text
            }
            AGENT -> turns.mapIndexed { index, message ->
                buildJsonObject {
                    put("schema", "meshlit.chat.turn.v1")
                    put("turn", index + 1)
                    put("role", message.role)
                    put("text", message.text)
                }.toString()
            }.joinToString("\n", postfix = "\n")
            CODE -> turns.filter { it.role == "assistant" }.flatMap { fencedBlocks(it.text) }
                .takeIf { it.isNotEmpty() }?.joinToString("\n\n", postfix = "\n")
        }
    }
}

private val fenceLine = Regex("""^ {0,3}(\x60{3,}|~{3,})(.*)$""")

private fun fencedBlocks(text: String): List<String> = buildList {
    var fence = ""
    val lines = mutableListOf<String>()
    for (line in text.lineSequence()) {
        val match = fenceLine.matchEntire(line)
        if (fence.isEmpty()) {
            if (match == null) continue
            val marker = match.groupValues[1]
            if (marker.first().code == 96 && match.groupValues[2].any { it.code == 96 }) continue
            fence = marker
            lines.clear()
            lines.add(line)
        } else {
            lines.add(line)
            if (match != null && match.groupValues[1].startsWith(fence) && match.groupValues[2].isBlank()) {
                add(lines.joinToString("\n"))
                fence = ""
            }
        }
    }
}
