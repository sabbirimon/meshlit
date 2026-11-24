package com.meshlit.chat

import kotlinx.serialization.Serializable

/** Tool selection and permission grants are independent from the decision to run an agent. */
@Serializable enum class ChatExecutionMode(val label:String) {
    CHAT("Chat"), AGENT("Agent")
}

/** No keyword or language heuristic decides whether a turn may enter the tool loop. */
internal fun shouldPlanToolCall(message:String,options:ChatOptions):Boolean =
    message.isNotBlank() && options.agentToolsActive
