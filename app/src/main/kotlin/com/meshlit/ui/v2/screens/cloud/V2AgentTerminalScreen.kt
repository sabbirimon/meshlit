package com.meshlit.ui.v2.screens.cloud

import androidx.compose.runtime.Composable
import com.meshlit.core.cloudmcp.rag.RagDecision
import com.meshlit.core.cloudmcp.rag.RagMode
import com.meshlit.ui.screens.cloud.AgentLoopMode
import com.meshlit.ui.screens.cloud.AgentTerminalScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrapper for the agent terminal. The terminal is the
 * cluster's live agent session — the lead bar here surfaces
 * the role identity (provider + loop mode + RAG decision) so
 * the user lands on the terminal already knowing what the
 * agent will do.
 */
@Composable
fun V2AgentTerminalScreen(
    providerId: String?,
    loopMode: AgentLoopMode,
    ragMode: RagMode,
    ragDecision: RagDecision?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    MeshlitDeepLinkWrap(
        headline = "Agent terminal",
        subtitle = buildAgentSubtitle(providerId, loopMode, ragDecision),
    ) {
        AgentTerminalScreen(
            providerId = providerId,
            loopMode = loopMode,
            ragMode = ragMode,
            ragDecision = ragDecision,
            onBack = onBack,
            onSend = onSend,
        )
    }
}

private fun buildAgentSubtitle(
    providerId: String?,
    loopMode: AgentLoopMode,
    ragDecision: RagDecision?,
): String {
    val parts = buildList {
        providerId?.takeIf { it.isNotBlank() }?.let(::add)
        add(loopMode.name)
        ragDecision?.let { add("RAG: ${it.javaClass.simpleName}") }
    }
    return parts.joinToString(" · ")
}
