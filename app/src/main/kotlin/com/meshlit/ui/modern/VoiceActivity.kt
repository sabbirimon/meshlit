package com.meshlit.ui.modern

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.meshlit.chat.VoiceState

/** Activity animation; measured microphone/PCM levels only, never invented speech samples. */
@Composable internal fun VoiceActivity(state:VoiceState,modifier:Modifier=Modifier) {
    val color=if(state.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val pulse=if(state.busy && !state.muted) {
        val transition=rememberInfiniteTransition(label="Voice activity")
        val value by transition.animateFloat(0.7f,1f,infiniteRepeatable(tween(1000),RepeatMode.Reverse),label="Activity pulse")
        value
    } else 0.7f
    val level=if(state.phase=="Listening") state.inputLevel else state.outputLevel ?: 0f
    Canvas(modifier.semantics{contentDescription=state.phase}) {
        val radius=size.minDimension/2
        drawCircle(color.copy(alpha=0.12f),radius*pulse)
        drawCircle(color.copy(alpha=0.45f),radius*0.85f,style=Stroke(2f))
        drawCircle(color,radius*(0.23f+0.32f*level.coerceIn(0f,1f)))
    }
}
