package com.meshlit.feature.advanced.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.meshlit.core.advanced.engines.EngineCategory
import com.meshlit.core.advanced.engines.EngineRegistry
import com.meshlit.core.advanced.engines.WhisperEngine
import com.meshlit.core.advanced.engines.WhisperRequest
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.launch
import java.io.File

/**
 * Transcription destination. Tabs: Batch / Live / Hybrid. Recording
 * the (stubbed) microphone just runs the WhisperEngine on a
 * synthetic audio path and shows the placeholder transcript.
 */
@Composable
fun TranscriptionScreen(
    accent: Color,
    accentDim: Color,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(0) }
    var transcript by remember { mutableStateOf<String?>(null) }
    val registry = remember { EngineRegistry() }
    LaunchedEffect(Unit) {
        val engine = WhisperEngine()
        engine.load(File("/tmp/whisper.bin"))
        registry.register(engine)
    }
    val modes = listOf("Batch", "Live", "Hybrid")

    SectionScreen(
        title = "Transcription",
        subtitle = "Whisper batch / live / hybrid.",
        accent = accent,
        accentDim = accentDim,
        onBack = onBack,
        content = {
            SectionCard(title = "Mode", accent = accent) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = i == mode,
                            onClick = { mode = i },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label) }
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            val engine = registry.firstFor(EngineCategory.STT) as? WhisperEngine ?: return@launch
                            val res = engine.run(WhisperRequest(audioPath = "/tmp/mic.wav"))
                            transcript = (res as? MeshlitResult.Success)?.value?.text
                        }
                    },
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                    Text("Record")
                }
                if (transcript != null) {
                    Text("Transcript: $transcript", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}
