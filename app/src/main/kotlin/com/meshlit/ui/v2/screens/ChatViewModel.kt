package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.FinishReason
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.inference.InferenceResult
import com.meshlit.core.inference.ModelInfo
import com.meshlit.core.inference.RuntimeEngine
import com.meshlit.di.koinInject
import com.meshlit.models.ModelCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the v2 ChatScreen. Owns the conversation list, calls
 * [InferenceCoordinator.infer] with a streaming [InferenceRequest.onToken]
 * callback, exposes model picker + backend chip data, and surfaces
 * the live coordinator state.
 *
 * Design:
 *  - All UI state lives in a single [ChatUiState] `StateFlow`.
 *  - Streaming tokens are appended to the *last* assistant message
 *    in-place via `update {}` so the composable re-renders every
 *    chunk without ever copying the full list.
 *  - Cancellation calls [InferenceCoordinator.cancel] which cancels
 *    the FGS-bound job; the active message's `FinishReason.CANCELLED`
 *    marker is added in the completion handler.
 *  - Each user submission appends a [ChatMessage.User] and a
 *    blank [ChatMessage.Assistant] placeholder; the placeholder
 *    fills with tokens as they stream.
 *  - Retry copies the last user prompt back into the input field
 *    and re-runs the same inference.
 *
 * Errors are surfaced via [ChatMessage.Assistant.errorTag] rather
 * than a separate snackbar channel so the user sees the failure
 * inside the conversation context where it occurred.
 */
