package com.meshlit.feature.advanced.screens

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.meshlit.core.advanced.engines.EngineCategory
import com.meshlit.core.advanced.engines.EngineRegistry
import com.meshlit.core.advanced.engines.SileroVadEngine
import com.meshlit.core.advanced.engines.VadFrame
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * Voice-activity destination. Plays a (stubbed) audio chunk through
 * the SileroVadEngine; the indicator flips to "speaking" while the
 * engine is running and back to "silence" when it finishes.
 */
@Composable
fun VoiceActivityScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf("Silence") }
    val registry = remember { EngineRegistry() }
    LaunchedEffect(Unit) {
        val engine = SileroVadEngine()
        engine.load(File("/tmp/silero.bin"))
        registry.register(engine)
    }
    SectionScreen(
        title = "Voice activity",
        subtitle = "Silero VAD — when the user is speaking.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Detector", accent = accent) {
                Text("Current: $state")
                Button(onClick = {
                    scope.launch {
                        state = "Speaking"
                        val engine = registry.firstFor(EngineCategory.VAD) as? SileroVadEngine ?: return@launch
                        engine.run(VadFrame(audioPath = "/tmp/mic.wav"))
                        state = "Silence"
                    }
                }) {
                    Text("Run detector")
                }
            }
        },
    )
}
