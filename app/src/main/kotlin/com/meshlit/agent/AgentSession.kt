package com.meshlit.agent

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.inference.ModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Multi-turn chat with a code-tuned system prompt. Inspired by
 * Claude Code and OpenHands: one conversation, code blocks extracted
 * out, optional autopilot that lets the agent iterate without
 * waiting for human confirmation.
 *
 * Design:
 *  - Messages are an immutable `List<AgentMessage>`; the agent
 *    appends `UserMessage`, then a streaming `AgentMessage`, then
 *    finalizes it. UI recomposes off the StateFlow.
 *  - Each agent message carries a `tokenCount` so the UI can show
 *    "n tokens · Nms" badges (cheap to compute: count whitespace-
 *    delimited pieces of the final text).
 *  - Code blocks (markdown ``` fences) are pulled out into a
 *    `CodeBlock` list at finalization time so the screen can render
 *    them with syntax-aware styling + Apply / Copy / Revert.
 *  - "Autopilot" mode is a per-session boolean that, when true,
 *    causes the agent to continue iterating on its own after each
 *    turn (we send the user's next "Continue" stub automatically).
 *
 * The session owns a `Job` for the active generation so the UI can
 * cancel it (Stop button).
 */
class AgentSession(
    private val context: Context,
    private val app: MeshlitApplication,
    private val scope: CoroutineScope,
) {

    private val log = logger("AgentSession")

    enum class Mode { CHAT, CODE, PLAN }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _mode = MutableStateFlow(Mode.CHAT)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _autopilot = MutableStateFlow(false)
    val autopilot: StateFlow<Boolean> = _autopilot.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var currentJob: Job? = null

    fun setMode(mode: Mode) {
        _mode.value = mode
    }

    fun setAutopilot(on: Boolean) {
        _autopilot.value = on
    }

    fun clear() {
        currentJob?.cancel()
        currentJob = null
        _messages.value = emptyList()
        _isRunning.value = false
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _isRunning.value = false
    }

    /**
     * Force-load a specific model path. Used by the Agent's model
     * picker — when the user picks a model that isn't the one the
     * FGS auto-loaded, we trigger a fresh [InferenceCoordinator.loadModel]
     * round-trip. Returns true on success.
     */
    suspend fun loadModel(modelPath: String, contextSize: Int = 4096): MeshlitResult<ModelInfo> {
        _isRunning.value = false
        currentJob?.cancel()
        currentJob = null
        val prettyName = modelPath.substringAfterLast('/')
        append(ChatMessage.SystemMessage(text = "Loading model: $prettyName…"))
        val result = app.inferenceCoordinator.loadModel(
            modelPath = modelPath,
            contextSize = contextSize,
        )
        val confirmText = when (result) {
            is MeshlitResult.Success -> {
                val info = result.value
                "Model ready: ${info.modelName} " +
                    "(${(info.sizeBytes / 1024 / 1024)} MB, ${info.quantization})"
            }
            is MeshlitResult.Failure -> "Model load failed: ${result.error.tag}"
        }
        append(
            ChatMessage.SystemMessage(
                text = confirmText,
                kind = if (result is MeshlitResult.Failure)
                    ChatMessage.SystemMessage.Kind.ERROR
                else
                    ChatMessage.SystemMessage.Kind.INFO,
            ),
        )
        return result
    }

    /**
     * Send a user message. If a generation is already running it
     * queues into the message list immediately but defers the
     * actual `infer` call until the previous turn completes.
     */
    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        append(ChatMessage.UserMessage(text = trimmed))
        // If a turn is already in-flight we don't kick off another
        // one — the in-flight one will pick up the new user message
        // when it finishes (autopilot) or the user can hit Send
        // again to retry.
        if (_isRunning.value) return
        kickOffTurn()
    }

    private fun kickOffTurn() {
        val state = app.inferenceCoordinator.state.value
        if (state !is CoordinatorState.Ready) {
            append(
                ChatMessage.SystemMessage(
                    text = context.getString(com.meshlit.R.string.agent_no_model),
                    kind = ChatMessage.SystemMessage.Kind.ERROR,
                ),
            )
            return
        }

        val mode = _mode.value
        val autopilot = _autopilot.value
        val systemPrompt = systemPromptFor(mode)

        // Build the prompt from the rolling message history. We
        // collapse messages into a single string because the engine
        // only takes a single `prompt` parameter; role-prefixing
        // gives the model the conversation structure.
        val conv = _messages.value
        val promptBuilder = StringBuilder()
        promptBuilder.append(systemPrompt).append("\n\n")
        conv.forEach { msg ->
            when (msg) {
                is ChatMessage.UserMessage -> promptBuilder.append("USER: ").append(msg.text).append("\n\n")
                is ChatMessage.AgentMessage -> promptBuilder.append("ASSISTANT: ").append(msg.finalText).append("\n\n")
                is ChatMessage.SystemMessage -> {
                    // System messages aren't sent to the model.
                }
            }
        }
        promptBuilder.append("ASSISTANT: ")

        val started = System.currentTimeMillis()
        val placeholder = ChatMessage.AgentMessage(
            id = UUID.randomUUID().toString(),
            streamingText = "",
            finalText = "",
            tokenCount = 0,
            elapsedMs = 0L,
            codeBlocks = emptyList(),
        )
        append(placeholder)
        _isRunning.value = true

        currentJob = scope.launch {
            try {
                val sb = StringBuilder()
                var tokens = 0
                val request = InferenceRequest(
                    prompt = promptBuilder.toString(),
                    maxTokens = 512,
                    temperature = if (mode == Mode.CODE) 0.3f else 0.7f,
                    onToken = { token ->
                        sb.append(token)
                        tokens++
                        update(placeholder.id) { it.copy(streamingText = sb.toString(), tokenCount = tokens) }
                    },
                    onComplete = { _ ->
                        val finalText = sb.toString()
                        val elapsed = System.currentTimeMillis() - started
                        update(placeholder.id) {
                            it.copy(
                                streamingText = finalText,
                                finalText = finalText,
                                tokenCount = tokens,
                                elapsedMs = elapsed,
                                codeBlocks = CodeBlock.extractAll(finalText),
                            )
                        }
                        _isRunning.value = false
                        if (autopilot && shouldContinueAutopilot(finalText)) {
                            // Append an implicit "continue" message and
                            // re-run, so the model keeps iterating.
                            append(ChatMessage.UserMessage(text = "(continue)"))
                            kickOffTurn()
                        }
                    },
                )
                app.inferenceCoordinator.infer(request)
            } catch (t: Throwable) {
                log.warn("agent.fail", "agent turn failed", mapOf("err" to (t.message ?: "")))
                update(placeholder.id) {
                    it.copy(
                        finalText = context.getString(com.meshlit.R.string.agent_error, t.message ?: t.javaClass.simpleName),
                        codeBlocks = emptyList(),
                    )
                }
                _isRunning.value = false
            }
        }
    }

    /**
     * Decide whether to keep going under autopilot. We use a simple
     * heuristic: if the model's last reply ends with a code block or
     * mentions "next", "step", "TODO", or "continue" — keep going.
     * Otherwise stop and wait for human input.
     */
    private fun shouldContinueAutopilot(text: String): Boolean {
        val tail = text.takeLast(200).lowercase()
        if (tail.endsWith("```")) return true
        return tail.contains("next:") ||
            tail.contains("step ") ||
            tail.contains("todo") ||
            tail.contains("continue")
    }

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun update(id: String, transform: (ChatMessage.AgentMessage) -> ChatMessage.AgentMessage) {
        _messages.value = _messages.value.map { msg ->
            if (msg is ChatMessage.AgentMessage && msg.id == id) transform(msg) else msg
        }
    }

    private fun systemPromptFor(mode: Mode): String = when (mode) {
        Mode.CHAT -> CHAT_SYSTEM_PROMPT
        Mode.CODE -> CODE_SYSTEM_PROMPT
        Mode.PLAN -> PLAN_SYSTEM_PROMPT
    }

    companion object {
        private const val CHAT_SYSTEM_PROMPT =
            "You are Meshlit Agent — a precise, helpful assistant running " +
                "on a distributed mesh of phones. Be concise. Use markdown " +
                "for structure. If asked to do something risky, summarize " +
                "the action and confirm before executing."

        private const val CODE_SYSTEM_PROMPT =
            "You are Meshlit Agent in CODE mode. Produce runnable code in " +
                "fenced blocks with a language tag (e.g. ```kotlin). Prefer " +
                "small, focused changes. When you finish a code block, " +
                "briefly explain what it does. If a task needs multiple " +
                "files, output one block per file. Stop after the final " +
                "block; the user will press Apply to write it."

        private const val PLAN_SYSTEM_PROMPT =
            "You are Meshlit Agent in PLAN mode. Outline a numbered plan " +
                "before doing anything. Each step should be one sentence " +
                "and concrete (e.g. \"Read /etc/hosts\", \"Edit file X " +
                "to add Y\"). Don't write code yet. End with a single " +
                "line \"READY\" so the UI knows the plan is complete."
    }
}

