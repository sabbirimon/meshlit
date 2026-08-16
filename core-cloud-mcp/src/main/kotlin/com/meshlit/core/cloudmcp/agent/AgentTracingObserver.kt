package com.meshlit.core.cloudmcp.agent

import com.meshlit.core.observability.TracerHolder
import io.opentelemetry.api.common.AttributeKey

/**
 * Wraps an agent invocation to emit a single span capturing
 * tool selection + tool execution. The agent loop emits a span
 * per tool call so the trace timeline shows the conversation
 * shape end-to-end.
 */
class AgentTracingObserver(
    private val delegate: AgentInvocation,
    private val logSink: AgentLogSink = NoopAgentLogSink,
    private val enabled: () -> Boolean = { true },
) : AgentInvocation {

    override suspend fun invoke(
        userMessage: String,
        context: Map<String, Any?>,
        onTool: suspend (AgentCapability) -> Unit,
    ): AgentResult {
        if (!enabled()) return delegate.invoke(userMessage, context, onTool)
        return TracerHolder.span(
            "agent.invoke",
            mapOf("user.length" to userMessage.length.toString()),
        ) { span ->
            logSink.onAgent("agent", "User turn: ${userMessage.take(120)}", mapOf("length" to userMessage.length))
            val startedTools = mutableListOf<String>()
            val wrappedOnTool: suspend (AgentCapability) -> Unit = { capability ->
                startedTools.add(capability.tag)
                TracerHolder.span(
                    "agent.tool",
                    mapOf("tool" to capability.tag, "capability" to capability.title),
                ) { toolSpan ->
                    logSink.onAgent("tool", "→ ${capability.tag}")
                    try {
                        onTool(capability)
                    } catch (t: Throwable) {
                        toolSpan.recordException(t)
                        logSink.onAgent("tool", "✗ ${capability.tag}: ${t.message}")
                        throw t
                    }
                }
                logSink.onAgent("tool", "✓ ${capability.tag}")
            }
            val result = delegate.invoke(userMessage, context, wrappedOnTool)
            span.setAttribute(AttributeKey.longKey("agent.tools"), startedTools.size.toLong())
            result
        }
    }
}

interface AgentInvocation {
    suspend fun invoke(
        userMessage: String,
        context: Map<String, Any?>,
        onTool: suspend (AgentCapability) -> Unit,
    ): AgentResult
}

data class AgentResult(
    val reply: String,
    val toolIds: List<String>,
    val citations: List<String> = emptyList(),
)

interface AgentLogSink {
    fun onAgent(tag: String, message: String, context: Map<String, Any?> = emptyMap())
}

object NoopAgentLogSink : AgentLogSink {
    override fun onAgent(tag: String, message: String, context: Map<String, Any?>) = Unit
}
