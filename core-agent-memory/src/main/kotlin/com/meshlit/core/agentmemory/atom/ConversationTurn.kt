package com.meshlit.core.agentmemory.atom

import kotlinx.serialization.Serializable

/**
 * A single L0 turn — a raw, unprocessed user or assistant message.
 * The distiller collapses a stream of [ConversationTurn]s into one
 * or more L1 [MemoryAtom]s (see
 * [com.meshlit.core.agentmemory.distill.MemoryDistiller]).
 *
 * @property speaker `"user"` or `"assistant"`. Kept as a string for
 *  JSON-friendliness — the distiller doesn't need an enum to route.
 * @property text The raw turn text.
 * @property timestampMs Wall-clock timestamp from the calling agent.
 */
@Serializable
data class ConversationTurn(
    val speaker: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    init {
        require(text.isNotBlank()) { "ConversationTurn.text must not be blank" }
        require(speaker.isNotBlank()) { "ConversationTurn.speaker must not be blank" }
    }
}