/**
 * Sealed hierarchy of message kinds rendered by [AgentScreen].
 *
 * - [UserMessage] — text the user typed
 * - [AgentMessage] — assistant reply; carries streaming text, final
 *   text, token count, elapsed ms, and a list of extracted code blocks
 * - [SystemMessage] — informational lines (errors, plan/system
 *   notifications) shown dimmed at the bottom of the chat
 */
sealed interface ChatMessage {
    data class UserMessage(val text: String) : ChatMessage
    data class AgentMessage(
        val id: String,
        val streamingText: String,
        val finalText: String,
        val tokenCount: Int,
        val elapsedMs: Long,
        val codeBlocks: List<CodeBlock>,
    ) : ChatMessage
    data class SystemMessage(val text: String, val kind: Kind = Kind.INFO) : ChatMessage {
        enum class Kind { INFO, ERROR }
    }
}

// Re-export the AgentMessage type so the screen can pattern-match
// without importing the inner class twice.
typealias AgentMessageImpl = ChatMessage.AgentMessage

/** A code block extracted from the assistant's reply. */
data class CodeBlock(
    val language: String,
    val code: String,
    val index: Int,
) {
    companion object {
        /**
         * Greedy parser for triple-backtick fenced code blocks. We
         * support ```kotlin ... ``` and ignore inline single-backtick
         * spans. Empty blocks are dropped.
         */
        fun extractAll(text: String): List<CodeBlock> {
            val blocks = mutableListOf<CodeBlock>()
            val pattern = Regex("```([a-zA-Z0-9_+\\-]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
            val matches = pattern.findAll(text)
            var idx = 0
            matches.forEach { m ->
                val lang = m.groupValues[1].ifBlank { "text" }
                val body = m.groupValues[2].trim('\n', ' ', '\t')
                if (body.isNotBlank()) {
                    blocks.add(CodeBlock(language = lang, code = body, index = idx++))
                }
            }
            return blocks
        }
    }
}