class ChatViewModel(
    private val coordinator: InferenceCoordinator = koinInject(),
) : ViewModel() {

    private val log = logger("ChatViewModel")
    private val _uiState = MutableStateFlow(ChatUiState.empty())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var inflightJob: Job? = null

    init {
        // Track coordinator state so the header chip + composer can
        // react to model load / unload / error transitions without
        // polling. We extract just the fields the screen needs.
        viewModelScope.launch {
            coordinator.state.collect { state ->
                val loaded = coordinator.loadedModel()
                val runtime = coordinator.currentRuntime
                val statusText = describeState(state, loaded, runtime)
                _uiState.update {
                    it.copy(
                        coordinatorStatus = statusText,
                        loadedModel = loaded,
                        runtimeDisplay = runtime?.displayName,
                        engineTag = runtime?.runtimeId ?: "",
                    )
                }
            }
        }
        // Note: the available-models picker requires a Context to
        // enumerate filesDir/imported-models/. We refresh it from the
        // screen side via [refreshAvailableModels] using LocalContext.
    }

    /**
     * Available model files on disk for the picker. Caller passes the
     * application context (resolved via `LocalContext.current` in the
     * composable) because the ViewModel doesn't keep a long-lived
     * context reference — Android config changes could otherwise leak
     * the activity-bound context.
     */
    fun refreshAvailableModels(context: android.content.Context) {
        val files = ModelCatalog.importedFiles(context)
        val currentLoadedPath = coordinator.loadedModel()?.modelPath
        _uiState.update {
            it.copy(
                availableModels = files.map { f ->
                    AvailableModel(
                        id = f.nameWithoutExtension,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        isCurrentlyLoaded = currentLoadedPath == f.absolutePath,
                    )
                },
            )
        }
    }

    /** Send the user's prompt. Streams tokens into the active assistant message. */
    fun send(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.isGenerating) return

        val loaded = coordinator.loadedModel()
        if (loaded == null) {
            // No model loaded — append an error message so the user sees
            // what's wrong instead of the input silently disappearing.
            log.warn("chat.send.no_model", "no model loaded; surfacing in UI")
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage.Assistant(
                        text = "",
                        finishReason = FinishReason.ERROR,
                        errorTag = "no_model_loaded",
                        hint = "Load a model from the Models tab before chatting.",
                    ),
                )
            }
            return
        }

        // Append the user bubble + an empty assistant bubble. The empty
        // bubble is mutated in place during streaming.
        val userMsg = ChatMessage.User(trimmed)
        val assistantMsg = ChatMessage.Assistant(text = "", finishReason = null)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + assistantMsg,
                isGenerating = true,
                lastError = null,
            )
        }

        inflightJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val request = InferenceRequest(
                prompt = trimmed,
                maxTokens = _uiState.value.maxTokens,
                temperature = _uiState.value.temperature,
                onToken = { token -> appendToken(token) },
                onComplete = { /* coordinator emits the InferenceEvent itself */ },
            )
            val result = coordinator.infer(request)
            val duration = System.currentTimeMillis() - started
            when (result) {
                is MeshlitResult.Success -> finalizeAssistant(result.value, duration)
                is MeshlitResult.Failure -> finalizeAssistantError(result.error, duration)
            }
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    /** Append a streamed token to the in-flight assistant message. */
    private fun appendToken(token: String) {
        if (token.isEmpty()) return
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            // The last message is always the streaming assistant bubble
            // when isGenerating == true (see `send`). Replace it with a
            // new copy that has the appended text.
            val idx = msgs.lastIndex
            if (idx >= 0) {
                val last = msgs[idx]
                if (last is ChatMessage.Assistant && last.finishReason == null) {
                    msgs[idx] = last.copy(text = last.text + token)
                }
            }
            state.copy(messages = msgs)
        }
    }

    private fun finalizeAssistant(result: InferenceResult, durationMs: Long) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.lastIndex
            if (idx >= 0) {
                val last = msgs[idx]
                if (last is ChatMessage.Assistant && last.finishReason == null) {
                    msgs[idx] = last.copy(
                        text = result.finalText.ifEmpty { last.text },
                        finishReason = result.finishReason,
                        generatedTokens = result.generatedTokens,
                        tokensPerSecond = result.tokensPerSecond,
                        durationMs = durationMs,
                    )
                }
            }
            state.copy(messages = msgs)
        }
        log.info(
            "chat.send.done",
            "generation finished",
            mapOf(
                "tokens" to result.generatedTokens.toString(),
                "durationMs" to durationMs.toString(),
                "finishReason" to result.finishReason.tag,
            ),
        )
    }

    private fun finalizeAssistantError(error: MeshlitError, durationMs: Long) {
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            val idx = msgs.lastIndex
            if (idx >= 0) {
                val last = msgs[idx]
                if (last is ChatMessage.Assistant && last.finishReason == null) {
                    msgs[idx] = last.copy(
                        finishReason = FinishReason.ERROR,
                        errorTag = error.tag,
                        hint = error.message ?: error.tag,
                        durationMs = durationMs,
                    )
                }
            }
            state.copy(messages = msgs, lastError = error.message ?: error.tag)
        }
        log.warn("chat.send.error", error.message ?: error.tag, mapOf("tag" to error.tag))
    }

    /** Cancel the in-flight inference. Safe to call when nothing is running. */
    fun cancel() {
        coordinator.cancel()
        inflightJob?.cancel()
        inflightJob = null
    }

    /** Clear the conversation. Does not unload the model. */
    fun clearConversation() {
        _uiState.update { it.copy(messages = emptyList(), lastError = null) }
    }

    /** Retry the last user prompt. */
    fun retryLast() {
        val lastUser = _uiState.value.messages.lastOrNull { it is ChatMessage.User } as? ChatMessage.User
            ?: return
        // Drop the trailing errored assistant message (if any) so retry
        // doesn't keep appending failure rows.
        _uiState.update { state ->
            val msgs = state.messages.toMutableList()
            while (msgs.isNotEmpty() && msgs.last() is ChatMessage.Assistant) {
                msgs.removeAt(msgs.lastIndex)
            }
            state.copy(messages = msgs, lastError = null)
        }
        send(lastUser.text)
    }

    /** Load a model by absolute path. Updates the picker state on success. */
    fun loadModel(path: String) {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            log.info("chat.load_model.start", path)
            val result = coordinator.loadModel(path)
            when (result) {
                is MeshlitResult.Success -> {
                    log.info(
                        "chat.load_model.ok",
                        "loaded ${result.value.modelName}",
                        mapOf("params" to result.value.parameterCount.toString()),
                    )
                    // The composable side will re-call refreshAvailableModels
                    // when it observes the new loadedModel; we don't have
                    // a Context here without coupling the VM to the
                    // Android lifecycle.
                    _uiState.update { it.copy(lastError = null) }
                }
                is MeshlitResult.Failure -> {
                    val msg = result.error.message ?: result.error.tag
                    log.warn("chat.load_model.fail", msg)
                    _uiState.update { it.copy(lastError = msg) }
                }
            }
        }
    }

    /** Update the in-flight draft text so the input field survives recomposition. */
    fun onDraftChange(text: String) {
        _uiState.update { it.copy(draft = text) }
    }

    /** Update generation parameters from the composer controls. */
    fun setMaxTokens(value: Int) {
        _uiState.update { it.copy(maxTokens = value.coerceIn(16, 2048)) }
    }

    fun setTemperature(value: Float) {
        _uiState.update { it.copy(temperature = value.coerceIn(0f, 2f)) }
    }

    /** Build the human-readable status line for the top bar. */
    private fun describeState(
        state: CoordinatorState,
        loaded: ModelInfo?,
        runtime: RuntimeEngine?,
    ): String = when (state) {
        is CoordinatorState.Idle -> "Ready · no model loaded"
        is CoordinatorState.Starting -> "Starting engine…"
        is CoordinatorState.Loading -> "Loading model…"
        is CoordinatorState.Ready -> {
            val name = loaded?.modelName?.substringAfterLast('/') ?: "model"
            val engine = runtime?.displayName ?: "engine"
            "$engine · $name"
        }
        is CoordinatorState.Generating -> {
            val engine = runtime?.displayName ?: "engine"
            "$engine · generating…"
        }
        is CoordinatorState.Error -> "Error · ${state.message}"
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ChatViewModel(coordinator = koinInject())
                }
            }
    }
}

