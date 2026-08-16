package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.rag.RagBackend
import com.meshlit.core.cloudmcp.rag.RagDecision
import com.meshlit.core.cloudmcp.rag.RagMode
import com.meshlit.ui.components.RaPillChip
import com.meshlit.ui.components.RaPillTone

/**
 * Small chip that surfaces the active RAG backend. Rendered on the
 * Cloud Hub header, the Agent Terminal header, and inside the
 * Settings → RAG screen.
 *
 * The chip is intentionally tiny — the user wants a glance, not a
 * paragraph. When [onClick] is supplied the chip becomes
 * tappable and routes to the RAG settings screen.
 */
@Composable
fun RagIndicatorChip(
    mode: RagMode,
    state: RagDecision?,
    onClick: (() -> Unit)? = null,
) {
    val text = chipLabel(mode = mode, state = state)
    val tone = chipTone(state)
    if (onClick == null) {
        RaPillChip(text = text, tone = tone, modifier = Modifier)
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(0.dp),
        ) {
            RaPillChip(text = text, tone = tone, modifier = Modifier)
        }
    }
}

@Composable
private fun chipLabel(mode: RagMode, state: RagDecision?): String {
    val backend = state?.backend
    return when {
        backend == null -> when (mode) {
            RagMode.Local -> stringResource(R.string.cloud_rag_local)
            RagMode.Remote -> stringResource(R.string.cloud_rag_remote)
            RagMode.Auto -> stringResource(R.string.cloud_rag_auto)
            RagMode.Ask -> stringResource(R.string.cloud_rag_ask)
        }
        mode == RagMode.Auto && backend == RagBackend.Local ->
            stringResource(R.string.cloud_rag_auto_local)
        mode == RagMode.Auto && backend == RagBackend.Remote ->
            stringResource(R.string.cloud_rag_auto_remote)
        mode == RagMode.Ask -> stringResource(R.string.cloud_rag_ask)
        backend == RagBackend.Local -> stringResource(R.string.cloud_rag_local)
        backend == RagBackend.Remote -> stringResource(R.string.cloud_rag_remote)
        backend == RagBackend.Hybrid -> stringResource(R.string.cloud_rag_auto)
        else -> stringResource(R.string.cloud_rag_auto)
    }
}

private fun chipTone(state: RagDecision?): RaPillTone = when (state?.backend) {
    RagBackend.Local -> RaPillTone.BUNDLED
    RagBackend.Remote -> RaPillTone.TOP_PICK
    RagBackend.Hybrid -> RaPillTone.NPU
    null -> RaPillTone.NEUTRAL
}
