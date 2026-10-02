package com.meshlit.ui.v2.screens

import com.meshlit.agent.AgentSession
import com.meshlit.agent.ChatMessage

/**
 * Sealed UiState for the v2 `AgentScreen`. Replaces the v1
 * `agent/AgentScreen.kt` (1,105 LOC) inline-MutableStateFlow
 * pattern with a real sealed UiState + lifecycle-aware collect.
 */
sealed interface AgentUiState {

    data class Ready(
        val messages: List<ChatMessage>,
        val mode: AgentSession.Mode,
        val autopilot: Boolean,
        val isRunning: Boolean,
        val activeModel: String?,
    ) : AgentUiState
}