/** One entry in the model's on-disk inventory. */
data class AvailableModel(
    val id: String,
    val path: String,
    val sizeBytes: Long,
    val isCurrentlyLoaded: Boolean,
) {
    val sizeMb: Long get() = sizeBytes / (1024L * 1024L)
    val file: File get() = File(path)
}

/** The screen's single state object. */
data class ChatUiState(
    val messages: List<ChatMessage>,
    val draft: String,
    val isGenerating: Boolean,
    val lastError: String?,
    val maxTokens: Int,
    val temperature: Float,
    val coordinatorStatus: String,
    val loadedModel: ModelInfo?,
    val runtimeDisplay: String?,
    val engineTag: String,
    val availableModels: List<AvailableModel>,
) {
    val canSend: Boolean get() = !isGenerating && draft.trim().isNotEmpty()
    val showEmptyState: Boolean get() = messages.isEmpty()

    companion object {
        fun empty(): ChatUiState = ChatUiState(
            messages = emptyList(),
            draft = "",
            isGenerating = false,
            lastError = null,
            maxTokens = 256,
            temperature = 0.7f,
            coordinatorStatus = "Initializing…",
            loadedModel = null,
            runtimeDisplay = null,
            engineTag = "",
            availableModels = emptyList(),
        )
    }
}

/** A single chat message — user or assistant. */
sealed interface ChatMessage {
    val id: Long

    data class User(
        val text: String,
    ) : ChatMessage {
        override val id: Long = text.hashCode().toLong()
    }

    data class Assistant(
        val text: String,
        val finishReason: FinishReason?,
        val generatedTokens: Int = 0,
        val tokensPerSecond: Float = 0f,
        val durationMs: Long = 0L,
        val errorTag: String? = null,
        val hint: String? = null,
    ) : ChatMessage {
        override val id: Long = System.identityHashCode(this).toLong()
        val isStreaming: Boolean get() = finishReason == null && errorTag == null
        val isError: Boolean get() = finishReason == FinishReason.ERROR || errorTag != null
        val tokensLabel: String
            get() = when {
                isError -> "error"
                generatedTokens <= 0 -> ""
                else -> "$generatedTokens tok · ${"%.1f".format(tokensPerSecond)} tok/s · ${durationMs}ms"
            }
    }
}