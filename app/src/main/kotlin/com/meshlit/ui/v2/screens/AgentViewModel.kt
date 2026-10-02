package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.agent.AgentSession
import com.meshlit.di.koinInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the v2 `AgentScreen`. Reads from the shared
 * `AgentSession` and projects its `MutableStateFlow`s into a
 * single `AgentUiState.Ready` for the composable layer.
 *
 * The v1 `agent/AgentScreen.kt` consumed the session's flows
 * inline with `collectAsState()`. The v2 build hoists that
 * read into a ViewModel + sealed UiState + lifecycle-aware
 * collect, per the plan's step 5 wiring audit.
 *
 * For build no. 1 the ViewModel exposes a `Ready` state only
 * (no Loading / Failure branches) — `AgentSession` is
 * constructed eagerly by the v2 nav graph so by the time the
 * screen mounts, the session is live. Failure paths (model
 * missing, etc.) fall back to an empty `Ready` so the screen
 * never blocks waiting for a session.
 */
class AgentViewModel(
    private val session: AgentSession = koinInject(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<AgentUiState>(
        AgentUiState.Ready(
            messages = emptyList(),
            mode = AgentSession.Mode.CHAT,
            autopilot = false,
            isRunning = false,
            activeModel = null,
        ),
    )
    val uiState: StateFlow<AgentUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                session.messages,
                session.mode,
                session.autopilot,
                session.isRunning,
            ) { messages, mode, autopilot, isRunning ->
                AgentUiState.Ready(
                    messages = messages,
                    mode = mode,
                    autopilot = autopilot,
                    isRunning = isRunning,
                    activeModel = null,
                )
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Send a user message via the shared session. The session's
     * `send(text)` is the canonical entry point; the v2 pill
     * input dispatches here on IME-send.
     */
    fun send(text: String) {
        session.send(text)
    }

    /**
     * Toggle autopilot mode. The session's `setAutopilot(on)`
     * is the underlying switch; the v2 pill input's thinking
     * toggle dispatches here.
     */
    fun toggleThinking() {
        session.setAutopilot(!session.autopilot.value)
    }

    /**
     * Stop the in-flight generation if any.
     */
    fun stop() {
        session.cancel()
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AgentViewModel(session = koinInject())
                }
            }
    }
}