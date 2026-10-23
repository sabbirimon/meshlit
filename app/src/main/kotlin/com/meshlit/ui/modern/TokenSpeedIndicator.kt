package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.chat.ChatState
import com.meshlit.chat.ChatTokenUsage
import kotlinx.coroutines.delay
import java.util.Locale

internal fun tokenSpeedLabel(usage:ChatTokenUsage):String = usage.tokensPerSecond?.let {
    "${String.format(Locale.ROOT,"%.1f",it)} tokens/s · ${usage.output} output tokens"
} ?: usage.output?.let{"$it output tokens · speed unavailable"}
    ?: "Response ${String.format(Locale.ROOT,"%.1f",usage.elapsedMs/1000.0)}s · token speed unavailable"

@Composable internal fun TokenSpeedIndicator(state:ChatState,onSettings:()->Unit) {
    val usage=state.current?.messages?.lastOrNull{it.role=="assistant"}?.usage
    var now by remember{mutableLongStateOf(android.os.SystemClock.elapsedRealtime())}
    LaunchedEffect(state.running,state.generationStartedMs){while(state.running){now=android.os.SystemClock.elapsedRealtime();delay(1000)}}
    if(state.running || usage!=null) TextButton(onClick=onSettings,contentPadding=PaddingValues(horizontal=16.dp,vertical=0.dp),
        modifier=Modifier.fillMaxWidth().heightIn(min=32.dp).testTag("chat-token-speed")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Speed,null,Modifier.size(16.dp))
            Text(if(state.running) "${state.operationLabel} · ${((now-(state.generationStartedMs ?: now)).coerceAtLeast(0))/1000}s · limit ${state.activeOutputLimit ?: state.current?.options?.maxTokens ?: 1024}"
                else tokenSpeedLabel(checkNotNull(usage)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun TokenUsageDetails(usage:ChatTokenUsage?,onClose:()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text("Response token details")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(usage==null) Text("No token metadata was recorded for this older response.") else {
            Text(usage.source)
            Text("Input: ${usage.input ?: "unreported"} · Output: ${usage.output ?: "unreported"} · Cached input: ${usage.cached ?: "unreported"}")
            Text("Requested output limit: ${usage.requestedOutput} · Loaded context capacity: ${usage.contextCapacity ?: "unknown"}")
            Text("Elapsed: ${usage.elapsedMs} ms · ${tokenSpeedLabel(usage)}")
            usage.rateBasis?.let{Text(it)}
            usage.budgetReason?.let{Text(it)}
            usage.finishReason?.let{Text("Finish: $it")}
            Text(if(usage.output==null) "This runtime did not report output tokens, so token speed is unavailable. Requested limits are not actual usage; text chunks and characters are never counted as tokens." else "Counts come from the selected runtime or provider. End-to-end averages include input processing, network and application overhead; they are not a measured decode-only rate.")
        }
    }},confirmButton={TextButton(onClick=onClose){Text("Close")}})
}
